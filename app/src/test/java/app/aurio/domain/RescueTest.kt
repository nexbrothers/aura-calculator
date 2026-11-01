package app.aurio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RescueTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val elevenPm = 23 * 60

    /** Days before today, newest first: a crossing minute, NO for under the limit, or UNKNOWN. */
    private fun history(vararg days: Int?): List<RescueDay> = days.mapIndexed { i, m ->
        val date = today.minusDays(i + 1L)
        if (m == UNKNOWN) RescueDay(date, known = false, crossedAtMinute = null) else RescueDay(date, true, m)
    }

    private fun app(used: Int, limit: Int = 60, pkg: String = "insta") = WatchedApp(pkg, "Instagram", limit, used)

    private fun candidate(used: Int = 40, usual: Int? = elevenPm, state: RescueState = RescueState(), pkg: String = "insta") =
        RescueCandidate(app(used, pkg = pkg), usual, state)

    private fun due(vararg c: RescueCandidate, now: Int = elevenPm - 20, fewLeft: Set<String> = emptySet(), quiet: Boolean = false) =
        Rescue.due(c.toList(), today, now, fewLeft, quiet).map { it.app.packageName }

    // ---- Learning the usual time ----

    @Test fun medianOfCrossings() =
        assertEquals(23 * 60, Rescue.usualCrossing(history(22 * 60, 23 * 60, 23 * 60 + 30, NO), today))

    @Test fun evenCountAveragesTheMiddleTwo() =
        assertEquals(22 * 60 + 30, Rescue.usualCrossing(history(22 * 60, 23 * 60, 21 * 60, 23 * 60 + 40), today))

    @Test fun needsFourKnownDays() =
        assertNull(Rescue.usualCrossing(history(elevenPm, elevenPm, elevenPm), today))

    @Test fun needsThreeOverDays() =
        assertNull(Rescue.usualCrossing(history(elevenPm, elevenPm, NO, NO, NO), today))

    @Test fun unknownDaysAreSkipped() =
        assertNull(Rescue.usualCrossing(history(elevenPm, elevenPm, elevenPm, UNKNOWN, UNKNOWN), today))

    @Test fun onlyTheLastSevenDaysCount() =
        assertNull(Rescue.usualCrossing(history(NO, NO, NO, NO, NO, NO, NO, elevenPm, elevenPm, elevenPm), today))

    @Test fun todayIsNeverHistory() {
        val withToday = history(elevenPm, elevenPm, NO) + RescueDay(today, true, elevenPm)
        assertNull(Rescue.usualCrossing(withToday, today))
    }

    // ---- When to nudge ----

    @Test fun nudgesInTheHalfHourBefore() = assertEquals(listOf("insta"), due(candidate()))

    @Test fun notBeforeTheHalfHour() = assertEquals(emptyList<String>(), due(candidate(), now = elevenPm - 31))

    @Test fun notOnceTheUsualTimeHasPassed() = assertEquals(emptyList<String>(), due(candidate(), now = elevenPm))

    @Test fun waitsForHalfTheLimit() {
        assertEquals(emptyList<String>(), due(candidate(used = 29)))
        assertEquals(listOf("insta"), due(candidate(used = 30)))
    }

    @Test fun notWithinFiveMinutesOfTheLimit() = assertEquals(emptyList<String>(), due(candidate(used = 55)))

    @Test fun notAfterTheFewLeftWarning() = assertEquals(emptyList<String>(), due(candidate(), fewLeft = setOf("insta")))

    @Test fun notWhileLearning() = assertEquals(emptyList<String>(), due(candidate(usual = null)))

    @Test fun neverInQuietHours() = assertEquals(emptyList<String>(), due(candidate(), quiet = true))

    @Test fun oncePerAppPerDay() =
        assertEquals(emptyList<String>(), due(candidate(state = RescueState(sentOn = today, judged = false))))

    @Test fun twiceADayAtMost() {
        val sent = RescueState(sentOn = today, judged = false)
        assertEquals(emptyList<String>(), due(candidate(state = sent, pkg = "a"), candidate(state = sent, pkg = "b"), candidate(pkg = "c")))
        assertEquals(listOf("c"), due(candidate(state = sent, pkg = "a"), candidate(pkg = "c")))
    }

    @Test fun twoDueAtOnceBothGo() = assertEquals(2, due(candidate(pkg = "a"), candidate(pkg = "b"), candidate(pkg = "c")).size)

    @Test fun notWhilePaused() {
        val paused = RescueState(pausedUntil = today.plusDays(1))
        assertEquals(emptyList<String>(), due(candidate(state = paused)))
        assertEquals(listOf("insta"), due(candidate(state = RescueState(pausedUntil = today))))
    }

    @Test fun earlyMorningUsualTimeStartsAtMidnight() {
        assertEquals(0, Rescue.nudgeMinute(10))
        assertEquals(listOf("insta"), due(candidate(usual = 10), now = 0))
    }

    // ---- When to look again ----

    private fun next(vararg c: RescueCandidate, now: Int, quietLeft: Int = 0) =
        Rescue.minutesUntilNextCheck(c.toList(), today, now, emptySet(), quietLeft)

    @Test fun nextCheckAtTheHalfHour() = assertEquals(60, next(candidate(), now = elevenPm - 90))

    @Test fun nextCheckWhenHalfCouldBeUsed() = assertEquals(20, next(candidate(used = 10), now = elevenPm - 25))

    @Test fun noCheckWhenHalfCannotComeInTime() = assertNull(next(candidate(used = 0), now = elevenPm - 25))

    @Test fun noCheckAfterTheUsualTime() = assertNull(next(candidate(), now = elevenPm + 5))

    @Test fun quietHoursPushTheCheckToTheirEnd() {
        assertEquals(10, next(candidate(), now = elevenPm - 20, quietLeft = 10))
        assertNull(next(candidate(), now = elevenPm - 20, quietLeft = 40))
    }

    @Test fun noCheckOnceTheDailyCapIsUsed() {
        val sent = RescueState(sentOn = today, judged = false)
        assertNull(next(candidate(state = sent, pkg = "a"), candidate(state = sent, pkg = "b"), candidate(pkg = "c"), now = elevenPm - 90))
    }

    @Test fun quietMinutesLeftCrossesMidnight() {
        assertEquals(0, Rescue.quietMinutesLeft(12 * 60, 22 * 60, 7 * 60))
        assertEquals(8 * 60, Rescue.quietMinutesLeft(23 * 60, 22 * 60, 7 * 60))
        assertEquals(0, Rescue.quietMinutesLeft(23 * 60, 22 * 60, 22 * 60))
    }

    // ---- Backing off ----

    private val sentYesterday = Rescue.sent(RescueState(), today.minusDays(1))

    private fun ignoreOn(state: RescueState, day: LocalDate) = Rescue.afterOutcome(Rescue.sent(state, day), RescueOutcome.OVER)

    @Test fun keptResetsEverything() {
        val state = sentYesterday.copy(style = RescueStyle.FOCUS_OFFER, ignoredInARow = 1)
        assertEquals(RescueState(sentOn = today.minusDays(1), judged = true), Rescue.afterOutcome(state, RescueOutcome.KEPT))
    }

    @Test fun unknownChangesNothing() {
        val after = Rescue.afterOutcome(sentYesterday.copy(ignoredInARow = 1), RescueOutcome.UNKNOWN)
        assertEquals(1, after.ignoredInARow)
        assertTrue(after.judged)
    }

    @Test fun judgedOnlyOnce() {
        val once = Rescue.afterOutcome(sentYesterday, RescueOutcome.OVER)
        assertEquals(once, Rescue.afterOutcome(once, RescueOutcome.OVER))
    }

    @Test fun twoIgnoresSwitchToTheFocusOffer() {
        var s = ignoreOn(RescueState(), today.minusDays(2))
        assertEquals(RescueStyle.HEADS_UP, s.style)
        s = ignoreOn(s, today.minusDays(1))
        assertEquals(RescueStyle.FOCUS_OFFER, s.style)
        assertEquals(0, s.ignoredInARow)
    }

    @Test fun twoMoreIgnoresPauseForSevenDaysThenOneMoreChance() {
        var s = RescueState(style = RescueStyle.FOCUS_OFFER)
        s = ignoreOn(s, today.minusDays(2))
        s = ignoreOn(s, today.minusDays(1))
        assertEquals(today.plusDays(7), s.pausedUntil)
        assertTrue(Rescue.isPaused(s, today.plusDays(6)))
        assertEquals(RescueStatus.Paused(today.plusDays(7)), Rescue.status(elevenPm, s, today))
        // Back after the pause: a single ignore pauses it again.
        val back = today.plusDays(7)
        assertEquals(false, Rescue.isPaused(s, back))
        s = ignoreOn(s, back)
        assertEquals(back.plusDays(8), s.pausedUntil)
    }

    @Test fun resumeStartsOver() = assertEquals(RescueState(), Rescue.resumed())

    @Test fun statusShowsLearningAndWatching() {
        assertEquals(RescueStatus.Learning, Rescue.status(null, RescueState(), today))
        assertEquals(RescueStatus.Watching(elevenPm), Rescue.status(elevenPm, RescueState(), today))
    }

    // ---- Copy and storage ----

    @Test fun copySaysOnlyWhatIsMeasured() {
        assertEquals("Instagram: 20 min left today", Rescue.title(app(40)))
        assertEquals(
            "You usually go over this limit around 11:00 pm. Staying under adds +20 at midnight.",
            Rescue.text(RescueStyle.HEADS_UP, "11:00 pm"),
        )
        assertTrue(Rescue.text(RescueStyle.FOCUS_OFFER, "11:00 pm").contains("15 min focus session"))
    }

    @Test fun encodeRoundTrips() {
        val s = RescueState(RescueStyle.FOCUS_OFFER, 1, today.plusDays(3), today, judged = false)
        assertEquals(s, Rescue.decode(Rescue.encode(s)))
        assertEquals(RescueState(), Rescue.decode(Rescue.encode(RescueState())))
    }

    @Test fun unreadableStateStartsFresh() {
        assertEquals(RescueState(), Rescue.decode(null))
        assertEquals(RescueState(), Rescue.decode("garbage"))
        assertEquals(RescueState(), Rescue.decode("NOPE|x|||1"))
    }

    private companion object {
        val NO: Int? = null
        const val UNKNOWN = -1
    }
}
