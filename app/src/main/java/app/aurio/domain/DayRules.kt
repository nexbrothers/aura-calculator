package app.aurio.domain

/** The rules one app is scored by on one day. [limitMinutes] is null unless LIMITED. */
data class AppTerms(val label: String, val category: AppCategory, val limitMinutes: Int?)

/** Which terms apply to which day. Pure: no Android, no clock, no database. */
object DayRules {

    /** Android keeps daily usage buckets for about 10 days, then merges them into weekly ones. */
    const val MAX_READABLE_AGE_DAYS = 7L

    /**
     * Whether a finished day can be read in full from Android at settlement. A day on which Usage
     * Access was seen off at any point ([accessWasOff]) is never read back: it stays Unknown, as
     * Home showed it, and the period the user withheld stays unread. Older days are not read
     * either, because Android no longer has them day by day.
     */
    fun canReadFullDay(ageDays: Long, accessWasOff: Boolean): Boolean =
        !accessWasOff && ageDays in 1..MAX_READABLE_AGE_DAYS

    /**
     * Minutes to settle for one app on a finished day, or null for Unknown (no bonus, no penalty).
     * [provided] is Android's record of the whole day, or null when it could not be read; a
     * reading saved during the day covers only part of it, so it is never enough on its own.
     * Minutes only grow within a day, so the result is never below what was already [stored]:
     * an app missing from Android's record (uninstalled, pruned) cannot drop to 0 and earn +20.
     */
    fun settledMinutes(provided: Map<String, Int>?, pkg: String, stored: Int?): Int? =
        if (provided == null) null else maxOf(provided[pkg] ?: 0, stored ?: 0)

    /**
     * Today's terms after the user edits an app ([edited] is null when it was removed).
     * A lower limit applies at once. Anything that could soften today's result (a higher limit,
     * a category change, removing the app) waits until tomorrow, so an edit never dodges a penalty.
     */
    fun termsAfterEdit(today: AppTerms, edited: AppTerms?): AppTerms {
        if (edited == null) return today
        val todayLimit = today.limitMinutes ?: return today
        val newLimit = edited.limitMinutes ?: return today
        val stricter = today.category == AppCategory.LIMITED && edited.category == AppCategory.LIMITED && newLimit < todayLimit
        return if (stricter) today.copy(limitMinutes = newLimit) else today
    }

    /**
     * The apps scored on one day. Terms recorded on that day win. An app with no record that day
     * (the phone was off, or the row predates recorded terms) falls back to [current], which the
     * caller limits to apps tracked before the day ended. [minutes] returns null for Unknown.
     */
    fun appsForDay(
        recorded: Map<String, AppTerms>,
        current: Map<String, AppTerms>,
        minutes: (String) -> Int?,
    ): List<AppUsage> =
        (current + recorded).map { (pkg, terms) ->
            AppUsage(pkg, terms.label, terms.category, minutes(pkg), terms.limitMinutes ?: 0)
        }.sortedBy { it.label.lowercase() }
}
