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
        repo.completeFocus(started)
        assertNull(db.focusSessions().running())
        assertEquals(25, db.focusSessions().forDate("2026-01-01").single().creditedMinutes)
    }

    @Test fun endingEarlyCreditsNothing() = runBlocking {
        val id = repo.startFocus(25).id
        repo.endFocusEarly(id)
        assertEquals(0, db.focusSessions().forDate("2026-01-01").single().creditedMinutes)
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

    @Test fun deleteEverythingEmptiesTables() = runBlocking {
        repo.saveApp("a", "A", AppCategory.LIMITED, 30)
        repo.startFocus(10)
        repo.deleteEverything()
        assertTrue(db.trackedApps().getAll().isEmpty())
        assertNull(db.focusSessions().running())
        assertNull(db.scores().lastDaily())
    }
}
