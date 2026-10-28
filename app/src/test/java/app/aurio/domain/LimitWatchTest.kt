package app.aurio.domain

import app.aurio.domain.LimitAlert.FEW_LEFT
import app.aurio.domain.LimitAlert.REACHED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LimitWatchTest {
    private fun app(used: Int, limit: Int = 30, pkg: String = "insta") = WatchedApp(pkg, "Instagram", limit, used)

    // ---- What to post ----

    @Test fun nothingWhileWellUnder() =
        assertEquals(emptyList<Any>(), LimitWatch.alertsDue(listOf(app(used = 10)), emptySet()))

    @Test fun fiveLeftWarns() =
        assertEquals(listOf(app(25) to FEW_LEFT), LimitWatch.alertsDue(listOf(app(25)), emptySet()))

    @Test fun fewLeftIsSentOnce() =
        assertEquals(emptyList<Any>(), LimitWatch.alertsDue(listOf(app(27)), setOf("insta" to FEW_LEFT)))

    @Test fun reachingTheLimitSaysReached() =
        assertEquals(listOf(app(30) to REACHED), LimitWatch.alertsDue(listOf(app(30)), setOf("insta" to FEW_LEFT)))

    @Test fun jumpingPastBothThresholdsSendsOnlyReached() =
        assertEquals(listOf(app(41) to REACHED), LimitWatch.alertsDue(listOf(app(41)), emptySet()))

    @Test fun reachedIsSentOnce() =
        assertEquals(emptyList<Any>(), LimitWatch.alertsDue(listOf(app(50)), setOf("insta" to REACHED)))

    @Test fun aFiveMinuteLimitNeverWarnsEarly() {
        assertEquals(emptyList<Any>(), LimitWatch.alertsDue(listOf(app(used = 0, limit = 5)), emptySet()))
        assertEquals(listOf(app(5, limit = 5) to REACHED), LimitWatch.alertsDue(listOf(app(5, limit = 5)), emptySet()))
    }

    // ---- When to look again ----

    @Test fun nextCheckIsTheDistanceToFiveLeft() =
        assertEquals(15, LimitWatch.minutesUntilNextCheck(listOf(app(used = 10)), emptySet()))

    @Test fun afterTheWarningTheNextCheckIsTheLimit() =
        assertEquals(4, LimitWatch.minutesUntilNextCheck(listOf(app(26)), setOf("insta" to FEW_LEFT)))

    @Test fun theSoonestAppWins() {
        val apps = listOf(app(used = 0, limit = 60, pkg = "a"), app(used = 20, limit = 30, pkg = "b"))
        assertEquals(5, LimitWatch.minutesUntilNextCheck(apps, emptySet()))
    }

    @Test fun neverSoonerThanAMinute() =
        assertEquals(1, LimitWatch.minutesUntilNextCheck(listOf(app(30)), setOf("insta" to FEW_LEFT)))

    @Test fun aFiveMinuteLimitIsWatchedForReachedOnly() =
        assertEquals(3, LimitWatch.minutesUntilNextCheck(listOf(app(used = 2, limit = 5)), emptySet()))

    @Test fun noCheckOnceEverythingWasSaid() =
        assertNull(LimitWatch.minutesUntilNextCheck(listOf(app(40)), setOf("insta" to REACHED)))

    @Test fun noCheckWithoutLimitedApps() =
        assertNull(LimitWatch.minutesUntilNextCheck(emptyList(), emptySet()))

    // ---- Quiet hours ----

    @Test fun quietHoursAcrossMidnight() {
        assertTrue(LimitWatch.inQuietHours(23 * 60, 22 * 60, 7 * 60))
        assertTrue(LimitWatch.inQuietHours(3 * 60, 22 * 60, 7 * 60))
        assertFalse(LimitWatch.inQuietHours(7 * 60, 22 * 60, 7 * 60))
        assertFalse(LimitWatch.inQuietHours(12 * 60, 22 * 60, 7 * 60))
    }

    @Test fun quietHoursWithinADay() {
        assertTrue(LimitWatch.inQuietHours(14 * 60, 13 * 60, 15 * 60))
        assertFalse(LimitWatch.inQuietHours(15 * 60, 13 * 60, 15 * 60))
    }

    @Test fun equalStartAndEndMeansNoQuietHours() =
        assertFalse(LimitWatch.inQuietHours(600, 600, 600))

    // ---- Remembering what was sent ----

    private val today = LocalDate.parse("2026-10-28")

    @Test fun sentRoundTrips() {
        val sent = setOf("com.instagram.android" to FEW_LEFT, "com.zhiliaoapp.musically" to REACHED)
        assertEquals(sent, LimitWatch.decodeSent(LimitWatch.encodeSent(today, sent), today))
    }

    @Test fun yesterdaysAlertsDoNotCountToday() =
        assertEquals(emptySet<Any>(), LimitWatch.decodeSent(LimitWatch.encodeSent(today.minusDays(1), setOf("a" to REACHED)), today))

    @Test fun nothingStoredReadsEmpty() {
        assertEquals(emptySet<Any>(), LimitWatch.decodeSent(null, today))
        assertEquals(emptySet<Any>(), LimitWatch.decodeSent("${today}|", today))
        assertEquals(emptySet<Any>(), LimitWatch.decodeSent("garbage", today))
    }
}
