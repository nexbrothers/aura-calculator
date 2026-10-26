package app.aurio.domain

/** The rules one app is scored by on one day. [limitMinutes] is null unless LIMITED. */
data class AppTerms(val label: String, val category: AppCategory, val limitMinutes: Int?)

/** Which terms apply to which day. Pure: no Android, no clock, no database. */
object DayRules {

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
