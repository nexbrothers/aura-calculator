package app.aurio.data

import app.aurio.data.db.*
import app.aurio.domain.AppCategory
import app.aurio.domain.AppUsage
import app.aurio.domain.AurioClock
import app.aurio.domain.FocusSession
import app.aurio.domain.LineKind
import app.aurio.domain.Settlement
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/** Single entry point the UI and workers use. Scoring maths lives in domain/, not here. */
class AurioRepository(
    private val db: AurioDatabase,
    private val clock: AurioClock,
) {
    private val settleLock = Mutex()

    val trackedApps: Flow<List<TrackedAppEntity>> = db.trackedApps().observeAll()
    val lastSettled: Flow<DailyScoreEntity?> = db.scores().observeLastDaily()
    val running: Flow<FocusSessionEntity?> = db.focusSessions().observeRunning()

    suspend fun trackedAppsNow() = db.trackedApps().getAll()

    fun history(): Flow<List<DailyScoreEntity>> = db.scores().observeSince(clock.today().minusDays(365).toString())
    fun usageFor(date: LocalDate): Flow<List<UsageDayEntity>> = db.usageDays().observeDate(date.toString())
    fun focusFor(date: LocalDate): Flow<List<FocusSessionEntity>> = db.focusSessions().observeDate(date.toString())

    // ---- Tracked apps -------------------------------------------------------------------

    suspend fun saveApp(pkg: String, label: String, category: AppCategory, limitMinutes: Int?) {
        val limit = if (category == AppCategory.LIMITED) (limitMinutes ?: DEFAULT_LIMIT).coerceIn(5, 240) else null
        db.trackedApps().upsert(TrackedAppEntity(pkg, label, category.name, limit, clock.nowMillis()))
    }

    /** Makes the tracked set equal [chosen]; keeps existing limits and add-dates for apps that stay. */
    suspend fun replaceApps(chosen: Map<String, Pair<String, AppCategory>>) = db.withTransaction {
        val existing = db.trackedApps().getAll().associateBy { it.packageName }
        existing.keys.filter { it !in chosen }.forEach { db.trackedApps().delete(it) }
        chosen.forEach { (pkg, pair) ->
            val (label, category) = pair
            val old = existing[pkg]
            val limit = if (category == AppCategory.LIMITED) (old?.limitMinutes ?: DEFAULT_LIMIT) else null
            db.trackedApps().upsert(TrackedAppEntity(pkg, label, category.name, limit, old?.addedAt ?: clock.nowMillis()))
        }
    }

    suspend fun setLimit(pkg: String, minutes: Int) = db.trackedApps().setLimit(pkg, minutes.coerceIn(5, 240))

    // ---- Usage --------------------------------------------------------------------------

    suspend fun recordUsage(date: LocalDate, minutesByPackage: Map<String, Int?>) {
        val now = clock.nowMillis()
        db.usageDays().upsertAll(minutesByPackage.map { (p, m) -> UsageDayEntity(date.toString(), p, m, now) })
    }

    // ---- Focus --------------------------------------------------------------------------

    /** Starts a session. The end time is stored so a killed app can still finish it correctly. */
    suspend fun startFocus(minutes: Int): FocusSessionEntity {
        val now = clock.nowMillis()
        val row = FocusSessionEntity(
            startedAt = now, plannedMinutes = minutes, endsAt = now + minutes * 60_000L,
            status = "RUNNING", creditedMinutes = 0, date = clock.today().toString(),
        )
        return row.copy(id = db.focusSessions().insert(row))
    }

    /** Ending early never costs aura; it just credits nothing. */
    suspend fun endFocusEarly(id: Long) =
        db.focusSessions().finish(id, "ENDED_EARLY", 0, clock.today().toString())

    suspend fun completeFocus(session: FocusSessionEntity) =
        db.focusSessions().finish(session.id, "COMPLETED", session.plannedMinutes, dateOf(session.endsAt).toString())

    /** A running session whose end time passed while the app was closed is completed now. */
    suspend fun reconcileFocus() {
        val r = db.focusSessions().running() ?: return
        if (r.endsAt <= clock.nowMillis()) completeFocus(r)
    }

    fun timeLabel(millis: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis)).lowercase()

    fun toFocusSessions(rows: List<FocusSessionEntity>): List<FocusSession> =
        rows.filter { it.status == "COMPLETED" }.map { FocusSession(it.creditedMinutes, timeLabel(it.startedAt)) }

    // ---- Midnight settlement ------------------------------------------------------------

    suspend fun markStartDate() {
        if (db.meta().get(START_DATE) == null) db.meta().put(AppMetaEntity(START_DATE, clock.today().toString()))
    }

    /**
     * Freezes every finished day that is not settled yet, oldest first, each in one transaction.
     * Safe to call any time and from any thread: a lock keeps the worker and the app apart.
     * [usageProvider] returns real minutes for a past date, or null when they cannot be read
     * (then stored numbers are used, and a missing number means Unknown).
     */
    suspend fun settlePastDays(usageProvider: (LocalDate) -> Map<String, Int>?) = settleLock.withLock {
        val start = db.meta().get(START_DATE)?.let(LocalDate::parse) ?: return@withLock
        val today = clock.today()
        val last = db.scores().lastDaily()
        var date = last?.let { LocalDate.parse(it.date).plusDays(1) } ?: start
        var total = last?.totalAfter ?: 0
        var streak = last?.streakAfter ?: 0
        val apps = db.trackedApps().getAll()

        while (date.isBefore(today)) {
            val endOfDay = date.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
            val provided = usageProvider(date)
            val stored = db.usageDays().forDate(date.toString()).associate { it.packageName to it.minutes }
            val usages = apps.filter { it.addedAt < endOfDay }.map { a ->
                val used: Int? = if (provided != null) (provided[a.packageName] ?: 0) else stored[a.packageName]
                AppUsage(a.packageName, a.label, AppCategory.valueOf(a.category), used, a.limitMinutes ?: 0)
            }
            val sessions = db.focusSessions().forDate(date.toString()).filter { it.status == "COMPLETED" }
            val day = Settlement.settle(usages, sessions.map { it.creditedMinutes }, total, streak, isFirstDay = date == start)
            val settledAt = clock.nowMillis()
            db.withTransaction {
                db.scores().insertEntries(day.entries.map {
                    ScoreEntryEntity(date = date.toString(), kind = it.kind.name, packageName = it.packageName, points = it.points, note = it.note)
                })
                db.scores().insertDaily(DailyScoreEntity(date.toString(), day.delta, day.totalAfter, day.partial, day.streakAfter, settledAt))
                if (day.levelFrom != day.levelTo) {
                    db.scores().insertLevelEvent(LevelEventEntity(date = date.toString(), fromLevel = day.levelFrom.name, toLevel = day.levelTo.name, at = settledAt))
                }
            }
            total = day.totalAfter
            streak = day.streakAfter
            date = date.plusDays(1)
        }
    }

    // ---- Data export / delete -----------------------------------------------------------

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "Aurio")
        root.put("exportedAt", Instant.ofEpochMilli(clock.nowMillis()).toString())
        root.put("trackedApps", JSONArray().also { a ->
            db.trackedApps().getAll().forEach {
                a.put(JSONObject().put("package", it.packageName).put("label", it.label).put("category", it.category).put("limitMinutes", it.limitMinutes ?: JSONObject.NULL))
            }
        })
        root.put("focusSessions", JSONArray().also { a ->
            db.focusSessions().all().forEach {
                a.put(JSONObject().put("date", it.date).put("startedAt", it.startedAt).put("plannedMinutes", it.plannedMinutes).put("status", it.status).put("creditedMinutes", it.creditedMinutes))
            }
        })
        root.put("dailyScores", JSONArray().also { a ->
            db.scores().allDaily().forEach {
                a.put(JSONObject().put("date", it.date).put("change", it.delta).put("total", it.totalAfter).put("partial", it.partial).put("streak", it.streakAfter))
            }
        })
        root.put("scoreLines", JSONArray().also { a ->
            db.scores().allEntries().forEach {
                a.put(JSONObject().put("date", it.date).put("kind", it.kind).put("package", it.packageName ?: JSONObject.NULL).put("points", it.points).put("note", it.note))
            }
        })
        return root.toString(2)
    }

    suspend fun deleteEverything() = db.wipe()

    private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(clock.zone()).toLocalDate()

    companion object {
        const val DEFAULT_LIMIT = 30
        private const val START_DATE = "start_date"
    }
}
