package app.aurio.data

import android.content.Context
import android.util.Log
import app.aurio.AurioApplication
import app.aurio.data.db.DailyScoreEntity
import app.aurio.data.db.FocusSessionEntity
import app.aurio.data.db.UsageDayEntity
import app.aurio.data.db.terms
import app.aurio.domain.AppTerms
import app.aurio.domain.DayRecord
import app.aurio.domain.DayRules
import app.aurio.domain.LevelChange
import app.aurio.domain.LevelUps
import app.aurio.data.settings.Settings
import app.aurio.domain.AppCategory
import app.aurio.domain.AppUsage
import app.aurio.domain.AuraLevel
import app.aurio.domain.AuraScoring
import app.aurio.domain.DailyScore
import app.aurio.domain.FocusSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.aurio.domain.DebugTime
import app.aurio.domain.SystemAurioClock
import app.aurio.focus.FocusAlarm
import app.aurio.focus.FocusNotifications
import app.aurio.limits.LimitAlarm
import app.aurio.limits.LimitCheck
import app.aurio.limits.LimitNotifications
import app.aurio.widget.Widgets
import app.aurio.ui.levelup.ShareCard
import java.time.LocalDate

/** Everything the screens show, derived from what is saved on the phone. */
data class AurioState(
    /** False until the saved data has been read once. */
    val loaded: Boolean = false,
    val settings: Settings = Settings(),
    /** Aura banked by settled days. Today's lines are added on top. */
    val bankedScore: Int = 0,
    val sessions: List<FocusSession> = emptyList(),
    /** Today's apps, scored by the terms recorded for today (see DayRules). */
    val apps: List<AppUsage> = emptyList(),
    /** Current settings per package; they apply from tomorrow where they differ from [apps]. */
    val tracked: Map<String, AppTerms> = emptyMap(),
    val usageAccess: Boolean = false,
    /** Why today's limits are not being counted, or null when they are. */
    val notCounted: NotCountedReason? = null,
    /**
     * Minutes measured today per package while access is on, even on a day that is not counted
     * ([NotCountedReason.OFF_EARLIER_TODAY]). For display only: scoring always uses [apps].
     */
    val measuredMinutes: Map<String, Int> = emptyMap(),
    val streakDays: Int = 0,
    val bestStreak: Int = 0,
    val running: FocusSessionEntity? = null,
    val history: List<DailyScoreEntity> = emptyList(),
) {
    val userName: String get() = settings.name
    val onboardingStep: Int get() = settings.onboardingStep
    val cloudBackup: Boolean get() = settings.cloudBackup

    val daily: DailyScore
        get() = AuraScoring.today(
            sessions,
            if (usageAccess) apps else apps.map { it.copy(usedMinutes = null) },
        )
    val score: Int get() = (bankedScore + daily.total).coerceIn(0, AuraScoring.MAX_SCORE)
    val level: AuraLevel get() = AuraLevel.of(score)
    val limitedApps: List<AppUsage> get() = apps.filter { it.category == AppCategory.LIMITED }
}

/** Single source of truth. Reads from and writes to the on-device database and settings. */
@OptIn(ExperimentalCoroutinesApi::class)
object AurioStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var app: AurioApplication? = null
    @Volatile private var syncJob: Job? = null

    /** True while Aurio is on screen; it then shows a completed session itself, so no notification. */
    @Volatile var visible: Boolean = false

    private val _state = MutableStateFlow(AurioState())
    val state: StateFlow<AurioState> = _state.asStateFlow()

    private val usageAccess = MutableStateFlow(false)
    private val today = MutableStateFlow(SystemAurioClock.today())

    private val repo get() = app!!.repository
    private val settingsStore get() = app!!.settings

    fun init(application: AurioApplication) {
        if (app != null) return
        app = application
        val dayData = today.flatMapLatest { d ->
            combine(repo.usageFor(d), repo.focusFor(d), usageAccess, repo.running, repo.accessWasOff(d)) { usage, focus, access, running, wasOff ->
                DayData(usage, focus, access, running, wasOff)
            }
        }
        scope.launch {
            combine(repo.trackedApps, dayData, repo.lastSettled, repo.history(), settingsStore.settings) { tracked, day, last, history, settings ->
                val current = tracked.associate { it.packageName to it.terms() }
                val recorded = day.usage.mapNotNull { r -> r.terms()?.let { r.packageName to it } }.toMap()
                val minutes = day.usage.associate { it.packageName to it.minutes }
                // Once access was seen off today, today settles as Unknown, so Home shows Unknown too.
                val known = day.access && !day.accessWasOff
                val apps = DayRules.appsForDay(recorded, current) { pkg -> if (known) (minutes[pkg] ?: 0) else null }
                AurioState(
                    loaded = true,
                    settings = settings,
                    bankedScore = last?.totalAfter ?: 0,
                    sessions = repo.toFocusSessions(day.focus),
                    apps = apps,
                    tracked = current,
                    usageAccess = day.access,
                    measuredMinutes = if (day.access) minutes.mapNotNull { (k, v) -> v?.let { k to it } }.toMap() else emptyMap(),
                    notCounted = when {
                        !day.access -> NotCountedReason.ACCESS_OFF
                        day.accessWasOff -> NotCountedReason.OFF_EARLIER_TODAY
                        else -> null
                    },
                    streakDays = last?.streakAfter ?: 0,
                    bestStreak = history.maxOfOrNull { it.streakAfter } ?: 0,
                    running = day.running,
                    history = history,
                )
            }.collect { _state.value = it }
        }
        scope.launch {
            combine(repo.levelEvents, repo.levelSeenThrough, repo.lastSettled) { events, seen, last ->
                val unseen = events.filter { it.id > seen }
                    .map { LevelChange(it.id, AuraLevel.valueOf(it.fromLevel), AuraLevel.valueOf(it.toLevel)) }
                LevelUps.toCelebrate(unseen, AuraLevel.of(last?.totalAfter ?: 0))
                    ?.let { LevelUp(it, last?.totalAfter ?: 0, seenThroughId = unseen.maxOf { e -> e.id }) }
            }.collect { _levelUp.value = it }
        }
        // Redraw home-screen widgets whenever something they show changes: a session, a sync,
        // a limit edit, midnight settlement. Nothing else triggers a redraw.
        scope.launch {
            _state.filter { it.loaded }
                .map { s -> listOf(s.score, s.level, s.daily.total, s.usageAccess, s.apps.map { Triple(it.label, it.usedMinutes, it.limitMinutes) }) }
                .distinctUntilChanged()
                .collect { Widgets.refreshAll(application) }
        }
    }

    // ---- Level-ups -------------------------------------------------------------------------

    /** A level reached at a past midnight that the user has not been shown yet. */
    data class LevelUp(val level: AuraLevel, val score: Int, val seenThroughId: Long)

    private val _levelUp = MutableStateFlow<LevelUp?>(null)
    val levelUp: StateFlow<LevelUp?> = _levelUp.asStateFlow()

    /** The moment is shown once: everything up to [levelUp] counts as seen, celebrated or not. */
    fun markLevelUpSeen(levelUp: LevelUp) { scope.launch { repo.markLevelsSeen(levelUp.seenThroughId) } }

    private class DayData(
        val usage: List<UsageDayEntity>,
        val focus: List<FocusSessionEntity>,
        val access: Boolean,
        val running: FocusSessionEntity?,
        val accessWasOff: Boolean,
    )

    // ---- Usage and the daily rhythm ------------------------------------------------------

    /**
     * Called whenever the app comes to the front and once a minute while visible.
     * Order matters: finish an overdue focus session first, then freeze finished days,
     * then record today's numbers.
     */
    fun sync(context: Context) {
        // Reading a day of usage events can be slow; never stack a second read behind it.
        if (syncJob?.isActive == true) return
        val granted = UsageAccess.isGranted(context) || DebugTime.enabled
        syncJob = scope.launch {
            try {
                syncOnce(context, granted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed read must never crash the app or mark a day Unknown. Each day settles in
                // one transaction, so stopping here is safe; the next sync (a minute later) retries.
                Log.w("AurioStore", "Sync failed; will retry", e)
            }
        }
    }

    private suspend fun syncOnce(context: Context, granted: Boolean) {
        val r = repo
        r.reconcileFocus()
        // Android drops pending alarms when exact alarms are turned off or the app is force-stopped.
        r.runningFocus()?.let { FocusAlarm.schedule(app!!, it.endsAt) }
        // Day one is measured from the install moment, not midnight (DayRules.measureFrom).
        val startDay = r.startDate()
        val installedAt = r.installedAt()
        r.settlePastDays { date ->
            when {
                DebugTime.enabled -> DebugTime.usageByDate[date] ?: emptyMap()
                granted -> UsageAccess.minutesForDate(context, date, notBefore = if (date == startDay) installedAt else null)
                else -> null
            }
        }
        val day = SystemAurioClock.today()
        if (!granted) r.markAccessOff(day)
        r.recordTerms(day)
        today.value = day
        usageAccess.value = granted
        if (granted) {
            val minutes = if (DebugTime.enabled) DebugTime.usageByDate[day] ?: emptyMap() else UsageAccess.minutesSoFar(context, day, notBefore = r.measureFrom(day))
            r.recordUsage(day, r.scoredPackages(day).associateWith { minutes[it] ?: 0 })
            LimitCheck.run(app!!, minutes)
        } else {
            LimitCheck.run(app!!)
        }
    }

    // ---- Onboarding ----------------------------------------------------------------------

    fun setOnboardingStep(step: Int) { scope.launch { settingsStore.setOnboardingStep(step) } }

    fun finishOnboarding(name: String) {
        scope.launch {
            settingsStore.setName(name)
            repo.markStartDate()
            settingsStore.setOnboardingStep(5)
        }
    }

    // ---- Apps and limits -----------------------------------------------------------------

    // Each edit re-checks limit warnings at once, since a new limit moves the next threshold.

    fun saveApps(chosen: Map<String, Pair<String, AppCategory>>) { scope.launch { repo.replaceApps(chosen); recheckLimits() } }

    fun setLimit(packageName: String, minutes: Int) { scope.launch { repo.setLimit(packageName, minutes); recheckLimits() } }

    /** Moves an app to another category. Like any loosening edit, it applies from tomorrow (DayRules). */
    fun setCategory(packageName: String, category: AppCategory) {
        val saved = state.value.tracked[packageName] ?: return
        scope.launch { repo.saveApp(packageName, saved.label, category, saved.limitMinutes); recheckLimits() }
    }

    private suspend fun recheckLimits() {
        try {
            LimitCheck.run(app!!)
        } catch (e: Exception) {
            Log.w("AurioStore", "Limit check failed", e)
        }
    }

    /** The last 7 days of one app, today included (today's bar comes from the live state). */
    fun appWeek(packageName: String): Flow<List<DayRecord>> =
        repo.appRecords(packageName, SystemAurioClock.today().minusDays(6))

    /** One app's settled aura over the 7 days before today. */
    fun appPointsLastWeek(packageName: String): Flow<List<Int>> {
        val today = SystemAurioClock.today()
        return repo.appPoints(packageName, today.minusDays(7), today)
    }

    /** Completed focus minutes over the last 7 days, today included. */
    fun focusMinutesLastWeek(): Flow<Int> = repo.focusMinutesSince(SystemAurioClock.today().minusDays(6))

    /** The install day; days before it have no score to show. */
    fun startDate(): Flow<java.time.LocalDate?> = repo.startDateFlow()

    // ---- Focus ---------------------------------------------------------------------------

    suspend fun startFocus(minutes: Int) {
        val row = repo.startFocus(minutes)
        FocusAlarm.schedule(app!!, row.endsAt)
        // Show it at once; the database flow will confirm a moment later.
        _state.update { it.copy(running = row) }
    }

    /** Completes the session (if the end-time alarm has not already) and returns what it earned. */
    suspend fun completeFocus(session: FocusSessionEntity): FocusResult {
        FocusAlarm.cancel(app!!)
        return repo.completeFocus(session)
    }

    /** Ends the session; if its time was already up it completes instead (see the result). */
    suspend fun endFocusEarly(session: FocusSessionEntity): FocusResult {
        FocusAlarm.cancel(app!!)
        val result = repo.endFocusEarly(session)
        _state.update { it.copy(running = null) }
        return result
    }

    fun setFocusReminders(on: Boolean) { scope.launch { settingsStore.setFocusReminders(on) } }

    /** Turning warnings off stops the checks at once; turning them on starts watching again. */
    fun setLimitWarnings(on: Boolean) { scope.launch { settingsStore.setLimitWarnings(on); recheckLimits() } }

    fun setQuietHours(startMinute: Int, endMinute: Int) { scope.launch { settingsStore.setQuietHours(startMinute, endMinute) } }

    /** The notification permission is asked once, after the first completed session. */
    fun setNotificationPromptSeen() { scope.launch { settingsStore.setNotificationPromptSeen() } }

    /** The limit-warnings prompt is asked once; a yes starts watching limits straight away. */
    fun setLimitPromptSeen() { scope.launch { settingsStore.setLimitPromptSeen(); recheckLimits() } }

    // ---- Settings ------------------------------------------------------------------------

    fun setName(name: String) { scope.launch { settingsStore.setName(name) } }
    fun setReduceMotion(on: Boolean) { scope.launch { settingsStore.setReduceMotion(on) } }
    fun setDefaultFocusMinutes(m: Int) { scope.launch { settingsStore.setDefaultFocusMinutes(m) } }

    suspend fun exportJson(): String = repo.exportJson()

    fun deleteAllData() {
        FocusAlarm.cancel(app!!)
        FocusNotifications.clear(app!!)
        LimitAlarm.cancel(app!!)
        LimitNotifications.clear(app!!)
        ShareCard.clear(app!!)
        scope.launch {
            repo.deleteEverything()
            settingsStore.clear()
        }
    }
}

/**
 * Why today's limits show no time and earn no bonus or penalty (the product's "Unknown" state).
 * Shown to users in plain words; see ui/components/NotCounted.kt.
 */
enum class NotCountedReason {
    /** Usage access is off right now. */
    ACCESS_OFF,
    /** Access is on now but was off earlier today, so today stays uncounted (DayRules). */
    OFF_EARLIER_TODAY,
}
