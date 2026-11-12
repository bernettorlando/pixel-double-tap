package com.bernettorlando.pixeldoubletap.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TapSettings(
    val masterEnabled: Boolean = true,
    val homeScreenEnabled: Boolean = true,
    val lockScreenEnabled: Boolean = true,
    val statusBarEnabled: Boolean = false,
    val doubleTapTimeoutMs: Int = DEFAULT_DOUBLE_TAP_TIMEOUT_MS,
    val clickVerifyDelayMs: Int = DEFAULT_CLICK_VERIFY_DELAY_MS,
    val hapticFeedback: Boolean = true,
    val lockCount: Int = 0
) {
    companion object {
        const val DEFAULT_DOUBLE_TAP_TIMEOUT_MS = 350
        const val MIN_DOUBLE_TAP_TIMEOUT_MS = 220
        const val MAX_DOUBLE_TAP_TIMEOUT_MS = 500

        const val DEFAULT_CLICK_VERIFY_DELAY_MS = 140
        const val MIN_CLICK_VERIFY_DELAY_MS = 80
        const val MAX_CLICK_VERIFY_DELAY_MS = 220
    }
}

class TapPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<TapSettings> = _settings.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = readSettings()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun readSettings(): TapSettings {
        return TapSettings(
            masterEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true),
            homeScreenEnabled = prefs.getBoolean(KEY_HOME_SCREEN_ENABLED, true),
            lockScreenEnabled = prefs.getBoolean(KEY_LOCK_SCREEN_ENABLED, true),
            statusBarEnabled = prefs.getBoolean(KEY_STATUS_BAR_ENABLED, false),
            doubleTapTimeoutMs = prefs.getInt(
                KEY_DOUBLE_TAP_TIMEOUT_MS,
                TapSettings.DEFAULT_DOUBLE_TAP_TIMEOUT_MS
            ).coerceIn(
                TapSettings.MIN_DOUBLE_TAP_TIMEOUT_MS,
                TapSettings.MAX_DOUBLE_TAP_TIMEOUT_MS
            ),
            clickVerifyDelayMs = prefs.getInt(
                KEY_CLICK_VERIFY_DELAY_MS,
                TapSettings.DEFAULT_CLICK_VERIFY_DELAY_MS
            ).coerceIn(
                TapSettings.MIN_CLICK_VERIFY_DELAY_MS,
                TapSettings.MAX_CLICK_VERIFY_DELAY_MS
            ),
            hapticFeedback = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true),
            lockCount = prefs.getInt(KEY_LOCK_COUNT, 0)
        )
    }

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun setHomeScreenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_SCREEN_ENABLED, enabled).apply()
    }

    fun setLockScreenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_SCREEN_ENABLED, enabled).apply()
    }

    fun setStatusBarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STATUS_BAR_ENABLED, enabled).apply()
    }

    fun setDoubleTapTimeoutMs(timeoutMs: Int) {
        val clamped = timeoutMs.coerceIn(
            TapSettings.MIN_DOUBLE_TAP_TIMEOUT_MS,
            TapSettings.MAX_DOUBLE_TAP_TIMEOUT_MS
        )
        prefs.edit().putInt(KEY_DOUBLE_TAP_TIMEOUT_MS, clamped).apply()
    }

    fun setClickVerifyDelayMs(delayMs: Int) {
        val clamped = delayMs.coerceIn(
            TapSettings.MIN_CLICK_VERIFY_DELAY_MS,
            TapSettings.MAX_CLICK_VERIFY_DELAY_MS
        )
        prefs.edit().putInt(KEY_CLICK_VERIFY_DELAY_MS, clamped).apply()
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    fun incrementLockCount() {
        val next = prefs.getInt(KEY_LOCK_COUNT, 0) + 1
        prefs.edit().putInt(KEY_LOCK_COUNT, next).apply()
    }

    companion object {
        private const val PREFS_NAME = "pixel_double_tap_prefs"

        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_HOME_SCREEN_ENABLED = "home_screen_enabled"
        private const val KEY_LOCK_SCREEN_ENABLED = "lock_screen_enabled"
        private const val KEY_STATUS_BAR_ENABLED = "status_bar_enabled"
        private const val KEY_DOUBLE_TAP_TIMEOUT_MS = "double_tap_timeout_ms"
        private const val KEY_CLICK_VERIFY_DELAY_MS = "click_verify_delay_ms"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_LOCK_COUNT = "lock_count"

        @Volatile
        private var instance: TapPreferences? = null

        fun getInstance(context: Context): TapPreferences {
            return instance ?: synchronized(this) {
                instance ?: TapPreferences(context).also { instance = it }
            }
        }
    }
}
