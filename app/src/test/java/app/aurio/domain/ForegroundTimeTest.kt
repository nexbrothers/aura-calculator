package app.aurio.domain

import app.aurio.domain.ForegroundEvent.Kind.DEVICE_STARTUP
import app.aurio.domain.ForegroundEvent.Kind.PAUSED
import app.aurio.domain.ForegroundEvent.Kind.RESUMED
import app.aurio.domain.ForegroundEvent.Kind.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTimeTest {
    private val min = 60_000L
    private val start = 1_000 * min
    private val end = start + 24 * 60 * min
    private val readFrom = start - 6 * 60 * min

    private val main = 1
    private val feed = 2
    private val story = 3

    private fun at(m: Double, pkg: String, kind: ForegroundEvent.Kind, screen: Int = main) =
        ForegroundEvent(start + (m * min).toLong(), pkg, screen, kind)

    private fun at(m: Int, pkg: String, kind: ForegroundEvent.Kind, screen: Int = main) = at(m.toDouble(), pkg, kind, screen)

    private fun minutes(events: List<ForegroundEvent>, to: Long = end) =
        ForegroundTime.perPackage(events, readFrom, start, to).mapValues { it.value / min }

    @Test fun oneStretch() {
        assertEquals(mapOf("ig" to 10L), minutes(listOf(at(5, "ig", RESUMED), at(15, "ig", PAUSED))))
    }

    @Test fun emptyReadCountsNothing() {
        assertTrue(minutes(emptyList()).isEmpty())
    }

    @Test fun handOffBetweenScreensInAndroidsOrder() {
        // Android pauses the old screen, then resumes the new one.
        val events = listOf(
            at(0, "ig", RESUMED, feed), at(4, "ig", PAUSED, feed), at(4, "ig", RESUMED, story),
            at(9, "ig", PAUSED, story), at(9, "ig", RESUMED, feed), at(20, "ig", PAUSED, feed),
        )
        assertEquals(mapOf("ig" to 20L), minutes(events))
    }

    @Test fun anotherCopyOfTheSameScreenKeepsCounting() {
        // Profile to profile: pause A, resume A again; the old copy's late "stop" is never passed in.
        val events = listOf(
            at(0, "ig", RESUMED), at(10, "ig", PAUSED), at(10, "ig", RESUMED), at(60, "ig", PAUSED),
        )
        assertEquals(mapOf("ig" to 60L), minutes(events))
    }

    @Test fun twoCopiesOfOneScreenSideBySideStayOpenUntilBothPause() {
        val events = listOf(at(0, "yt", RESUMED), at(5, "yt", RESUMED), at(10, "yt", PAUSED), at(30, "yt", PAUSED))
        assertEquals(mapOf("yt" to 30L), minutes(events))
    }

    @Test fun screenOffEndsEveryOpenApp() {
        val events = listOf(at(0, "yt", RESUMED), at(2, "chat", RESUMED), at(25, "yt", SCREEN_OFF), at(40, "yt", PAUSED))
        assertEquals(mapOf("yt" to 25L, "chat" to 23L), minutes(events))
    }

    @Test fun stretchFromYesterdayCountsOnlyFromMidnight() {
        val events = listOf(at(-20, "ig", RESUMED), at(15, "ig", PAUSED))
        assertEquals(mapOf("ig" to 15L), minutes(events))
    }

    @Test fun appOpenSinceBeforeTheReadCountsFromMidnight() {
        // YouTube playing since long before the read window, paused at 01:30.
        assertEquals(mapOf("yt" to 90L), minutes(listOf(at(90, "yt", PAUSED))))
    }

    @Test fun pauseAfterAnAppClosedCountsNothingMore() {
        val events = listOf(at(0, "ig", RESUMED), at(10, "ig", PAUSED), at(11, "ig", PAUSED))
        assertEquals(mapOf("ig" to 10L), minutes(events))
    }

    @Test fun stretchPastMidnightCountsOnlyUntilMidnight() {
        // The old daily totals could add the after-midnight part to the day that ended.
        val events = listOf(at(24 * 60 - 10, "ig", RESUMED), at(24 * 60 + 30, "ig", PAUSED))
        assertEquals(mapOf("ig" to 10L), minutes(events))
    }

    @Test fun eventsExactlyAtTheEdges() {
        val events = listOf(at(0, "a", RESUMED), at(24 * 60, "a", PAUSED))
        assertEquals(mapOf("a" to 24 * 60L), minutes(events))
    }

    @Test fun appStillOpenRunsToTheEndOfTheWindow() {
        val now = start + 50 * min
        assertEquals(mapOf("x" to 10L), minutes(listOf(at(40, "x", RESUMED)), to = now))
    }

    @Test fun crashOrPowerLossIsNotCountedAsUse() {
        // Open at 0, last sign of life at 30, phone back on at 240 with no shutdown event.
        val events = listOf(
            at(0, "ig", RESUMED), at(30, "other", PAUSED), at(240, "android", DEVICE_STARTUP),
            at(250, "ig", RESUMED), at(260, "ig", PAUSED),
        )
        assertEquals(40L, minutes(events)["ig"])
    }

    @Test fun splitScreenCountsBothApps() {
        val events = listOf(at(0, "yt", RESUMED), at(2, "chat", RESUMED), at(12, "chat", PAUSED), at(20, "yt", PAUSED))
        assertEquals(mapOf("yt" to 20L, "chat" to 10L), minutes(events))
    }

    @Test fun separateStretchesAddUp() {
        val events = listOf(
            at(0, "ig", RESUMED), at(10, "ig", PAUSED),
            at(60, "ig", RESUMED), at(75, "ig", PAUSED),
        )
        assertEquals(mapOf("ig" to 25L), minutes(events))
    }

    @Test fun overLimitUseIsAPenaltyNotABonus() {
        // Ties the count to the score: 60 min of Instagram against a 30 min limit.
        val events = listOf(at(0, "ig", RESUMED), at(10, "ig", PAUSED), at(10, "ig", RESUMED), at(60, "ig", PAUSED))
        val used = minutes(events)["ig"]!!.toInt()
        val status = AuraScoring.limitStatus(AppUsage("ig", "Instagram", AppCategory.LIMITED, used, 30))
        assertEquals(LimitStatus.Over(30, 30), status)
    }
}
