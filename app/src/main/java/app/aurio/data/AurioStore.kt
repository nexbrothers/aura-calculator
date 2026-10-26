package app.aurio.data

import android.content.Context
import app.aurio.AurioApplication
import app.aurio.data.db.DailyScoreEntity
import app.aurio.data.db.FocusSessionEntity
import app.aurio.data.db.UsageDayEntity
import app.aurio.data.db.terms
import app.aurio.domain.AppTerms
import app.aurio.domain.DayRules
import app.aurio.data.settings.Settings
import app.aurio.domain.AppCategory
import app.aurio.domain.AppUsage
import app.aurio.domain.AuraLevel
import app.aurio.domain.AuraScoring
import app.aurio.domain.DailyScore
import app.aurio.domain.FocusSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.aurio.domain.DebugTime
import app.aurio.domain.SystemAurioClock
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
                    streakDays = last?.streakAfter ?: 0,
                    bestStreak = history.maxOfOrNull { it.streakAfter } ?: 0,
                    running = day.running,
                    history = history,
                )
            }.collect { _state.value = it }
        }
    }

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
            val r = repo
            r.reconcileFocus()
            r.settlePastDays { date ->
                when {
                    DebugTime.enabled -> DebugTime.usageByDate[date] ?: emptyMap()
                    granted -> UsageAccess.minutesForDate(context, date)
                    else -> null
                }
            }
            val day = SystemAurioClock.today()
            if (!granted) r.markAccessOff(day)
            r.recordTerms(day)
            today.value = day
            usageAccess.value = granted
            if (granted) {
                val minutes = if (DebugTime.enabled) DebugTime.usageByDate[day] ?: emptyMap() else UsageAccess.minutesSoFar(context, day)
                r.recordUsage(day, r.scoredPackages(day).associateWith { minutes[it] ?: 0 })
            }
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

    fun saveApps(chosen: Map<String, Pair<String, AppCategory>>) { scope.launch { repo.replaceApps(chosen) } }

    fun setLimit(packageName: String, minutes: Int) { scope.launch { repo.setLimit(packageName, minutes) } }

    // ---- Focus ---------------------------------------------------------------------------

    suspend fun startFocus(minutes: Int) {
        val row = repo.startFocus(minutes)
        // Show it at once; the database flow will confirm a moment later.
        _state.update { it.copy(running = row) }
    }

    suspend fun completeFocus(session: FocusSessionEntity) = repo.completeFocus(session)

    suspend fun endFocusEarly(id: Long) {
        repo.endFocusEarly(id)
        _state.update { it.copy(running = null) }
    }

    // ---- Settings ------------------------------------------------------------------------

    fun setName(name: String) { scope.launch { settingsStore.setName(name) } }
    fun setReduceMotion(on: Boolean) { scope.launch { settingsStore.setReduceMotion(on) } }
    fun setDefaultFocusMinutes(m: Int) { scope.launch { settingsStore.setDefaultFocusMinutes(m) } }

    suspend fun exportJson(): String = repo.exportJson()

    fun deleteAllData() {
        scope.launch {
            repo.deleteEverything()
            settingsStore.clear()
        }
    }
}
