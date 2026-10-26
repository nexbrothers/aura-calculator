package app.aurio.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** What was saved for one app on one day: minutes (null = Unknown) and the terms it was scored by. */
data class DayRecord(val date: LocalDate, val minutes: Int?, val terms: AppTerms?)

/** One bar of an app's 7-day chart. */
data class DayBar(val date: LocalDate, val minutes: Int?, val limitMinutes: Int?, val state: State) {
    enum class State {
        /** Not tracked that day, or no record (phone off, before install). */
        NO_DATA,
        /** Usage Access was off: never a win or a loss. */
        UNKNOWN,
        /** Neutral or essential that day: time shown, never scored. */
        NOT_SCORED,
        UNDER,
        OVER,
    }
}

/** An app's settled aura over a period, from its frozen ledger lines. */
data class AppImpact(val total: Int, val daysUnder: Int, val daysOver: Int)

/** Pure history views for the app detail and insights screens. */
object History {

    /**
     * The last 7 days for one app, oldest first, ending [today]. Today comes from the live view
     * ([todayUsage], already Unknown when access is off); earlier days from [records]. A record
     * without terms (saved before terms were recorded) is judged by the [current] settings.
     */
    fun week(today: LocalDate, records: List<DayRecord>, todayUsage: AppUsage?, current: AppTerms?): List<DayBar> {
        val byDate = records.associateBy { it.date }
        return (6L downTo 0L).map { back ->
            val date = today.minusDays(back)
            if (date == today && todayUsage != null) {
                bar(date, todayUsage.usedMinutes, AppTerms(todayUsage.label, todayUsage.category, todayUsage.limitMinutes))
            } else {
                val r = byDate[date] ?: return@map DayBar(date, null, null, DayBar.State.NO_DATA)
                val terms = r.terms ?: current ?: return@map DayBar(date, r.minutes, null, DayBar.State.NO_DATA)
                bar(date, r.minutes, terms)
            }
        }
    }

    private fun bar(date: LocalDate, minutes: Int?, terms: AppTerms): DayBar {
        val limit = terms.limitMinutes.takeIf { terms.category == AppCategory.LIMITED }
        val state = when {
            minutes == null -> DayBar.State.UNKNOWN
            terms.category != AppCategory.LIMITED || limit == null -> DayBar.State.NOT_SCORED
            minutes > limit -> DayBar.State.OVER
            else -> DayBar.State.UNDER
        }
        return DayBar(date, minutes, limit, state)
    }

    /** Sums one app's settled ledger lines ([points], one per settled day it was scored). */
    fun impact(points: List<Int>): AppImpact =
        AppImpact(points.sum(), daysUnder = points.count { it > 0 }, daysOver = points.count { it < 0 })

    /**
     * A month laid out as calendar weeks starting on [firstDayOfWeek]: each week has 7 slots, and
     * slots before the 1st or after the last day are null.
     */
    fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val slots = List<LocalDate?>(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        return slots.chunked(7).map { week -> week + List(7 - week.size) { null } }
    }
}
