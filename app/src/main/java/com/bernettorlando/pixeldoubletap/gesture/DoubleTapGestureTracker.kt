package com.bernettorlando.pixeldoubletap.gesture

/**
 * Pure, deterministic state machine for detecting empty-space double taps
 * from non-intrusive outside-touch timestamps while filtering out icon clicks,
 * widget taps, PIN pad presses, long-presses, and swipe/scroll gestures.
 */
class DoubleTapGestureTracker(
    var minTapIntervalMs: Long = DEFAULT_MIN_TAP_INTERVAL_MS,
    var maxTapIntervalMs: Long = DEFAULT_MAX_TAP_INTERVAL_MS,
    var confirmDelayMs: Long = DEFAULT_CONFIRM_DELAY_MS
) {

    sealed interface Decision {
        data object Ignored : Decision
        data object FirstTapRecorded : Decision
        data class ScheduleLockConfirmation(val delayMs: Long, val token: Long) : Decision
    }

    private var firstTapUptimeMs: Long = NO_TIMESTAMP
    private var ignoreUntilUptimeMs: Long = 0L
    private var pendingToken: Long = 0L
    private var hasPendingConfirmation: Boolean = false

    /**
     * Records an outside touch-down timestamp on the watched surface (Home or Lock Screen).
     */
    @Synchronized
    fun onOutsideTouchDown(uptimeMs: Long): Decision {
        if (uptimeMs < ignoreUntilUptimeMs) {
            firstTapUptimeMs = NO_TIMESTAMP
            hasPendingConfirmation = false
            return Decision.Ignored
        }

        // If a 3rd tap arrives while confirmation is already pending, cancel to avoid accidental spam
        if (hasPendingConfirmation) {
            reset()
            ignoreUntilUptimeMs = uptimeMs + TRIPLE_TAP_COOLDOWN_MS
            return Decision.Ignored
        }

        val previousTap = firstTapUptimeMs
        if (previousTap == NO_TIMESTAMP) {
            firstTapUptimeMs = uptimeMs
            return Decision.FirstTapRecorded
        }

        val deltaMs = uptimeMs - previousTap
        return when {
            deltaMs < minTapIntervalMs -> {
                // Palm bounce or multi-finger simultaneous touch; discard both
                reset()
                ignoreUntilUptimeMs = uptimeMs + PALM_BOUNCE_COOLDOWN_MS
                Decision.Ignored
            }
            deltaMs <= maxTapIntervalMs -> {
                // Candidate double-tap! Wait confirmDelayMs to ensure no view click or scroll fires.
                firstTapUptimeMs = NO_TIMESTAMP
                hasPendingConfirmation = true
                val nextToken = ++pendingToken
                Decision.ScheduleLockConfirmation(confirmDelayMs, nextToken)
            }
            else -> {
                // Previous tap expired; treat this touch as a fresh first tap
                firstTapUptimeMs = uptimeMs
                Decision.FirstTapRecorded
            }
        }
    }

    /**
     * Called whenever an interactive UI element was clicked, long-clicked, scrolled,
     * or when the active window changed. Immediately cancels any candidate tap or pending lock.
     */
    @Synchronized
    fun onInteractiveEvent(uptimeMs: Long, cooldownMs: Long = DEFAULT_UI_EVENT_COOLDOWN_MS) {
        firstTapUptimeMs = NO_TIMESTAMP
        hasPendingConfirmation = false
        pendingToken++
        val targetCooldown = uptimeMs + cooldownMs
        if (targetCooldown > ignoreUntilUptimeMs) {
            ignoreUntilUptimeMs = targetCooldown
        }
    }

    /**
     * Invoked after [confirmDelayMs] elapses. Returns true iff the candidate double-tap
     * with [token] was NOT cancelled by any view click, scroll, or window transition.
     */
    @Synchronized
    fun confirmPendingLock(token: Long, uptimeMs: Long): Boolean {
        if (!hasPendingConfirmation || token != pendingToken) {
            return false
        }
        if (uptimeMs < ignoreUntilUptimeMs) {
            reset()
            return false
        }
        hasPendingConfirmation = false
        firstTapUptimeMs = NO_TIMESTAMP
        ignoreUntilUptimeMs = uptimeMs + POST_LOCK_COOLDOWN_MS
        return true
    }

    @Synchronized
    fun reset() {
        firstTapUptimeMs = NO_TIMESTAMP
        hasPendingConfirmation = false
        pendingToken++
    }

    companion object {
        private const val NO_TIMESTAMP = -1L
        const val DEFAULT_MIN_TAP_INTERVAL_MS = 65L
        const val DEFAULT_MAX_TAP_INTERVAL_MS = 350L
        const val DEFAULT_CONFIRM_DELAY_MS = 140L
        const val DEFAULT_UI_EVENT_COOLDOWN_MS = 400L
        const val LOCK_SCREEN_SCROLL_COOLDOWN_MS = 2200L
        private const val TRIPLE_TAP_COOLDOWN_MS = 300L
        private const val PALM_BOUNCE_COOLDOWN_MS = 200L
        private const val POST_LOCK_COOLDOWN_MS = 600L
    }
}
