package com.bernettorlando.pixeldoubletap.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bernettorlando.pixeldoubletap.prefs.TapPreferences
import com.bernettorlando.pixeldoubletap.service.ScreenLockAccessibilityService
import com.bernettorlando.pixeldoubletap.system.PixelSystemHelper
import com.bernettorlando.pixeldoubletap.ui.theme.PixelDoubleTapTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = TapPreferences.getInstance(this)

        setContent {
            PixelDoubleTapTheme {
                val settings by prefs.settings.collectAsState()
                val liveConnected by ScreenLockAccessibilityService.isConnected.collectAsState()

                var a11yEnabledInSettings by remember {
                    mutableStateOf(PixelSystemHelper.isAccessibilityServiceEnabled(this))
                }
                var wakeStatus by remember {
                    mutableStateOf(PixelSystemHelper.readPixelWakeStatus(this))
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            a11yEnabledInSettings =
                                PixelSystemHelper.isAccessibilityServiceEnabled(this@MainActivity)
                            wakeStatus =
                                PixelSystemHelper.readPixelWakeStatus(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val isServiceReady = liveConnected || a11yEnabledInSettings

                MainScreen(
                    settings = settings,
                    isAccessibilityEnabled = isServiceReady,
                    wakeStatus = wakeStatus,
                    onOpenAppInfo = {
                        PixelSystemHelper.openAppDetailsSettings(this)
                    },
                    onOpenAccessibility = {
                        PixelSystemHelper.openAccessibilitySettings(this)
                    },
                    onOpenWakeSettings = {
                        PixelSystemHelper.openPixelLockScreenWakeSettings(this)
                    },
                    onTestLockNow = {
                        val locked = ScreenLockAccessibilityService.requestLockNow()
                        if (!locked) {
                            Toast.makeText(
                                this,
                                "Please toggle Pixel Double Tap ON in Accessibility Settings first",
                                Toast.LENGTH_SHORT
                            ).show()
                            PixelSystemHelper.openAccessibilitySettings(this)
                        }
                    },
                    onMasterEnabledChange = prefs::setMasterEnabled,
                    onHomeScreenChange = prefs::setHomeScreenEnabled,
                    onLockScreenChange = prefs::setLockScreenEnabled,
                    onStatusBarChange = prefs::setStatusBarEnabled,
                    onTimeoutChange = prefs::setDoubleTapTimeoutMs,
                    onHapticChange = prefs::setHapticFeedback
                )
            }
        }
    }
}
