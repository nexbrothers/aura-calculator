package app.aurio.domain

import java.time.LocalDate

/** One settled day of one limited app, as Rescue sees it. [known] is false on Unknown days. */
data class RescueDay(val date: LocalDate, val known: Boolean, val crossedAtMinute: Int?)

/** How a nudge reads: a plain heads-up, or (after it was ignored) an offer of a short focus session. */
enum class RescueStyle { HEADS_UP, FOCUS_OFFER }

/** What happened on a day a nudge went out, judged once that day has settled. */
enum class RescueOutcome { KEPT, OVER, UNKNOWN }

/**
 * Rescue's memory for one app. [sentOn] is the last day a nudge went out; [judged] turns true
 * once that day's outcome has been applied. Paused while today is before [pausedUntil].
 */
data class RescueState(
    val style: RescueStyle = RescueStyle.HEADS_UP,
    val ignoredInARow: Int = 0,
    val pausedUntil: LocalDate? = null,
    val sentOn: LocalDate? = null,
    val judged: Boolean = true,
)

/** One app Rescue is ready to watch today: its terms and usage, when it usually goes over, and its memory. */
data class RescueCandidate(val app: WatchedApp, val usualMinute: Int?, val state: RescueState)

sealed interface RescueStatus {
    data object Learning : RescueStatus
    data class Watching(val usualMinute: Int) : RescueStatus
    data class Paused(val until: LocalDate) : RescueStatus
}

/**
 * Smart Aura Rescue: one gentle nudge shortly before the time an app usually goes over its limit,
 * learned on the phone from the minute each limit was crossed. Pure: no Android, no clock.
 * Rescue never adds or removes aura; it only decides when a reminder could help.
 *
 * - It learns from the last 7 settled days, skipping Unknown days, and needs at least 4 known
 *   days with the limit crossed on 3 of them. The usual time is the median crossing minute.
 * - A nudge goes out from 30 minutes before that time until it, only once at least half the limit
 *   is used, never after the "5 min left" warning, once per app per day and twice a day at most.
 * - A nudge followed by a kept limit resets everything. Two ignored days in a row switch to the
 *   focus offer; two more pause the app for 7 days, after which one more ignore pauses it again.
 */
object Rescue {
    const val HISTORY_DAYS = 7L
    const val MIN_KNOWN_DAYS = 4
    const val MIN_OVER_DAYS = 3
    const val LEAD_MINUTES = 30
    const val MIN_USED_PERCENT = 50
    const val MAX_PER_DAY = 2
    const val IGNORES_BEFORE_CHANGE = 2
    const val PAUSE_DAYS = 7L
    const val FOCUS_OFFER_MINUTES = 15

    /** The usual minute of the day the limit is crossed, or null while there is too little to go on. */
    fun usualCrossing(history: List<RescueDay>, today: LocalDate): Int? {
        val known = history.filter { it.known && it.date < today && !it.date.isBefore(today.minusDays(HISTORY_DAYS)) }
        val crossings = known.mapNotNull { it.crossedAtMinute }.sorted()
        if (known.size < MIN_KNOWN_DAYS || crossings.size < MIN_OVER_DAYS) return null
        val mid = crossings.size / 2
        return if (crossings.size % 2 == 1) crossings[mid] else (crossings[mid - 1] + crossings[mid]) / 2
    }

    /** The first minute a nudge may go out on a day the limit is usually crossed at [usualMinute]. */
    fun nudgeMinute(usualMinute: Int) = (usualMinute - LEAD_MINUTES).coerceAtLeast(0)

    fun isPaused(state: RescueState, today: LocalDate) = state.pausedUntil?.let { today.isBefore(it) } == true

    fun status(usualMinute: Int?, state: RescueState, today: LocalDate): RescueStatus = when {
        isPaused(state, today) -> RescueStatus.Paused(state.pausedUntil!!)
        usualMinute != null -> RescueStatus.Watching(usualMinute)
        else -> RescueStatus.Learning
    }

    /**
     * The candidates to nudge now. [fewLeftSent] are the apps that already had "5 min left" today.
     * Nothing goes out in quiet hours: a nudge that arrives without sound helps no one.
     */
    fun due(
        candidates: List<RescueCandidate>,
        today: LocalDate,
        nowMinute: Int,
        fewLeftSent: Set<String>,
        quiet: Boolean,
    ): List<RescueCandidate> {
        if (quiet) return emptyList()
        val left = MAX_PER_DAY - candidates.count { it.state.sentOn == today }
        if (left <= 0) return emptyList()
        return candidates
            .filter { c -> waitMinutes(c, today, nowMinute, fewLeftSent) == 0 }
            .sortedBy { it.app.minutesLeft }
            .take(left)
    }

    /**
     * Minutes until the soonest moment a nudge could be due, at least 1, or null when none can be
     * today. Usage grows at most one minute per minute, so "half the limit used" cannot come sooner
     * than its distance. In quiet hours the wait runs at least to their end ([quietMinutesLeft]).
     */
    fun minutesUntilNextCheck(
        candidates: List<RescueCandidate>,
        today: LocalDate,
        nowMinute: Int,
        fewLeftSent: Set<String>,
        quietMinutesLeft: Int,
    ): Int? {
        if (candidates.count { it.state.sentOn == today } >= MAX_PER_DAY) return null
        return candidates.mapNotNull { c ->
            val usual = c.usualMinute ?: return@mapNotNull null
            val wait = maxOf(waitMinutes(c, today, nowMinute, fewLeftSent) ?: return@mapNotNull null, quietMinutesLeft)
            wait.takeIf { nowMinute + it < usual }
        }.minOrNull()?.coerceAtLeast(1)
    }

    /** Minutes until [c] is due (0 = now), or null when it cannot be due again today. */
    private fun waitMinutes(c: RescueCandidate, today: LocalDate, nowMinute: Int, fewLeftSent: Set<String>): Int? {
        val usual = c.usualMinute ?: return null
        val app = c.app
        if (isPaused(c.state, today) || c.state.sentOn == today || app.packageName in fewLeftSent) return null
        if (nowMinute >= usual || app.minutesLeft <= LimitWatch.WARN_AT_MINUTES_LEFT) return null
        val half = (app.limitMinutes * MIN_USED_PERCENT + 99) / 100
        val wait = maxOf(nudgeMinute(usual) - nowMinute, half - app.usedMinutes, 0)
        return wait.takeIf { nowMinute + it < usual }
    }

    /** Marks a nudge sent [today]; it is judged once [today] has settled. */
    fun sent(state: RescueState, today: LocalDate) = state.copy(sentOn = today, judged = false)

    /** Applies the settled outcome of the day the last nudge went out. Applying it twice changes nothing. */
    fun afterOutcome(state: RescueState, outcome: RescueOutcome): RescueState {
        if (state.judged) return state
        val sentOn = state.sentOn ?: return state.copy(judged = true)
        return when (outcome) {
            RescueOutcome.UNKNOWN -> state.copy(judged = true)
            RescueOutcome.KEPT -> state.copy(style = RescueStyle.HEADS_UP, ignoredInARow = 0, judged = true)
            RescueOutcome.OVER -> {
                val ignored = state.ignoredInARow + 1
                when {
                    ignored < IGNORES_BEFORE_CHANGE -> state.copy(ignoredInARow = ignored, judged = true)
                    state.style == RescueStyle.HEADS_UP -> state.copy(style = RescueStyle.FOCUS_OFFER, ignoredInARow = 0, judged = true)
                    // Paused for the 7 days after, then one more chance: a single ignore pauses again.
                    else -> state.copy(ignoredInARow = IGNORES_BEFORE_CHANGE - 1, pausedUntil = sentOn.plusDays(PAUSE_DAYS + 1), judged = true)
                }
            }
        }
    }

    /** "Resume" from app detail: start over as if Rescue had never nudged this app. */
    fun resumed() = RescueState()

    fun title(app: WatchedApp) = "${app.label}: ${app.minutesLeft} min left today"

    /** [usualTime] is the usual crossing time, already formatted for the phone's clock. */
    fun text(style: RescueStyle, usualTime: String) = when (style) {
        RescueStyle.HEADS_UP ->
            "You usually go over this limit around $usualTime. Staying under adds +${AuraScoring.UNDER_LIMIT_BONUS} at midnight."
        RescueStyle.FOCUS_OFFER ->
            "You usually go over this limit around $usualTime. A $FOCUS_OFFER_MINUTES min focus session now adds up to +$FOCUS_OFFER_MINUTES aura."
    }

    /** Minutes from [nowMinute] until quiet hours end, 0 outside them. Same rules as LimitWatch.inQuietHours. */
    fun quietMinutesLeft(nowMinute: Int, startMinute: Int, endMinute: Int): Int =
        if (!LimitWatch.inQuietHours(nowMinute, startMinute, endMinute)) 0 else Math.floorMod(endMinute - nowMinute, 24 * 60)

    // One line per app in app_meta: "HEADS_UP|0|2026-11-09|2026-11-01|1" (style, ignored in a row,
    // paused until, sent on, judged). Empty fields are null; anything unreadable starts fresh.

    fun encode(state: RescueState): String = listOf(
        state.style.name, state.ignoredInARow.toString(), state.pausedUntil?.toString().orEmpty(),
        state.sentOn?.toString().orEmpty(), if (state.judged) "1" else "0",
    ).joinToString("|")

    fun decode(stored: String?): RescueState {
        val f = stored?.split("|")?.takeIf { it.size == 5 } ?: return RescueState()
        return runCatching {
            RescueState(
                style = RescueStyle.valueOf(f[0]),
                ignoredInARow = f[1].toInt(),
                pausedUntil = f[2].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                sentOn = f[3].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                judged = f[4] == "1",
            )
        }.getOrDefault(RescueState())
    }
}
