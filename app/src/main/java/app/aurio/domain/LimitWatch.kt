package app.aurio.domain

import java.time.LocalDate

/** The two limit notifications an app can get in a day, each at most once. */
enum class LimitAlert { FEW_LEFT, REACHED }

/** One limited app as the watcher sees it: today's terms and the minutes used so far. */
data class WatchedApp(val packageName: String, val label: String, val limitMinutes: Int, val usedMinutes: Int) {
    val minutesLeft get() = limitMinutes - usedMinutes
}

/**
 * When to warn about limits, and when to look again. Android tells ordinary apps nothing when
 * a limit is crossed, so Aurio wakes itself. Usage grows by at most one minute per minute, so
 * nothing can cross a threshold sooner than its distance in minutes: that is the next check.
 */
object LimitWatch {
    /** "5 min left" goes out at or under this many minutes. */
    const val WARN_AT_MINUTES_LEFT = 5

    /** The alerts to post now that have not gone out today. A reached limit skips "few left". */
    fun alertsDue(apps: List<WatchedApp>, sent: Set<Pair<String, LimitAlert>>): List<Pair<WatchedApp, LimitAlert>> =
        apps.mapNotNull { app ->
            val left = app.minutesLeft
            when {
                left <= 0 -> if (app.packageName to LimitAlert.REACHED in sent) null else app to LimitAlert.REACHED
                left <= WARN_AT_MINUTES_LEFT && warnsEarly(app) && app.packageName to LimitAlert.FEW_LEFT !in sent ->
                    app to LimitAlert.FEW_LEFT
                else -> null
            }
        }

    /**
     * Minutes until the soonest moment any app could cross a threshold it has not been warned
     * about, at least 1. Null when every app has been told everything for today.
     */
    fun minutesUntilNextCheck(apps: List<WatchedApp>, sent: Set<Pair<String, LimitAlert>>): Int? =
        apps.mapNotNull { app ->
            when {
                app.packageName to LimitAlert.REACHED in sent -> null
                warnsEarly(app) && app.packageName to LimitAlert.FEW_LEFT !in sent && app.minutesLeft > WARN_AT_MINUTES_LEFT ->
                    app.minutesLeft - WARN_AT_MINUTES_LEFT
                else -> app.minutesLeft
            }
        }.minOrNull()?.coerceAtLeast(1)

    /** A 5-minute limit starts at "5 min left", so it only gets the "reached" alert. */
    private fun warnsEarly(app: WatchedApp) = app.limitMinutes > WARN_AT_MINUTES_LEFT

    /** True when [minuteOfDay] falls in quiet hours [startMinute, endMinute), which may cross midnight. */
    fun inQuietHours(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean = when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    // Alerts already sent are kept as one line, "2026-10-28|pkg:REACHED,pkg2:FEW_LEFT".
    // A line from another day reads as empty, so yesterday's alerts never block today's.

    fun encodeSent(date: LocalDate, sent: Set<Pair<String, LimitAlert>>): String =
        "$date|" + sent.joinToString(",") { (pkg, alert) -> "$pkg:${alert.name}" }

    fun decodeSent(stored: String?, today: LocalDate): Set<Pair<String, LimitAlert>> {
        val (date, items) = stored?.split("|", limit = 2)?.takeIf { it.size == 2 } ?: return emptySet()
        if (date != today.toString()) return emptySet()
        return items.split(",").mapNotNull { item ->
            val pkg = item.substringBeforeLast(":", "")
            val alert = LimitAlert.entries.firstOrNull { it.name == item.substringAfterLast(":") }
            if (pkg.isEmpty() || alert == null) null else pkg to alert
        }.toSet()
    }
}
