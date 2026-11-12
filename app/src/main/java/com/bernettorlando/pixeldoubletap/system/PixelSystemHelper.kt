package com.bernettorlando.pixeldoubletap.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.bernettorlando.pixeldoubletap.service.ScreenLockAccessibilityService

data class PixelWakeStatus(
    val tapToCheckPhoneEnabled: Boolean?,
    val liftToCheckPhoneEnabled: Boolean?,
    val alwaysOnDisplayEnabled: Boolean?
)

object PixelSystemHelper {

    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"

    /**
     * Checks Settings.Secure to determine if ScreenLockAccessibilityService is enabled.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (ScreenLockAccessibilityService.isConnected.value) return true

        val expected = ComponentName(context, ScreenLockAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it.packageName == expected.packageName && it.className == expected.className }
    }

    /**
     * Reads Pixel 11 Pro's built-in hardware wake gesture settings (read-only via Settings.Secure).
     * "doze_tap_gesture" backs Pixel's "Tap to check phone" (zero-battery hardware tap-to-wake).
     */
    fun readPixelWakeStatus(context: Context): PixelWakeStatus {
        val cr = context.contentResolver
        val tapGesture = readSecureBool(cr, "doze_tap_gesture")
            ?: readSecureBool(cr, "doze_pulse_on_double_tap")
        val liftGesture = readSecureBool(cr, "wake_gesture_enabled")
            ?: readSecureBool(cr, "doze_pulse_on_pick_up")
        val aod = readSecureBool(cr, "doze_always_on")

        return PixelWakeStatus(
            tapToCheckPhoneEnabled = tapGesture,
            liftToCheckPhoneEnabled = liftGesture,
            alwaysOnDisplayEnabled = aod
        )
    }

    private fun readSecureBool(
        cr: android.content.ContentResolver,
        key: String
    ): Boolean? {
        return try {
            val raw = Settings.Secure.getInt(cr, key, -1)
            when (raw) {
                1 -> true
                0 -> false
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Opens App Info directly so sideloaded APK users on Android 13–17 (Pixel 11 Pro)
     * can tap the top-right 3-dot menu -> "Allow restricted settings".
     */
    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        safeStartActivity(context, intent, Intent(Settings.ACTION_SETTINGS))
    }

    /**
     * Opens Accessibility Settings and highlights Pixel Double Tap directly on Pixel devices.
     */
    fun openAccessibilitySettings(context: Context) {
        val component = ComponentName(context, ScreenLockAccessibilityService::class.java)
            .flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            val bundle = Bundle().apply {
                putString(EXTRA_FRAGMENT_ARG_KEY, component)
            }
            putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
            putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, bundle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        safeStartActivity(context, intent, Intent(Settings.ACTION_SETTINGS))
    }

    /**
     * Jumps directly to Pixel 11 Pro's Lock Screen settings where "Tap to check phone" lives,
     * falling back gracefully to Display settings if unavailable.
     */
    fun openPixelLockScreenWakeSettings(context: Context) {
        val candidates = listOf(
            Intent("android.settings.LOCK_SCREEN_SETTINGS"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (candidate in candidates) {
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(candidate)
                return
            } catch (_: Exception) {
                // Try next fallback
            }
        }
    }

    private fun safeStartActivity(context: Context, primary: Intent, fallback: Intent) {
        try {
            context.startActivity(primary)
        } catch (_: Exception) {
            try {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            } catch (_: Exception) {
                // Ignored
            }
        }
    }
}
