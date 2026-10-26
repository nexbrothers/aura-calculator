package app.aurio.data

import android.app.AppOpsManager
import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import app.aurio.domain.ForegroundEvent
import app.aurio.domain.ForegroundTime

/**
 * Reads only how long each app was in the foreground today. Never content.
 * Everything stays on the device; the app has no INTERNET permission.
 */
object UsageAccess {

    fun isGranted(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Foreground minutes per package on [date] so far. The window is taken from [date], not from
     * the clock, so a read that straddles midnight cannot file tomorrow's minutes under today.
     */
    fun minutesSoFar(context: Context, date: java.time.LocalDate): Map<String, Int> {
        val zone = java.time.ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(System.currentTimeMillis(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        return foregroundMinutes(context, start, end)
    }

    /** Foreground minutes per package for a finished local date, or null when access is off. */
    fun minutesForDate(context: Context, date: java.time.LocalDate): Map<String, Int>? {
        if (!isGranted(context)) return null
        val zone = java.time.ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return foregroundMinutes(context, start, end)
    }

    /** How far before a day to read, so an app already open at midnight is seen opening. */
    private const val LOOKBACK_MILLIS = 6 * 3_600_000L

    /**
     * Sums foreground time from lifecycle events cut exactly at [start] and [end]. Android's daily
     * totals (queryAndAggregateUsageStats) use buckets that need not end at local midnight, so
     * they can add part of the next day. Only event type, time and package are kept; the activity
     * name is reduced to a hash at once, so which part of an app was used is never held.
     */
    private fun foregroundMinutes(context: Context, start: Long, end: Long): Map<String, Int> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val readFrom = start - LOOKBACK_MILLIS
        val events = usm.queryEvents(readFrom, end)
        val list = ArrayList<ForegroundEvent>()
        val e = UsageEvents.Event()
        // Already in time order, as ForegroundTime expects.
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val kind = kindOf(e.eventType) ?: continue
            list += ForegroundEvent(e.timeStamp, e.packageName, e.className.hashCode(), kind)
        }
        return ForegroundTime.perPackage(list, readFrom, start, end).mapValues { (_, ms) -> (ms / 60_000L).toInt() }
    }

    /**
     * Newer event types simply never appear on older Android versions. ACTIVITY_STOPPED is left
     * out on purpose: it arrives after the next screen has resumed and would end the stretch early.
     */
    @SuppressLint("InlinedApi")
    private fun kindOf(type: Int): ForegroundEvent.Kind? = when (type) {
        UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundEvent.Kind.RESUMED
        UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundEvent.Kind.PAUSED
        UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> ForegroundEvent.Kind.SCREEN_OFF
        UsageEvents.Event.DEVICE_STARTUP -> ForegroundEvent.Kind.DEVICE_STARTUP
        else -> null
    }
}
