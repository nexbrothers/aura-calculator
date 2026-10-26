package app.aurio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@Database(
    entities = [
        TrackedAppEntity::class, UsageDayEntity::class, FocusSessionEntity::class,
        ScoreEntryEntity::class, DailyScoreEntity::class, LevelEventEntity::class, AppMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AurioDatabase : RoomDatabase() {
    abstract fun trackedApps(): TrackedAppDao
    abstract fun usageDays(): UsageDayDao
    abstract fun focusSessions(): FocusSessionDao
    abstract fun scores(): ScoreDao
    abstract fun meta(): MetaDao

    /** Wipes every table (Settings → Delete all data). */
    suspend fun wipe() = withTransaction { clearAllTables() }

    companion object {
        private const val NAME = "aurio.db"

        fun build(context: Context): AurioDatabase =
            Room.databaseBuilder(context.applicationContext, AurioDatabase::class.java, NAME)
                // No destructive fallback: a missing migration must fail loudly, never erase history.
                .build()
    }
}
