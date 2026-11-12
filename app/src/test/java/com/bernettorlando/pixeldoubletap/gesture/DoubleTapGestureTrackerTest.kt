package com.bernettorlando.pixeldoubletap.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapGestureTrackerTest {

    @Test
    fun `valid double tap schedules confirmation and confirms cleanly`() {
        val tracker = DoubleTapGestureTracker(
            minTapIntervalMs = 65L,
            maxTapIntervalMs = 350L,
            confirmDelayMs = 140L
        )

        val d1 = tracker.onOutsideTouchDown(1_000L)
        assertEquals(DoubleTapGestureTracker.Decision.FirstTapRecorded, d1)

        val d2 = tracker.onOutsideTouchDown(1_180L)
        assertTrue(d2 is DoubleTapGestureTracker.Decision.ScheduleLockConfirmation)
        val confirm = d2 as DoubleTapGestureTracker.Decision.ScheduleLockConfirmation
        assertEquals(140L, confirm.delayMs)

        assertTrue(tracker.confirmPendingLock(confirm.token, 1_320L))
    }

    @Test
    fun `view click right after second tap cancels pending lock`() {
        val tracker = DoubleTapGestureTracker()

        tracker.onOutsideTouchDown(1_000L)
        val d2 = tracker.onOutsideTouchDown(1_190L)
            as DoubleTapGestureTracker.Decision.ScheduleLockConfirmation

        // App icon click fires on ACTION_UP (~50ms after 2nd ACTION_DOWN)
        tracker.onInteractiveEvent(uptimeMs = 1_240L)

        assertFalse(tracker.confirmPendingLock(d2.token, 1_330L))
    }

    @Test
    fun `scroll gesture invalidates first tap and enforces cooldown`() {
        val tracker = DoubleTapGestureTracker()

        tracker.onOutsideTouchDown(2_000L)
        tracker.onInteractiveEvent(
            uptimeMs = 2_050L,
            cooldownMs = DoubleTapGestureTracker.LOCK_SCREEN_SCROLL_COOLDOWN_MS
        )

        // Tapping PIN digits during lock-screen scroll cooldown is ignored
        val duringCooldown = tracker.onOutsideTouchDown(3_000L)
        assertEquals(DoubleTapGestureTracker.Decision.Ignored, duringCooldown)

        // After cooldown expires, normal detection resumes
        val afterCooldown = tracker.onOutsideTouchDown(4_500L)
        assertEquals(DoubleTapGestureTracker.Decision.FirstTapRecorded, afterCooldown)
    }

    @Test
    fun `slow second tap starts a new first tap instead of locking`() {
        val tracker = DoubleTapGestureTracker(maxTapIntervalMs = 350L)

        assertEquals(
            DoubleTapGestureTracker.Decision.FirstTapRecorded,
            tracker.onOutsideTouchDown(1_000L)
        )
        // 600ms later -> too slow, becomes new 1st tap
        assertEquals(
            DoubleTapGestureTracker.Decision.FirstTapRecorded,
            tracker.onOutsideTouchDown(1_600L)
        )
        // Quick follow-up 170ms later -> triggers double tap
        val d3 = tracker.onOutsideTouchDown(1_770L)
        assertTrue(d3 is DoubleTapGestureTracker.Decision.ScheduleLockConfirmation)
    }

    @Test
    fun `palm bounce under minimum interval is rejected`() {
        val tracker = DoubleTapGestureTracker(minTapIntervalMs = 65L)

        tracker.onOutsideTouchDown(5_000L)
        val bounce = tracker.onOutsideTouchDown(5_020L)
        assertEquals(DoubleTapGestureTracker.Decision.Ignored, bounce)
    }

    @Test
    fun `triple tap cancels pending confirmation`() {
        val tracker = DoubleTapGestureTracker()

        tracker.onOutsideTouchDown(1_000L)
        val d2 = tracker.onOutsideTouchDown(1_150L)
            as DoubleTapGestureTracker.Decision.ScheduleLockConfirmation

        // 3rd tap arrives before confirmation timer fires
        val d3 = tracker.onOutsideTouchDown(1_230L)
        assertEquals(DoubleTapGestureTracker.Decision.Ignored, d3)
        assertFalse(tracker.confirmPendingLock(d2.token, 1_300L))
    }
}
