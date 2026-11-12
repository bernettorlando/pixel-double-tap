package com.bernettorlando.pixeldoubletap.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.bernettorlando.pixeldoubletap.gesture.DoubleTapGestureTracker
import com.bernettorlando.pixeldoubletap.prefs.TapPreferences
import com.bernettorlando.pixeldoubletap.prefs.TapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScreenLockAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var serviceScope: CoroutineScope? = null

    private lateinit var windowManager: WindowManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var powerManager: PowerManager
    private lateinit var tapPreferences: TapPreferences

    private val gestureTracker = DoubleTapGestureTracker()
    private var currentSettings = TapSettings()

    private var homePackages: Set<String> = KNOWN_PIXEL_LAUNCHERS
    private var lastForegroundAppPackage: String? = PIXEL_LAUNCHER_PACKAGE
    private var bouncerActiveOnKeyguard: Boolean = false

    private var emptySpaceWatcherView: View? = null
    private var statusBarOverlayView: View? = null
    private var pendingConfirmRunnable: Runnable? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    bouncerActiveOnKeyguard = false
                    cancelPendingConfirmation()
                    gestureTracker.reset()
                    detachAllOverlays()
                }
                Intent.ACTION_SCREEN_ON -> {
                    bouncerActiveOnKeyguard = false
                    gestureTracker.reset()
                    evaluateAndSyncOverlays()
                }
                Intent.ACTION_USER_PRESENT -> {
                    bouncerActiveOnKeyguard = false
                    refreshHomeLauncherPackages()
                    gestureTracker.reset()
                    evaluateAndSyncOverlays()
                }
            }
        }
    }

    private var receiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        keyguardManager = getSystemService(KeyguardManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        tapPreferences = TapPreferences.getInstance(this)

        refreshHomeLauncherPackages()
        lastForegroundAppPackage = homePackages.firstOrNull() ?: PIXEL_LAUNCHER_PACKAGE

        registerScreenStateReceiver()

        activeInstance = this
        _isConnected.value = true
        Log.i(TAG, "service_connected homePackages=$homePackages")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        serviceScope = scope
        scope.launch {
            tapPreferences.settings.collectLatest { updated ->
                currentSettings = updated
                gestureTracker.maxTapIntervalMs = updated.doubleTapTimeoutMs.toLong()
                gestureTracker.confirmDelayMs = updated.clickVerifyDelayMs.toLong()
                evaluateAndSyncOverlays()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val now = SystemClock.uptimeMillis()

        when (ev.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                Log.d(
                    TAG,
                    "interactive_click type=${ev.eventType} pkg=${ev.packageName} cls=${ev.className}"
                )
                cancelPendingConfirmation()
                gestureTracker.onInteractiveEvent(
                    uptimeMs = now,
                    cooldownMs = DoubleTapGestureTracker.DEFAULT_UI_EVENT_COOLDOWN_MS
                )
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                Log.d(TAG, "interactive_scroll pkg=${ev.packageName} locked=${keyguardManager.isKeyguardLocked}")
                cancelPendingConfirmation()
                val cooldown = if (keyguardManager.isKeyguardLocked) {
                    DoubleTapGestureTracker.LOCK_SCREEN_SCROLL_COOLDOWN_MS
                } else {
                    DoubleTapGestureTracker.DEFAULT_UI_EVENT_COOLDOWN_MS
                }
                gestureTracker.onInteractiveEvent(uptimeMs = now, cooldownMs = cooldown)
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = ev.packageName?.toString()
                val cls = ev.className?.toString().orEmpty()

                // Ignore window events originating from our own non-Activity overlay views
                val isOwnOverlayEvent = (pkg == packageName && !cls.contains("MainActivity"))
                if (isOwnOverlayEvent) {
                    return
                }

                if (keyguardManager.isKeyguardLocked) {
                    if (pkg == SYSTEM_UI_PACKAGE && isBouncerClassOrTitle(cls)) {
                        bouncerActiveOnKeyguard = true
                    }
                } else {
                    bouncerActiveOnKeyguard = false
                    if (!pkg.isNullOrBlank() &&
                        pkg != SYSTEM_UI_PACKAGE &&
                        pkg != ANDROID_SYSTEM_PACKAGE &&
                        !isInputMethodPackage(pkg)
                    ) {
                        if (pkg !in homePackages) {
                            refreshHomeLauncherPackages()
                        }
                        lastForegroundAppPackage = pkg
                    }
                }

                Log.d(
                    TAG,
                    "window_state pkg=$pkg cls=$cls locked=${keyguardManager.isKeyguardLocked} bouncer=$bouncerActiveOnKeyguard"
                )
                cancelPendingConfirmation()
                gestureTracker.onInteractiveEvent(uptimeMs = now, cooldownMs = 300L)
                evaluateAndSyncOverlays()
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                if (!keyguardManager.isKeyguardLocked) {
                    bouncerActiveOnKeyguard = false
                }
                evaluateAndSyncOverlays()
            }
        }
    }

    override fun onInterrupt() {
        cancelPendingConfirmation()
        gestureTracker.reset()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanupService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanupService()
        super.onDestroy()
    }

    private fun cleanupService() {
        _isConnected.value = false
        if (activeInstance === this) {
            activeInstance = null
        }
        cancelPendingConfirmation()
        detachAllOverlays()
        unregisterScreenStateReceiver()
        serviceScope?.cancel()
        serviceScope = null
    }

    /**
     * Public helper used by the Quick Settings Tile, Transparent Home Widget, and In-App Test button.
     */
    fun triggerImmediateLock(): Boolean {
        return performScreenLock()
    }

    private fun performScreenLock(): Boolean {
        if (!powerManager.isInteractive) return false

        if (currentSettings.hapticFeedback) {
            triggerSubtleHapticTick()
        }

        val locked = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        if (locked) {
            val count = tapPreferences.incrementLockCount()
            gestureTracker.reset()
            Log.i(TAG, "perform_lock_screen success=true count=$count")
        } else {
            Log.i(TAG, "perform_lock_screen success=false")
        }
        return locked
    }

    private fun triggerSubtleHapticTick() {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (_: Exception) {
            // Haptics are strictly best-effort
        }
    }

    private fun evaluateAndSyncOverlays() {
        if (!this::powerManager.isInitialized) return

        val settings = currentSettings
        if (!settings.masterEnabled || !powerManager.isInteractive) {
            detachAllOverlays()
            return
        }

        if (shouldWatchEmptySpace(settings)) {
            attachEmptySpaceWatcherIfNeeded()
        } else {
            detachEmptySpaceWatcher()
        }

        if (settings.statusBarEnabled && !keyguardManager.isKeyguardLocked) {
            attachStatusBarOverlayIfNeeded()
        } else {
            detachStatusBarOverlay()
        }
    }

    private fun shouldWatchEmptySpace(settings: TapSettings = currentSettings): Boolean {
        if (!settings.masterEnabled || !powerManager.isInteractive) return false

        val winSnapshot = inspectInteractiveWindows()
        if (winSnapshot.imeVisible) return false

        val isLocked = keyguardManager.isKeyguardLocked
        if (isLocked) {
            if (!settings.lockScreenEnabled) return false
            if (bouncerActiveOnKeyguard || winSnapshot.bouncerVisible) return false
            return true
        }

        // Unlocked: check if user is on Home Screen with no SystemUI shade/dialog covering it
        if (!settings.homeScreenEnabled) return false
        if (winSnapshot.systemShadeOrDialogActive) return false

        winSnapshot.activeAppPackage?.let { liveAppPkg ->
            if (liveAppPkg !in homePackages) {
                refreshHomeLauncherPackages()
            }
            lastForegroundAppPackage = liveAppPkg
        }

        val fgPkg = winSnapshot.activeAppPackage ?: lastForegroundAppPackage
        return fgPkg != null && fgPkg in homePackages
    }

    private data class WindowSnapshot(
        val imeVisible: Boolean = false,
        val systemShadeOrDialogActive: Boolean = false,
        val bouncerVisible: Boolean = false,
        val activeAppPackage: String? = null
    )

    private fun inspectInteractiveWindows(): WindowSnapshot {
        return try {
            val winList: List<AccessibilityWindowInfo> = windows ?: emptyList()
            var ime = false
            var sysActive = false
            var bouncer = false
            var topAppPkg: String? = null

            for (w in winList) {
                when (w.type) {
                    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> {
                        ime = true
                    }
                    AccessibilityWindowInfo.TYPE_SYSTEM -> {
                        if (w.isActive) {
                            sysActive = true
                        }
                        val title = w.title?.toString().orEmpty()
                        if (title.isNotEmpty() && isBouncerClassOrTitle(title)) {
                            bouncer = true
                        }
                        if (keyguardManager.isKeyguardLocked && w.isActive) {
                            val root = w.root
                            if (root != null) {
                                if (hasBouncerInputNodes(root)) {
                                    bouncer = true
                                }
                            }
                        }
                    }
                    AccessibilityWindowInfo.TYPE_APPLICATION -> {
                        if (topAppPkg == null || w.isActive) {
                            val pkg = w.root?.packageName?.toString()
                            if (!pkg.isNullOrBlank() &&
                                pkg != SYSTEM_UI_PACKAGE &&
                                pkg != ANDROID_SYSTEM_PACKAGE
                            ) {
                                topAppPkg = pkg
                            }
                        }
                    }
                }
            }
            WindowSnapshot(
                imeVisible = ime,
                systemShadeOrDialogActive = sysActive,
                bouncerVisible = bouncer,
                activeAppPackage = topAppPkg
            )
        } catch (_: Exception) {
            WindowSnapshot()
        }
    }

    private fun hasBouncerInputNodes(root: android.view.accessibility.AccessibilityNodeInfo): Boolean {
        for (id in BOUNCER_VIEW_IDS) {
            val matches = root.findAccessibilityNodeInfosByViewId(id)
            if (matches != null && matches.any { it.isVisibleToUser }) {
                return true
            }
        }
        return false
    }

    private fun isBouncerClassOrTitle(value: String): Boolean {
        val lower = value.lowercase()
        return lower.contains("bouncer") ||
            lower.contains("pinentry") ||
            lower.contains("password") ||
            lower.contains("lockpattern") ||
            lower.contains("biometric")
    }

    private fun isInputMethodPackage(pkg: String): Boolean {
        return pkg.contains("inputmethod") || pkg == GBOARD_PACKAGE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachEmptySpaceWatcherIfNeeded() {
        if (emptySpaceWatcherView != null) return

        val view = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener { _, event ->
                val action = event.actionMasked
                if (action == MotionEvent.ACTION_OUTSIDE || action == MotionEvent.ACTION_DOWN) {
                    handleOutsideTouch(SystemClock.uptimeMillis())
                }
                false
            }
        }

        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(view, params)
            emptySpaceWatcherView = view
            Log.i(
                TAG,
                "watcher_attached locked=${keyguardManager.isKeyguardLocked} fg=$lastForegroundAppPackage"
            )
        } catch (e: Exception) {
            Log.w(TAG, "watcher_attach_failed err=${e.javaClass.simpleName}")
            emptySpaceWatcherView = null
        }
    }

    private fun handleOutsideTouch(nowUptimeMs: Long) {
        if (!shouldWatchEmptySpace()) {
            Log.d(TAG, "outside_touch_ignored_not_watching")
            gestureTracker.reset()
            return
        }

        val decision = gestureTracker.onOutsideTouchDown(nowUptimeMs)
        Log.i(TAG, "outside_touch decision=${decision.javaClass.simpleName}")
        when (decision) {
            is DoubleTapGestureTracker.Decision.ScheduleLockConfirmation -> {
                cancelPendingConfirmation()
                val runnable = Runnable {
                    pendingConfirmRunnable = null
                    val confirmTime = SystemClock.uptimeMillis()
                    val stillWatching = shouldWatchEmptySpace()
                    val confirmed = stillWatching &&
                        gestureTracker.confirmPendingLock(decision.token, confirmTime)
                    Log.i(TAG, "confirm_lock stillWatching=$stillWatching confirmed=$confirmed")
                    if (confirmed) {
                        performScreenLock()
                    }
                }
                pendingConfirmRunnable = runnable
                mainHandler.postDelayed(runnable, decision.delayMs)
            }
            DoubleTapGestureTracker.Decision.FirstTapRecorded,
            DoubleTapGestureTracker.Decision.Ignored -> Unit
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachStatusBarOverlayIfNeeded() {
        if (statusBarOverlayView != null) return

        val heightPx = getStatusBarHeightPx()
        val detector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    performScreenLock()
                    return true
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    val start = e1 ?: return false
                    val dy = e2.rawY - start.rawY
                    if (dy > dpToPx(24)) {
                        val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
                        if (start.rawX > screenWidth * 0.68f) {
                            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                        } else {
                            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                        }
                        return true
                    }
                    return false
                }
            }
        )

        val stripView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener { _, event ->
                detector.onTouchEvent(event)
                true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(stripView, params)
            statusBarOverlayView = stripView
        } catch (_: Exception) {
            statusBarOverlayView = null
        }
    }

    private fun getStatusBarHeightPx(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val insets = windowManager.currentWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(
                        WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
                    )
                if (insets.top > 0) return insets.top
            } catch (_: Exception) {
                // Fallback below
            }
        }
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else dpToPx(36)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun cancelPendingConfirmation() {
        pendingConfirmRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingConfirmRunnable = null
    }

    private fun detachEmptySpaceWatcher() {
        emptySpaceWatcherView?.let { view ->
            try {
                windowManager.removeView(view)
                Log.i(TAG, "watcher_detached")
            } catch (_: Exception) {
                // Already detached
            }
        }
        emptySpaceWatcherView = null
    }

    private fun detachStatusBarOverlay() {
        statusBarOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // Already detached
            }
        }
        statusBarOverlayView = null
    }

    private fun detachAllOverlays() {
        detachEmptySpaceWatcher()
        detachStatusBarOverlay()
    }

    private fun refreshHomeLauncherPackages() {
        val discovered = mutableSetOf<String>()
        discovered.addAll(KNOWN_PIXEL_LAUNCHERS)

        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            resolved?.activityInfo?.packageName?.let { pkg ->
                if (pkg != ANDROID_SYSTEM_PACKAGE && pkg != SETTINGS_PACKAGE) {
                    discovered.add(pkg)
                }
            }
            val candidates = packageManager.queryIntentActivities(
                homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (info in candidates) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg != ANDROID_SYSTEM_PACKAGE && pkg != SETTINGS_PACKAGE) {
                    discovered.add(pkg)
                }
            }
        } catch (_: Exception) {
            // Keep known defaults
        }
        homePackages = discovered
    }

    private fun registerScreenStateReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterScreenStateReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
            // Ignored
        }
        receiverRegistered = false
    }

    companion object {
        private const val TAG = "PixelDoubleTap"
        private const val PIXEL_LAUNCHER_PACKAGE = "com.google.android.apps.nexuslauncher"
        private const val AOSP_LAUNCHER_PACKAGE = "com.android.launcher3"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val ANDROID_SYSTEM_PACKAGE = "android"
        private const val SETTINGS_PACKAGE = "com.android.settings"
        private const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"

        private val KNOWN_PIXEL_LAUNCHERS = setOf(
            PIXEL_LAUNCHER_PACKAGE,
            AOSP_LAUNCHER_PACKAGE
        )

        private val BOUNCER_VIEW_IDS = listOf(
            "com.android.systemui:id/pinEntry",
            "com.android.systemui:id/passwordEntry",
            "com.android.systemui:id/lockPatternView",
            "com.android.systemui:id/key_enter"
        )

        @Volatile
        private var activeInstance: ScreenLockAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun requestLockNow(): Boolean {
            return activeInstance?.triggerImmediateLock() ?: false
        }
    }
}
