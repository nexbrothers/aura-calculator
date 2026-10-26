package app.aurio.data

import app.aurio.data.db.*
import app.aurio.domain.AppCategory
import app.aurio.domain.AuraScoring
import app.aurio.domain.AurioClock
import app.aurio.domain.DayRecord
import app.aurio.domain.DayRules
import app.aurio.domain.FocusSession
import app.aurio.domain.LineKind
import app.aurio.domain.Settlement
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    /** Packages scored on [date]: tracked now, plus any recorded that day (an app removed today still counts today). */
    suspend fun scoredPackages(date: LocalDate): Set<String> =
        db.trackedApps().getAll().map { it.packageName }.toSet() +
            db.usageDays().forDate(date.toString()).filter { it.category != null }.map { it.packageName }

    fun history(): Flow<List<DailyScoreEntity>> = db.scores().observeSince(clock.today().minusDays(365).toString())
    fun usageFor(date: LocalDate): Flow<List<UsageDayEntity>> = db.usageDays().observeDate(date.toString())
    fun focusFor(date: LocalDate): Flow<List<FocusSessionEntity>> = db.focusSessions().observeDate(date.toString())

    /** One app's saved days from [from] on (app detail). */
    fun appRecords(pkg: String, from: LocalDate): Flow<List<DayRecord>> =
        db.usageDays().observeApp(pkg, from.toString()).map { rows -> rows.map { DayRecord(LocalDate.parse(it.date), it.minutes, it.terms()) } }

    /** One app's settled aura points for days in [from, until). */
    fun appPoints(pkg: String, from: LocalDate, until: LocalDate): Flow<List<Int>> =
        db.scores().observePointsFor(pkg, from.toString(), until.toString())

    // ---- Tracked apps -------------------------------------------------------------------

    // Edits change tracked_app, which is what tomorrow uses. Today keeps the terms recorded
    // before the edit, except where DayRules lets a stricter change apply at once.

    suspend fun saveApp(pkg: String, label: String, category: AppCategory, limitMinutes: Int?) = db.withTransaction {
        recordTerms(clock.today())
        val limit = if (category == AppCategory.LIMITED) (limitMinutes ?: DEFAULT_LIMIT).coerceIn(5, 240) else null
        val addedAt = db.trackedApps().get(pkg)?.addedAt ?: clock.nowMillis()
        db.trackedApps().upsert(TrackedAppEntity(pkg, label, category.name, limit, addedAt))
        applyEditToToday(pkg)
        recordTerms(clock.today())
    }

    /** Makes the tracked set equal [chosen]; keeps existing limits and add-dates for apps that stay. */
    suspend fun replaceApps(chosen: Map<String, Pair<String, AppCategory>>) = db.withTransaction {
        recordTerms(clock.today())
        val existing = db.trackedApps().getAll().associateBy { it.packageName }
        existing.keys.filter { it !in chosen }.forEach { db.trackedApps().delete(it) }
        chosen.forEach { (pkg, pair) ->
            val (label, category) = pair
            val old = existing[pkg]
            val limit = if (category == AppCategory.LIMITED) (old?.limitMinutes ?: DEFAULT_LIMIT) else null
            db.trackedApps().upsert(TrackedAppEntity(pkg, label, category.name, limit, old?.addedAt ?: clock.nowMillis()))
        }
        // Apps added just now are scored from today.
        recordTerms(clock.today())
    }

    suspend fun setLimit(pkg: String, minutes: Int) = db.withTransaction {
        recordTerms(clock.today())
        db.trackedApps().setLimit(pkg, minutes.coerceIn(5, 240))
        applyEditToToday(pkg)
    }

    /** Lets a stricter edit reach today's recorded terms (DayRules); looser edits wait for tomorrow. */
    private suspend fun applyEditToToday(pkg: String) {
        val today = clock.today().toString()
        val todayTerms = db.usageDays().get(today, pkg)?.terms() ?: return
        val next = DayRules.termsAfterEdit(todayTerms, db.trackedApps().get(pkg)?.terms())
        if (next != todayTerms) db.usageDays().setLimit(today, pkg, next.limitMinutes)
    }

    // ---- Usage --------------------------------------------------------------------------

    /**
     * Records, once per day, the terms each tracked app is scored by on [date]. Later calls the
     * same day change nothing, so editing an app cannot rewrite today's terms behind DayRules.
     */
    suspend fun recordTerms(date: LocalDate) = db.withTransaction {
        val now = clock.nowMillis()
        db.trackedApps().getAll().forEach { a ->
            db.usageDays().insertIfAbsent(UsageDayEntity(date.toString(), a.packageName, null, now, a.label, a.category, a.limitMinutes))
            db.usageDays().fillTerms(date.toString(), a.packageName, a.label, a.category, a.limitMinutes)
        }
    }

    suspend fun recordUsage(date: LocalDate, minutesByPackage: Map<String, Int?>) = db.withTransaction {
        val now = clock.nowMillis()
        minutesByPackage.forEach { (p, m) ->
            db.usageDays().insertIfAbsent(UsageDayEntity(date.toString(), p, m, now))
            db.usageDays().setMinutes(date.toString(), p, m, now)
        }
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

    // Completing a session shares the settlement lock: a session must never be credited to a day
    // that settles without it.

    /**
     * Ends a session early: never costs aura, credits nothing. A session whose time is already up
     * is completed instead (a tap on End as the timer reaches zero must not lose the credit).
     */
    suspend fun endFocusEarly(session: FocusSessionEntity): FocusResult = settleLock.withLock {
        if (session.endsAt <= clock.nowMillis()) return@withLock completeLocked(session)
        db.focusSessions().finish(session.id, "ENDED_EARLY", 0, clock.today().toString())
        FocusResult(session.plannedMinutes, earned = 0, justCompleted = false, completed = false)
    }

    /**
     * Completes [session] once its end time has passed, and returns the aura it earned within the
     * daily focus cap. Safe to call twice: the end-time alarm and the timer screen may both try.
     */
    suspend fun completeFocus(session: FocusSessionEntity): FocusResult = settleLock.withLock { completeLocked(session) }

    /** A running session whose end time has passed is completed now; null if there was none. */
    suspend fun reconcileFocus(): FocusResult? = settleLock.withLock { reconcileLocked() }

    private suspend fun reconcileLocked(): FocusResult? {
        val r = db.focusSessions().running() ?: return null
        return if (r.endsAt <= clock.nowMillis()) completeLocked(r) else null
    }

    private suspend fun completeLocked(session: FocusSessionEntity): FocusResult = db.withTransaction {
        // Credited to the day it ended, unless that day has already settled (for example after
        // travelling west across midnight); then to the first day still open.
        val firstOpen = db.scores().lastDaily()?.let { LocalDate.parse(it.date).plusDays(1) }
        val ended = dateOf(session.endsAt)
        val date = (if (firstOpen != null && ended.isBefore(firstOpen)) firstOpen else ended).toString()
        val justCompleted = session.endsAt <= clock.nowMillis() &&
            db.focusSessions().finish(session.id, "COMPLETED", session.plannedMinutes, date) == 1
        val row = db.focusSessions().get(session.id)
        if (row?.status != "COMPLETED") {
            return@withTransaction FocusResult(session.plannedMinutes, earned = 0, justCompleted = false, completed = false)
        }
        val completed = db.focusSessions().forDate(row.date).filter { it.status == "COMPLETED" }
        val earned = AuraScoring.earnedBy(completed.map { it.creditedMinutes }, completed.indexOfFirst { it.id == session.id })
        FocusResult(session.plannedMinutes, earned, justCompleted, completed = true)
    }

    suspend fun runningFocus(): FocusSessionEntity? = db.focusSessions().running()

    fun timeLabel(millis: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis)).lowercase()

    fun toFocusSessions(rows: List<FocusSessionEntity>): List<FocusSession> =
        rows.filter { it.status == "COMPLETED" }.map { FocusSession(it.creditedMinutes, timeLabel(it.startedAt)) }

    // ---- Midnight settlement ------------------------------------------------------------

    /**
     * Records the first scored day when onboarding finishes. Access is always off at the start of
     * onboarding (the user turns it on in step 4), so that is not held against the first day.
     */
    suspend fun markStartDate() {
        if (db.meta().get(START_DATE) == null) {
            db.meta().put(AppMetaEntity(START_DATE, clock.today().toString()))
            db.meta().delete(accessOffKey(clock.today()))
        }
    }

    /**
     * Notes that Usage Access was seen off on [date]. That day then settles as Unknown even if
     * access comes back before midnight, matching what Home showed. Not before onboarding has
     * finished: nothing is scored yet, and the user has not been asked for access.
     */
    suspend fun markAccessOff(date: LocalDate) {
        if (db.meta().get(START_DATE) == null) return
        if (db.meta().get(accessOffKey(date)) == null) db.meta().put(AppMetaEntity(accessOffKey(date), "1"))
    }

    fun accessWasOff(date: LocalDate): Flow<Boolean> = db.meta().observe(accessOffKey(date)).map { it != null }

    private fun accessOffKey(date: LocalDate) = "access_off:$date"

    /**
     * Freezes every finished day that is not settled yet, oldest first, each in one transaction.
     * Safe to call any time and from any thread: a lock keeps the worker and the app apart.
     * [usageProvider] returns real minutes for a past date, or null when they cannot be read
     * (then the day is Unknown). It is not called for days with Usage Access seen off (see DayRules).
     */
    suspend fun settlePastDays(usageProvider: (LocalDate) -> Map<String, Int>?) = settleLock.withLock {
        // A session that ended before now belongs to a day about to settle: complete it first.
        reconcileLocked()
        val start = db.meta().get(START_DATE)?.let(LocalDate::parse) ?: return@withLock
        val today = clock.today()
        val last = db.scores().lastDaily()
        var date = last?.let { LocalDate.parse(it.date).plusDays(1) } ?: start
        var total = last?.totalAfter ?: 0
        var streak = last?.streakAfter ?: 0
        val apps = db.trackedApps().getAll()

        while (date.isBefore(today)) {
            val endOfDay = date.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
            val accessWasOff = db.meta().get(accessOffKey(date)) != null
            val ageDays = java.time.temporal.ChronoUnit.DAYS.between(date, today)
            val provided = if (DayRules.canReadFullDay(ageDays, accessWasOff)) usageProvider(date) else null
            val rows = db.usageDays().forDate(date.toString())
            val stored = rows.associate { it.packageName to it.minutes }
            val recorded = rows.mapNotNull { r -> r.terms()?.let { r.packageName to it } }.toMap()
            val current = apps.filter { it.addedAt < endOfDay }.associate { it.packageName to it.terms() }
            val usages = DayRules.appsForDay(recorded, current) { pkg ->
                DayRules.settledMinutes(provided, pkg, stored[pkg])
            }
            val sessions = db.focusSessions().forDate(date.toString()).filter { it.status == "COMPLETED" }
            val day = Settlement.settle(usages, sessions.map { it.creditedMinutes }, total, streak, isFirstDay = date == start)
            val settledAt = clock.nowMillis()
            db.withTransaction {
                // Freeze the day's usage as it was scored, so history shows the same minutes
                // (or Unknown) and terms the score came from, not the last reading during the day.
                usages.forEach { u ->
                    db.usageDays().insertIfAbsent(UsageDayEntity(date.toString(), u.packageName, null, settledAt, u.label, u.category.name, u.limitMinutes.takeIf { u.category == AppCategory.LIMITED }))
                    db.usageDays().fillTerms(date.toString(), u.packageName, u.label, u.category.name, u.limitMinutes.takeIf { u.category == AppCategory.LIMITED })
                    db.usageDays().setMinutes(date.toString(), u.packageName, u.usedMinutes, settledAt)
                }
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

/**
 * How a focus session ended. [completed] is its final state (false: ended early or not yet due);
 * [justCompleted] is true only for the call that completed it, which is the one to announce it.
 */
data class FocusResult(val minutes: Int, val earned: Int, val justCompleted: Boolean, val completed: Boolean)
