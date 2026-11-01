package app.aurio.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** One week since install, as the progress summary in Export reports it. */
data class WeekReport(
    val week: Int,
    val from: LocalDate,
    val to: LocalDate,
    /** False while the week has days that have not settled yet. */
    val complete: Boolean,
    /** Limited app-days that were counted (not Unknown). */
    val limitDaysCounted: Int,
    val limitDaysKept: Int,
    /** Minutes over limits, summed over all limited app-days. */
    val minutesOverLimits: Int,
    /** Average minutes per counted limited app-day, or null with none. */
    val averageMinutesPerLimitedApp: Int?,
    val focusSessionsCompleted: Int,
    val daysWithFocus: Int,
    val auraChange: Int,
)

/** How Rescue reminders turned out, judged once each day settled. */
data class RescueStats(val sent: Int = 0, val keptAfter: Int = 0, val overAfter: Int = 0, val notCounted: Int = 0)

/**
 * The progress summary for the 14-day test: plain weekly numbers from the phone's own records,
 * shared only when the user exports. Pure: no Android, no clock.
 */
object Validation {

    /**
     * Weeks of 7 days from [start] through [settledThrough], oldest first; the last may be
     * partial. [usage] are saved app-days with their terms; [focusDates] the date of each
     * completed session; [dailyDeltas] each settled day's aura change.
     */
    fun weeks(
        start: LocalDate,
        settledThrough: LocalDate?,
        usage: List<DayRecord>,
        focusDates: List<LocalDate>,
        dailyDeltas: Map<LocalDate, Int>,
    ): List<WeekReport> {
        settledThrough ?: return emptyList()
        if (settledThrough.isBefore(start)) return emptyList()
        val reports = mutableListOf<WeekReport>()
        var from = start
        var week = 1
        while (!from.isAfter(settledThrough)) {
            val to = from.plusDays(6)
            val last = minOf(to, settledThrough)
            fun inWeek(d: LocalDate) = !d.isBefore(from) && !d.isAfter(last)
            val limited = usage.filter { r ->
                inWeek(r.date) && r.minutes != null && r.terms?.category == AppCategory.LIMITED && r.terms.limitMinutes != null
            }
            val focus = focusDates.filter(::inWeek)
            reports += WeekReport(
                week = week, from = from, to = to, complete = !to.isAfter(settledThrough),
                limitDaysCounted = limited.size,
                limitDaysKept = limited.count { it.minutes!! <= it.terms!!.limitMinutes!! },
                minutesOverLimits = limited.sumOf { (it.minutes!! - it.terms!!.limitMinutes!!).coerceAtLeast(0) },
                averageMinutesPerLimitedApp = if (limited.isEmpty()) null else (limited.sumOf { it.minutes!! }.toDouble() / limited.size).roundToInt(),
                focusSessionsCompleted = focus.size,
                daysWithFocus = focus.toSet().size,
                auraChange = dailyDeltas.filterKeys(::inWeek).values.sum(),
            )
            from = from.plusDays(7)
            week++
        }
        return reports
    }

    /** Counts one more reminder sent, or one more outcome. */
    fun record(stats: RescueStats, outcome: RescueOutcome?): RescueStats = when (outcome) {
        null -> stats.copy(sent = stats.sent + 1)
        RescueOutcome.KEPT -> stats.copy(keptAfter = stats.keptAfter + 1)
        RescueOutcome.OVER -> stats.copy(overAfter = stats.overAfter + 1)
        RescueOutcome.UNKNOWN -> stats.copy(notCounted = stats.notCounted + 1)
    }

    // Stored as one line in app_meta: "sent|kept|over|unknown". Anything unreadable starts at zero.

    fun encode(stats: RescueStats) = "${stats.sent}|${stats.keptAfter}|${stats.overAfter}|${stats.notCounted}"

    fun decode(stored: String?): RescueStats {
        val n = stored?.split("|")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 4 } ?: return RescueStats()
        return RescueStats(n[0], n[1], n[2], n[3])
    }
}
