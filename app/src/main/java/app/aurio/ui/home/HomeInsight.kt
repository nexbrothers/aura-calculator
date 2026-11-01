package app.aurio.ui.home

import app.aurio.domain.AppCategory
import app.aurio.domain.AppUsage
import app.aurio.domain.AuraScoring
import app.aurio.domain.Progress
import app.aurio.domain.WeekChange

/** Limits this close to the end are the most useful thing to say on Home. */
internal const val INSIGHT_CLOSE_MINUTES = 10

/**
 * The one insight Home shows, most useful first: a limit about to run out, a week of progress,
 * a limit already passed, the focus aura still open today, then limits on track. Describes only
 * what was measured. Null when there is nothing worth saying.
 *
 * [apps] are today's apps (minutes null when not counted); [focusLeft] the focus aura still
 * open today; [focusMinutes] the default session length.
 */
internal fun homeInsight(apps: List<AppUsage>, weekChanges: List<WeekChange>, focusLeft: Int, focusMinutes: Int): String? {
    val limited = apps.filter { it.category == AppCategory.LIMITED && it.usedMinutes != null && it.limitMinutes > 0 }
    val close = limited.filter { it.limitMinutes - it.usedMinutes!! in 1..INSIGHT_CLOSE_MINUTES }.minByOrNull { it.limitMinutes - it.usedMinutes!! }
    if (close != null) {
        val left = close.limitMinutes - close.usedMinutes!!
        return "${close.label} has $left min left today. Staying under adds +${AuraScoring.UNDER_LIMIT_BONUS} at midnight."
    }
    val total = Progress.totalLessPerDay(weekChanges)
    if (weekChanges.isNotEmpty() && total >= Progress.SAME_WITHIN_MINUTES) {
        return "$total min less a day in your limited apps than the week before."
    }
    val over = limited.filter { it.usedMinutes!! > it.limitMinutes }.maxByOrNull { it.usedMinutes!! - it.limitMinutes }
    if (over != null) {
        return "${over.label} is ${over.usedMinutes!! - over.limitMinutes} min over its limit today. The loss stops at −${AuraScoring.MAX_PENALTY_PER_APP}."
    }
    if (focusLeft > 0) {
        return "A $focusMinutes min focus session adds +${minOf(focusMinutes * AuraScoring.FOCUS_PER_MINUTE, focusLeft)} aura."
    }
    if (limited.isNotEmpty()) {
        val n = limited.size
        return if (n == 1) "Your limit is on track for +${AuraScoring.UNDER_LIMIT_BONUS} at midnight."
        else "All $n limits are on track for +${n * AuraScoring.UNDER_LIMIT_BONUS} at midnight."
    }
    return null
}
