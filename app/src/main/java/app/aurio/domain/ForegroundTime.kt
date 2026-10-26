package app.aurio.domain

/**
 * One app lifecycle event, reduced to time, package and an opaque screen id (a hash of the
 * activity name, never the name itself). Never content.
 */
data class ForegroundEvent(val timeMillis: Long, val packageName: String, val screen: Int, val kind: Kind) {
    enum class Kind { RESUMED, PAUSED, SCREEN_OFF, DEVICE_STARTUP }
}

/**
 * Foreground time from lifecycle events, as Android's own screen-time page counts it: from a
 * screen resuming to it pausing. Pure: no Android, no clock. Android's daily totals can
 * over-count around midnight; events can be cut exactly at the edges of a day.
 */
object ForegroundTime {

    /**
     * Foreground milliseconds per package inside [start, end), from [events] in time order that
     * were read from [readFrom] (at or before [start], so a stretch that began yesterday is seen).
     *
     * - An app is in the foreground while any of its screens is resumed. Screens are counted, not
     *   kept in a set, because Android pauses one copy of a screen before resuming the next copy
     *   of the same screen; "stopped" events are not passed in at all.
     * - If the first event seen for an app is a pause, the app was already open when the read
     *   began, so it counts from [readFrom].
     * - Screen off closes every app. A device start closes every app at the last event before it,
     *   because a crash or power loss writes no shutdown event and the downtime is not usage.
     * - A stretch still open at the end runs until [end]. Only the part inside the window counts.
     */
    fun perPackage(events: List<ForegroundEvent>, readFrom: Long, start: Long, end: Long): Map<String, Long> {
        val since = HashMap<String, Long>()
        val resumed = HashMap<String, HashMap<Int, Int>>()
        val seen = HashSet<String>()
        val total = HashMap<String, Long>()
        var lastEventAt = readFrom

        fun add(pkg: String, from: Long, to: Long) {
            val counted = minOf(to, end) - maxOf(from, start)
            if (counted > 0) total.merge(pkg, counted, Long::plus)
        }

        fun close(pkg: String, at: Long) {
            val from = since.remove(pkg) ?: return
            resumed.remove(pkg)
            add(pkg, from, at)
        }

        for (e in events) {
            if (e.timeMillis >= end) break
            val pkg = e.packageName
            when (e.kind) {
                ForegroundEvent.Kind.RESUMED -> {
                    seen += pkg
                    since.getOrPut(pkg) { e.timeMillis }
                    resumed.getOrPut(pkg) { HashMap() }.merge(e.screen, 1, Int::plus)
                }
                ForegroundEvent.Kind.PAUSED -> {
                    val screens = resumed[pkg]
                    when {
                        screens != null -> {
                            screens.computeIfPresent(e.screen) { _, n -> if (n > 1) n - 1 else null }
                            if (screens.isEmpty()) close(pkg, e.timeMillis)
                        }
                        pkg !in seen -> add(pkg, readFrom, e.timeMillis) // open before the read began
                    }
                    seen += pkg
                }
                ForegroundEvent.Kind.SCREEN_OFF -> since.keys.toList().forEach { close(it, e.timeMillis) }
                ForegroundEvent.Kind.DEVICE_STARTUP -> since.keys.toList().forEach { close(it, lastEventAt) }
            }
            lastEventAt = e.timeMillis
        }
        since.keys.toList().forEach { close(it, end) }
        return total
    }
}
