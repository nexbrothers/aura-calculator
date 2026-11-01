package app.aurio.domain

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** How one limited app's last 7 settled days compare with the 7 before them. */
data class WeekChange(
    val packageName: String,
    val label: String,
    val averageMinutes: Int,
    val previousAverageMinutes: Int,
    val daysKept: Int,
    val daysCounted: Int,
    val previousDaysKept: Int,
    val previousDaysCounted: Int,
) {
    /** Positive when the app was used less per day than the week before. */
    val minutesLessPerDay get() = previousAverageMinutes - averageMinutes

    val direction: Direction
        get() = when {
            abs(minutesLessPerDay) < Progress.SAME_WITHIN_MINUTES -> Direction.SAME
            minutesLessPerDay > 0 -> Direction.LESS
            else -> Direction.MORE
        }

    enum class Direction { LESS, SAME, MORE }
}

/**
 * Week-over-week progress, so a low score still shows what is getting better. Pure: no Android,
 * no clock. Only settled, counted days are compared (Unknown days and days an app was not
 * limited are left out), and each week needs at least 3 of them. Never changes aura.
 */
object Progress {
    const val MIN_DAYS_PER_WEEK = 3
    /** Daily averages closer than this read as "about the same". */
    const val SAME_WITHIN_MINUTES = 2

    /**
     * [records] are one app's saved days. This week is the 7 days before [today], the previous
     * week the 7 before that; days after [settledThrough] are not final yet and are skipped.
     * Null until both weeks have enough counted days.
     */
    fun weekChange(packageName: String, records: List<DayRecord>, today: LocalDate, settledThrough: LocalDate?): WeekChange? {
        settledThrough ?: return null
        val counted = records.filter { r ->
            r.minutes != null && r.terms?.category == AppCategory.LIMITED && r.terms.limitMinutes != null &&
                r.date.isBefore(today) && !r.date.isAfter(settledThrough)
        }
        val thisWeek = counted.filter { !it.date.isBefore(today.minusDays(7)) }
        val lastWeek = counted.filter { it.date.isBefore(today.minusDays(7)) && !it.date.isBefore(today.minusDays(14)) }
        if (thisWeek.size < MIN_DAYS_PER_WEEK || lastWeek.size < MIN_DAYS_PER_WEEK) return null
        val label = (thisWeek + lastWeek).maxBy { it.date }.terms!!.label
        return WeekChange(
            packageName, label,
            averageMinutes = average(thisWeek), previousAverageMinutes = average(lastWeek),
            daysKept = kept(thisWeek), daysCounted = thisWeek.size,
            previousDaysKept = kept(lastWeek), previousDaysCounted = lastWeek.size,
        )
    }

    /** Biggest improvement first, then the rest by name. */
    fun sorted(changes: List<WeekChange>): List<WeekChange> =
        changes.sortedWith(compareByDescending<WeekChange> { it.minutesLessPerDay }.thenBy { it.label })

    /** Minutes less per day across all [changes] together; negative means more. */
    fun totalLessPerDay(changes: List<WeekChange>): Int = changes.sumOf { it.minutesLessPerDay }

    /** "18 min less a day than the week before". Describes only what was measured. */
    fun minutesText(change: WeekChange): String = when (change.direction) {
        WeekChange.Direction.LESS -> "${change.minutesLessPerDay} min less a day than the week before"
        WeekChange.Direction.MORE -> "${-change.minutesLessPerDay} min more a day than the week before"
        WeekChange.Direction.SAME -> "About the same as the week before"
    }

    /** Short form for a pill: "18 min less", "12 min more", "Same". */
    fun pillText(change: WeekChange): String = when (change.direction) {
        WeekChange.Direction.LESS -> "${change.minutesLessPerDay} min less"
        WeekChange.Direction.MORE -> "${-change.minutesLessPerDay} min more"
        WeekChange.Direction.SAME -> "Same"
    }

    /** "Limit kept on 5 of 7 days, 2 of 6 the week before". */
    fun keptText(change: WeekChange): String =
        "Limit kept on ${change.daysKept} of ${days(change.daysCounted)}, ${change.previousDaysKept} of ${change.previousDaysCounted} the week before"

    /** The headline across apps, or null when nothing can be compared yet. */
    fun headline(changes: List<WeekChange>): String? {
        if (changes.isEmpty()) return null
        val total = totalLessPerDay(changes)
        return when {
            total >= SAME_WITHIN_MINUTES -> "$total min less a day in your limited apps"
            total <= -SAME_WITHIN_MINUTES -> "${-total} min more a day in your limited apps"
            else -> "About the same time in your limited apps"
        }
    }

    private fun average(days: List<DayRecord>) = (days.sumOf { it.minutes!! }.toDouble() / days.size).roundToInt()

    private fun kept(days: List<DayRecord>) = days.count { it.minutes!! <= it.terms!!.limitMinutes!! }

    private fun days(n: Int) = if (n == 1) "1 day" else "$n days"
}
