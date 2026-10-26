package app.aurio.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Dates are ISO yyyy-MM-dd strings in the user's local zone. */

@Entity(tableName = "tracked_app")
data class TrackedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    /** LIMITED, NEUTRAL or ESSENTIAL (AppCategory name). */
    val category: String,
    /** Daily limit in minutes; null unless LIMITED. */
    val limitMinutes: Int?,
    val addedAt: Long,
)

@Entity(tableName = "usage_day", primaryKeys = ["date", "packageName"])
data class UsageDayEntity(
    val date: String,
    val packageName: String,
    /** Null means Usage Access was off: Unknown, never a bonus or penalty. */
    val minutes: Int?,
    val updatedAt: Long,
)

@Entity(tableName = "focus_session", indices = [Index("date")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val plannedMinutes: Int,
    /** Wall-clock end time; remaining time is computed from this, not a counter. */
    val endsAt: Long,
    /** RUNNING, COMPLETED or ENDED_EARLY (never costs aura). */
    val status: String,
    /** Minutes credited; 0 unless COMPLETED. */
    val creditedMinutes: Int,
    /** Local date the session ended on (credited to that day). */
    val date: String,
)

/** Frozen ledger lines written only by midnight settlement. */
@Entity(tableName = "score_entry", indices = [Index("date")])
data class ScoreEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    /** FOCUS, LIMIT_BONUS, LIMIT_PENALTY */
    val kind: String,
    val packageName: String?,
    val points: Int,
    val note: String,
)

@Entity(tableName = "daily_score")
data class DailyScoreEntity(
    @PrimaryKey val date: String,
    val delta: Int,
    val totalAfter: Int,
    /** True when usage was Unknown for part or all of the day. */
    val partial: Boolean,
    val streakAfter: Int,
    val settledAt: Long,
)

@Entity(tableName = "level_event", indices = [Index("date")])
data class LevelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val fromLevel: String,
    val toLevel: String,
    val at: Long,
)

/** Small key-value rows (schema flags, last settled date). */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
