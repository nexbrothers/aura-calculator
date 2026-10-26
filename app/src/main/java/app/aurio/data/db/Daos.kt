package app.aurio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedAppDao {
    @Query("SELECT * FROM tracked_app ORDER BY label")
    fun observeAll(): Flow<List<TrackedAppEntity>>

    @Query("SELECT * FROM tracked_app ORDER BY label")
    suspend fun getAll(): List<TrackedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: TrackedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<TrackedAppEntity>)

    @Query("SELECT * FROM tracked_app WHERE packageName = :pkg")
    suspend fun get(pkg: String): TrackedAppEntity?

    @Query("UPDATE tracked_app SET limitMinutes = :minutes WHERE packageName = :pkg AND category = 'LIMITED'")
    suspend fun setLimit(pkg: String, minutes: Int)

    @Query("DELETE FROM tracked_app WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface UsageDayDao {
    /** Never REPLACE: that would erase the terms recorded for the day. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: UsageDayEntity)

    @Query("UPDATE usage_day SET minutes = :minutes, updatedAt = :at WHERE date = :date AND packageName = :pkg")
    suspend fun setMinutes(date: String, pkg: String, minutes: Int?, at: Long)

    /** Fills terms only on rows that have none yet (rows from schema 1, or written before tracking). */
    @Query("UPDATE usage_day SET label = :label, category = :category, limitMinutes = :limit WHERE date = :date AND packageName = :pkg AND category IS NULL")
    suspend fun fillTerms(date: String, pkg: String, label: String, category: String, limit: Int?)

    @Query("UPDATE usage_day SET limitMinutes = :limit WHERE date = :date AND packageName = :pkg")
    suspend fun setLimit(date: String, pkg: String, limit: Int?)

    @Query("SELECT * FROM usage_day WHERE date = :date AND packageName = :pkg")
    suspend fun get(date: String, pkg: String): UsageDayEntity?

    @Query("SELECT * FROM usage_day WHERE date = :date")
    suspend fun forDate(date: String): List<UsageDayEntity>

    @Query("SELECT * FROM usage_day WHERE date = :date")
    fun observeDate(date: String): Flow<List<UsageDayEntity>>

    @Query("SELECT * FROM usage_day WHERE packageName = :pkg AND date >= :from ORDER BY date")
    fun observeApp(pkg: String, from: String): Flow<List<UsageDayEntity>>
}

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insert(s: FocusSessionEntity): Long

    @Query("UPDATE focus_session SET status = :status, creditedMinutes = :credited, date = :date WHERE id = :id")
    suspend fun finish(id: Long, status: String, credited: Int, date: String)

    @Query("SELECT * FROM focus_session WHERE status = 'RUNNING' LIMIT 1")
    suspend fun running(): FocusSessionEntity?

    @Query("SELECT * FROM focus_session WHERE date = :date ORDER BY startedAt")
    fun observeDate(date: String): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_session WHERE status = 'RUNNING' LIMIT 1")
    fun observeRunning(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_session ORDER BY startedAt")
    suspend fun all(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_session WHERE date = :date ORDER BY startedAt")
    suspend fun forDate(date: String): List<FocusSessionEntity>
}

@Dao
interface ScoreDao {
    @Insert
    suspend fun insertEntries(rows: List<ScoreEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDaily(row: DailyScoreEntity)

    @Insert
    suspend fun insertLevelEvent(e: LevelEventEntity)

    @Query("SELECT * FROM daily_score ORDER BY date DESC LIMIT 1")
    suspend fun lastDaily(): DailyScoreEntity?

    @Query("SELECT * FROM daily_score ORDER BY date DESC LIMIT 1")
    fun observeLastDaily(): Flow<DailyScoreEntity?>

    @Query("SELECT * FROM daily_score WHERE date >= :from ORDER BY date")
    fun observeSince(from: String): Flow<List<DailyScoreEntity>>

    @Query("SELECT * FROM score_entry WHERE date = :date ORDER BY id")
    suspend fun entriesFor(date: String): List<ScoreEntryEntity>

    @Query("SELECT * FROM daily_score ORDER BY date")
    suspend fun allDaily(): List<DailyScoreEntity>

    @Query("SELECT * FROM score_entry ORDER BY id")
    suspend fun allEntries(): List<ScoreEntryEntity>

    @Query("SELECT * FROM level_event ORDER BY at DESC")
    fun observeLevelEvents(): Flow<List<LevelEventEntity>>
}

@Dao
interface MetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(m: AppMetaEntity)

    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>
}
