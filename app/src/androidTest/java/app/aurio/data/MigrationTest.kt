package app.aurio.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aurio.data.db.AurioDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every schema change must keep a user's history. Add a test here for each new version. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AurioDatabase::class.java)

    @Test fun v1ToV2KeepsUsageAndAddsEmptyTerms() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO usage_day (date, packageName, minutes, updatedAt) VALUES ('2026-01-01', 'ig', 23, 1)")
            db.execSQL("INSERT INTO tracked_app (packageName, label, category, limitMinutes, addedAt) VALUES ('ig', 'Instagram', 'LIMITED', 30, 1)")
        }

        helper.runMigrationsAndValidate(dbName, 2, true).use { db ->
            db.query("SELECT minutes, label, category, limitMinutes FROM usage_day WHERE packageName = 'ig'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(23, c.getInt(0))
                assertTrue(c.isNull(1))
                assertTrue(c.isNull(2))
                assertTrue(c.isNull(3))
            }
            db.query("SELECT COUNT(*) FROM tracked_app").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        }
    }
}
