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

    @Test fun deleteEverythingEmptiesTables() = runBlocking {
        repo.saveApp("a", "A", AppCategory.LIMITED, 30)
        repo.startFocus(10)
        repo.deleteEverything()
        assertTrue(db.trackedApps().getAll().isEmpty())
        assertNull(db.focusSessions().running())
        assertNull(db.scores().lastDaily())
    }
}
