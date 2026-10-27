package app.aurio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayRulesTest {
    private fun limited(limit: Int, label: String = "Instagram") = AppTerms(label, AppCategory.LIMITED, limit)
    private fun neutral(label: String = "Instagram") = AppTerms(label, AppCategory.NEUTRAL, null)

    @Test fun lowerLimitAppliesToday() {
        assertEquals(limited(20), DayRules.termsAfterEdit(limited(30), limited(20)))
    }

    @Test fun higherLimitWaitsUntilTomorrow() {
        assertEquals(limited(30), DayRules.termsAfterEdit(limited(30), limited(45)))
    }

    @Test fun categoryChangeWaitsUntilTomorrow() {
        assertEquals(limited(30), DayRules.termsAfterEdit(limited(30), neutral()))
        assertEquals(neutral(), DayRules.termsAfterEdit(neutral(), limited(30)))
    }

    @Test fun removingAnAppStillScoresItToday() {
        assertEquals(limited(30), DayRules.termsAfterEdit(limited(30), null))
    }

    @Test fun raisingAfterLoweringKeepsTheLowerLimitToday() {
        val lowered = DayRules.termsAfterEdit(limited(30), limited(20))
        assertEquals(limited(20), DayRules.termsAfterEdit(lowered, limited(30)))
    }

    // ---- Which saved minutes count at settlement ----------------------------------------

    @Test fun fullDayReadingAtSettlementIsUsed() {
        assertEquals(47, DayRules.settledMinutes(mapOf("yt" to 47), "yt", 5))
    }

    @Test fun appMissingFromFullDayReadingNeverDropsBelowWhatWasSaved() {
        // Uninstalled before midnight: Android forgets it, but 90 min were already saved.
        assertEquals(90, DayRules.settledMinutes(emptyMap(), "yt", 90))
        assertEquals(0, DayRules.settledMinutes(emptyMap(), "yt", null))
    }

    @Test fun savedReadingAloneIsUnknown() {
        // Access on in the morning, no full-day reading at settlement: a partial day must not pay +20.
        assertNull(DayRules.settledMinutes(null, "yt", 5))
        assertNull(DayRules.settledMinutes(null, "yt", null))
    }

    @Test fun dayWithAccessSeenOffIsNeverReadBack() {
        assertEquals(false, DayRules.canReadFullDay(ageDays = 1, accessWasOff = true))
        assertEquals(true, DayRules.canReadFullDay(ageDays = 1, accessWasOff = false))
    }

    @Test fun daysOlderThanAndroidKeepsDailyAreNotRead() {
        assertEquals(true, DayRules.canReadFullDay(DayRules.MAX_READABLE_AGE_DAYS, false))
        assertEquals(false, DayRules.canReadFullDay(DayRules.MAX_READABLE_AGE_DAYS + 1, false))
        assertEquals(false, DayRules.canReadFullDay(0, false)) // today is not finished
    }

    @Test fun partialMorningReadingGivesNoBonus() {
        val apps = DayRules.appsForDay(mapOf("ig" to limited(30)), emptyMap()) { pkg ->
            DayRules.settledMinutes(null, pkg, 5)
        }
        val day = Settlement.settle(apps, emptyList(), previousTotal = 100, previousStreak = 2, isFirstDay = false)
        assertEquals(0, day.delta)
        assertEquals(true, day.partial)
    }

    // ---- Counting starts at install ----------------------------------------------------

    @Test fun installDayIsMeasuredFromInstallTime() {
        val midnight = 1_000_000L
        val installedAt = midnight + 9 * 3_600_000L // set up at 9:00
        assertEquals(installedAt, DayRules.measureFrom(midnight, installedAt))
    }

    @Test fun laterDaysAreMeasuredFromMidnight() {
        val midnight = 1_000_000L
        assertEquals(midnight, DayRules.measureFrom(midnight, midnight - 86_400_000L))
        assertEquals(midnight, DayRules.measureFrom(midnight, null))
    }

    @Test fun accessOffNeverCountsAgainstTheInstallDay() {
        val install = java.time.LocalDate.parse("2026-10-28")
        assertEquals(false, DayRules.accessOffCounts(install, install))
        assertEquals(true, DayRules.accessOffCounts(install.plusDays(1), install))
        assertEquals(false, DayRules.accessOffCounts(install, null)) // not set up yet
    }

    @Test fun sameLimitChangesNothing() {
        assertEquals(limited(30), DayRules.termsAfterEdit(limited(30), limited(30)))
    }

    @Test fun lowerLimitDoesNotMakeANeutralDayLimited() {
        // Moved to Limited earlier today (pending), then lowered: today stays Neutral.
        assertEquals(neutral(), DayRules.termsAfterEdit(neutral(), limited(10)))
    }

    @Test fun recordedNeutralNeverScoresEvenIfNowLimited() {
        val apps = DayRules.appsForDay(mapOf("ig" to neutral()), mapOf("ig" to limited(30))) { 500 }
        val day = Settlement.settle(apps, emptyList(), previousTotal = 100, previousStreak = 0, isFirstDay = false)
        assertEquals(0, day.delta)
        assertEquals(emptyList<SettledEntry>(), day.entries)
    }

    @Test fun recordedTermsWithUnknownMinutesGiveNoBonusAndPartialDay() {
        val apps = DayRules.appsForDay(mapOf("ig" to limited(30)), emptyMap()) { null }
        val day = Settlement.settle(apps, emptyList(), previousTotal = 100, previousStreak = 2, isFirstDay = false)
        assertEquals(0, day.delta)
        assertEquals(true, day.partial)
    }

    @Test fun recordedTermsBeatCurrentSettings() {
        val apps = DayRules.appsForDay(
            recorded = mapOf("ig" to limited(30)),
            current = mapOf("ig" to limited(60)),
        ) { 45 }
        val ig = apps.single()
        assertEquals(30, ig.limitMinutes)
        // 15 over the limit that applied that day, not under the raised one.
        assertEquals(LimitStatus.Over(15, 15), AuraScoring.limitStatus(ig))
    }

    @Test fun removedAppIsStillScoredForTheDayItWasRecorded() {
        val apps = DayRules.appsForDay(recorded = mapOf("yt" to limited(30, "YouTube")), current = emptyMap()) { 50 }
        assertEquals(listOf("yt"), apps.map { it.packageName })
        assertEquals(LimitStatus.Over(20, 20), AuraScoring.limitStatus(apps.single()))
    }

    @Test fun unrecordedAppFallsBackToCurrentSettings() {
        val apps = DayRules.appsForDay(
            recorded = mapOf("ig" to limited(30)),
            current = mapOf("ig" to limited(60), "x" to limited(15, "X")),
        ) { null }
        assertEquals(listOf("Instagram", "X"), apps.map { it.label })
        assertEquals(15, apps.last().limitMinutes)
        assertNull(apps.last().usedMinutes)
    }

    @Test fun designDayScoresTheSameThroughRecordedTerms() {
        val recorded = mapOf(
            "ig" to limited(30, "Instagram"), "yt" to limited(30, "YouTube"),
            "snap" to limited(20, "Snapchat"), "x" to limited(15, "X"),
        )
        val used = mapOf("ig" to 23, "yt" to 47, "snap" to 12, "x" to 4)
        val apps = DayRules.appsForDay(recorded, emptyMap()) { used[it] }
        val day = Settlement.settle(apps, listOf(25, 25), previousTotal = 612, previousStreak = 6, isFirstDay = false)
        assertEquals(93, day.delta)
    }
}
