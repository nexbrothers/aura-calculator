package app.aurio.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aurio.data.db.*
import app.aurio.domain.AppCategory
import app.aurio.domain.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AurioDatabaseTest {
    private lateinit var db: AurioDatabase
    private lateinit var repo: AurioRepository
    private val clock = FakeClock(1_767_225_600_000L) // 2026-01-01T00:00Z

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AurioDatabase::class.java).allowMainThreadQueries().build()
        repo = AurioRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun limitedAppKeepsLimitOthersDoNot() = runBlocking {
        repo.saveApp("a", "A", AppCategory.LIMITED, 45)
        repo.saveApp("b", "B", AppCategory.NEUTRAL, 45)
        val apps = db.trackedApps().getAll().associateBy { it.packageName }
        assertEquals(45, apps["a"]!!.limitMinutes)
        assertNull(apps["b"]!!.limitMinutes)
    }

    @Test fun unknownUsageStaysNull() = runBlocking {
        repo.recordUsage(java.time.LocalDate.parse("2026-01-01"), mapOf("a" to null, "b" to 12))
        val rows = db.usageDays().forDate("2026-01-01").associateBy { it.packageName }
        assertNull(rows["a"]!!.minutes)
        assertEquals(12, rows["b"]!!.minutes)
    }

    @Test fun runningSessionSurvivesAndCompletes() = runBlocking {
        val started = repo.startFocus(25)
        val running = db.focusSessions().running()!!
        assertEquals(clock.nowMillis() + 25 * 60_000L, running.endsAt)
        assertEquals(started.id, running.id)
        clock.advanceMinutes(25)
        repo.completeFocus(started)
        assertNull(db.focusSessions().running())
        assertEquals(25, db.focusSessions().forDate("2026-01-01").single().creditedMinutes)
    }

    @Test fun alarmAndTimerScreenCompletingTogetherCountsOnce() = runBlocking {
        val s = repo.startFocus(25)
        clock.advanceMinutes(25)
        val first = repo.completeFocus(s)
        val second = repo.completeFocus(s)
        assertTrue(first.justCompleted)
        assertFalse(second.justCompleted)
        assertEquals(25, first.earned)
        assertEquals(25, second.earned) // the screen still shows the right number
        assertEquals(1, db.focusSessions().forDate("2026-01-01").count { it.status == "COMPLETED" })
    }

    private suspend fun runSession(minutes: Int): FocusResult {
        val s = repo.startFocus(minutes)
        clock.advanceMinutes(minutes.toLong())
        return repo.completeFocus(s)
    }

    @Test fun earnedRespectsTheDailyCap() = runBlocking {
        assertEquals(60, runSession(60).earned)
        assertEquals(30, runSession(30).earned)
        assertEquals(10, runSession(25).earned) // hits the 100 cap partway
        assertEquals(0, runSession(25).earned)
    }

    @Test fun endingAfterTheAlarmCompletedKeepsTheCredit() = runBlocking {
        val s = repo.startFocus(25)
        clock.advanceMinutes(25)
        repo.completeFocus(s)
        clock.advanceMinutes(1)
        assertTrue(repo.endFocusEarly(s).completed)
        assertEquals(25, db.focusSessions().forDate("2026-01-01").single().creditedMinutes)
    }

    @Test fun endingEarlyCreditsNothing() = runBlocking {
        val s = repo.startFocus(25)
        clock.advanceMinutes(10)
        assertFalse(repo.endFocusEarly(s).completed)
        assertEquals(0, db.focusSessions().forDate("2026-01-01").single().creditedMinutes)
    }

    @Test fun endingAtZeroCompletesInsteadOfLosingTheCredit() = runBlocking {
        val s = repo.startFocus(25)
        clock.advanceMinutes(25)
        val result = repo.endFocusEarly(s)
        assertTrue(result.completed)
        assertEquals(25, result.earned)
    }

    @Test fun completingBeforeTheEndTimeIsRefused() = runBlocking {
        val s = repo.startFocus(25)
        clock.advanceMinutes(24)
        val result = repo.completeFocus(s)
        assertFalse(result.completed)
        assertEquals("RUNNING", db.focusSessions().get(s.id)!!.status)
    }

    @Test fun endThenCompleteReportsNotCompleted() = runBlocking {
        val s = repo.startFocus(25)
        clock.advanceMinutes(5)
        repo.endFocusEarly(s)
        clock.advanceMinutes(20)
        val result = repo.completeFocus(s)
        assertFalse(result.completed)
        assertEquals(0, db.focusSessions().get(s.id)!!.creditedMinutes)
    }

    @Test fun sessionEndingJustBeforeMidnightIsInThatDaysSettlement() = runBlocking {
        repo.markStartDate()
        clock.advanceMinutes(23 * 60 + 30)
        val s = repo.startFocus(25) // ends 23:55 on 2026-01-01, still running
        clock.advanceMinutes(40)    // 00:10 next day; nobody completed it yet
        repo.settlePastDays { emptyMap() }
        assertEquals("COMPLETED", db.focusSessions().get(s.id)!!.status)
        assertEquals(25, db.scores().entriesFor("2026-01-01").single { it.kind == "FOCUS" }.points)
    }

    @Test fun sessionCompletedAfterItsDaySettledGoesToTheNextOpenDay() = runBlocking {
        repo.markStartDate()
        val s = repo.startFocus(25)
        clock.advanceDays(1)
        // Settle day 1 without the session, as if the clock had been moved back while it ran.
        db.focusSessions().finish(s.id, "PAUSED_FOR_TEST", 0, "2026-01-01")
        repo.settlePastDays { emptyMap() }
        db.focusSessions().insert(s.copy(id = 0, status = "RUNNING"))
        val late = db.focusSessions().running()!!
        val result = repo.completeFocus(late)
        assertTrue(result.completed)
        assertEquals("2026-01-02", db.focusSessions().get(late.id)!!.date)
        assertEquals(25, result.earned)
    }

    @Test fun earnedShownMatchesWhatSettlementCredits() = runBlocking {
        repo.markStartDate()
        val shown = listOf(runSession(60).earned, runSession(30).earned, runSession(25).earned)
        clock.advanceDays(1)
        repo.settlePastDays { emptyMap() }
        val credited = db.scores().entriesFor("2026-01-01").filter { it.kind == "FOCUS" }.map { it.points }
        assertEquals(shown, credited)
    }

    // ---- Terms that applied each day (DayRules) ------------------------------------------

    private suspend fun todayRow(pkg: String) = db.usageDays().get("2026-01-01", pkg)!!

    @Test fun loweringALimitAppliesToday() = runBlocking {
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.setLimit("ig", 20)
        assertEquals(20, todayRow("ig").limitMinutes)
        assertEquals(20, db.trackedApps().get("ig")!!.limitMinutes)
    }

    @Test fun raisingALimitAppliesTomorrow() = runBlocking {
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.setLimit("ig", 45)
        assertEquals(30, todayRow("ig").limitMinutes)
        assertEquals(45, db.trackedApps().get("ig")!!.limitMinutes)
    }

    @Test fun recordingUsageKeepsTheDaysTerms() = runBlocking {
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.recordUsage(java.time.LocalDate.parse("2026-01-01"), mapOf("ig" to 12))
        repo.recordUsage(java.time.LocalDate.parse("2026-01-01"), mapOf("ig" to 14))
        val row = todayRow("ig")
        assertEquals(14, row.minutes)
        assertEquals(30, row.limitMinutes)
        assertEquals("LIMITED", row.category)
    }

    @Test fun settlementUsesTheLimitThatAppliedThatDay() = runBlocking {
        repo.markStartDate()
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.recordUsage(java.time.LocalDate.parse("2026-01-01"), mapOf("ig" to 45))
        repo.setLimit("ig", 60) // raised mid-day: must not erase today's 15 minutes over
        clock.advanceDays(1)
        repo.settlePastDays { mapOf("ig" to 45) }
        val entry = db.scores().entriesFor("2026-01-01").single()
        assertEquals(-15, entry.points)
    }

    @Test fun removedAppIsStillSettledForThatDay() = runBlocking {
        repo.markStartDate()
        repo.replaceApps(mapOf("yt" to ("YouTube" to AppCategory.LIMITED)))
        repo.recordUsage(java.time.LocalDate.parse("2026-01-01"), mapOf("yt" to 50))
        repo.replaceApps(emptyMap())
        clock.advanceDays(1)
        repo.settlePastDays { mapOf("yt" to 50) }
        assertEquals(-20, db.scores().entriesFor("2026-01-01").single().points)
    }

    @Test fun removedAppKeepsGettingMinutesToday() = runBlocking {
        repo.replaceApps(mapOf("ig" to ("Instagram" to AppCategory.LIMITED)))
        repo.replaceApps(emptyMap())
        assertEquals(setOf("ig"), repo.scoredPackages(java.time.LocalDate.parse("2026-01-01")))
        clock.advanceDays(1)
        assertTrue(repo.scoredPackages(java.time.LocalDate.parse("2026-01-02")).isEmpty())
    }

    @Test fun saveAppLoweringALimitAppliesToday() = runBlocking {
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 20)
        assertEquals(20, todayRow("ig").limitMinutes)
    }

    @Test fun movingToLimitedTodayEarnsNothingToday() = runBlocking {
        repo.markStartDate()
        repo.saveApp("ig", "Instagram", AppCategory.NEUTRAL, null)
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.recordUsage(java.time.LocalDate.parse("2026-01-01"), mapOf("ig" to 0))
        clock.advanceDays(1)
        repo.settlePastDays { null }
        assertTrue(db.scores().entriesFor("2026-01-01").isEmpty())
    }

    @Test fun accessOffAllDaySettlesAsUnknown() = runBlocking {
        repo.markStartDate()
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        clock.advanceDays(1)
        repo.recordTerms(java.time.LocalDate.parse("2026-01-02"))
        repo.settlePastDays { null }
        clock.advanceDays(1)
        repo.settlePastDays { null }
        val day2 = db.scores().allDaily().single { it.date == "2026-01-02" }
        assertTrue(day2.partial)
        assertEquals(0, day2.delta)
        assertTrue(db.scores().entriesFor("2026-01-02").isEmpty())
    }

    @Test fun morningReadingWithAccessOffAtSettlementIsUnknown() = runBlocking {
        repo.markStartDate()
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.recordUsage(java.time.LocalDate.parse("2026-01-01"), mapOf("ig" to 5)) // morning, access on
        clock.advanceDays(1)
        repo.settlePastDays { null } // access off now: the 5 min was only part of the day
        assertTrue(db.scores().entriesFor("2026-01-01").isEmpty())
        val day = db.scores().allDaily().single()
        assertTrue(day.partial)
        assertEquals(0, day.delta)
    }

    @Test fun dayWithAccessSeenOffSettlesUnknownWithoutReadingAndroid() = runBlocking {
        repo.markStartDate()
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        clock.advanceDays(1)
        repo.recordTerms(java.time.LocalDate.parse("2026-01-02"))
        repo.markAccessOff(java.time.LocalDate.parse("2026-01-02")) // off at some point, back on later
        clock.advanceDays(1)
        val asked = mutableListOf<java.time.LocalDate>()
        repo.settlePastDays { date -> asked += date; mapOf("ig" to 90) }
        assertEquals(listOf(java.time.LocalDate.parse("2026-01-01")), asked)
        assertTrue(db.scores().entriesFor("2026-01-02").isEmpty())
        assertTrue(db.scores().allDaily().single { it.date == "2026-01-02" }.partial)
    }

    @Test fun uninstalledAppKeepsItsSavedMinutes() = runBlocking {
        repo.markStartDate()
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.recordUsage(java.time.LocalDate.parse("2026-01-01"), mapOf("ig" to 90))
        clock.advanceDays(1)
        repo.settlePastDays { emptyMap() } // Android no longer knows the app
        assertEquals(-60, db.scores().entriesFor("2026-01-01").single().points)
    }

    @Test fun settledDayKeepsTheMinutesItWasScoredOn() = runBlocking {
        repo.markStartDate()
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.recordUsage(java.time.LocalDate.parse("2026-01-01"), mapOf("ig" to 12)) // partial, mid-day
        clock.advanceDays(1)
        repo.settlePastDays { mapOf("ig" to 41) } // Android's full-day record
        val row = db.usageDays().get("2026-01-01", "ig")!!
        assertEquals(41, row.minutes)
        assertEquals(30, row.limitMinutes)
        assertEquals(-11, db.scores().entriesFor("2026-01-01").single().points)
    }

    @Test fun unknownSettledDayIsSavedAsUnknown() = runBlocking {
        repo.markStartDate()
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.recordUsage(java.time.LocalDate.parse("2026-01-01"), mapOf("ig" to 12))
        clock.advanceDays(1)
        repo.settlePastDays { null }
        assertNull(db.usageDays().get("2026-01-01", "ig")!!.minutes)
    }

    @Test fun accessOffDuringOnboardingDoesNotMakeTheFirstDayUnknown() = runBlocking {
        val day1 = java.time.LocalDate.parse("2026-01-01")
        repo.markAccessOff(day1)           // a sync before the user reached the usage-access step
        repo.saveApp("ig", "Instagram", AppCategory.LIMITED, 30)
        repo.markStartDate()               // onboarding finished with access on
        assertFalse(repo.accessWasOff(day1).first())
        repo.recordUsage(day1, mapOf("ig" to 10))
        clock.advanceDays(1)
        repo.settlePastDays { mapOf("ig" to 10) }
        assertEquals(20, db.scores().entriesFor("2026-01-01").single().points)
    }

    @Test fun accessOffAfterOnboardingStillCounts() = runBlocking {
        repo.markStartDate()
        repo.markAccessOff(java.time.LocalDate.parse("2026-01-01"))
        assertTrue(repo.accessWasOff(java.time.LocalDate.parse("2026-01-01")).first())
    }

    @Test fun deleteEverythingEmptiesTables() = runBlocking {
        repo.saveApp("a", "A", AppCategory.LIMITED, 30)
        repo.startFocus(10)
        repo.deleteEverything()
        assertTrue(db.trackedApps().getAll().isEmpty())
        assertNull(db.focusSessions().running())
        assertNull(db.scores().lastDaily())
    }
}
