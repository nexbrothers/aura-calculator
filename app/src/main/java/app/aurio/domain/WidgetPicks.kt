package app.aurio.domain

/** Which limited apps the home-screen widgets show. Pure: no Android, no clock. */
object WidgetPicks {

    /**
     * Today's limited apps in the order that matters most at a glance: the furthest over first,
     * then the least time left. Apps with Unknown usage come last, in name order, so the widget
     * never ranks a guess.
     */
    fun limitsByUrgency(apps: List<AppUsage>): List<AppUsage> {
        val limited = apps.filter { it.category == AppCategory.LIMITED }
        val known = limited.filter { it.usedMinutes != null }.sortedBy { it.limitMinutes - (it.usedMinutes ?: 0) }
        val unknown = limited.filter { it.usedMinutes == null }.sortedBy { it.label.lowercase() }
        return known + unknown
    }

    /** The one limit the 4x1 widget shows: the most urgent, or null with no limited apps. */
    fun closestLimit(apps: List<AppUsage>): AppUsage? = limitsByUrgency(apps).firstOrNull()
}
