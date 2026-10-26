package app.aurio.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.util.Calendar

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

    /** Foreground minutes per package since local midnight. */
    fun todayMinutes(context: Context): Map<String, Int> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
        return stats.mapValues { (_, s) -> (s.totalTimeInForeground / 60_000L).toInt() }
    }

    /** Foreground minutes per package for a finished local date, or null when access is off. */
    fun minutesForDate(context: Context, date: java.time.LocalDate): Map<String, Int>? {
        if (!isGranted(context)) return null
        val zone = java.time.ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return usm.queryAndAggregateUsageStats(start, end).mapValues { (_, s) -> (s.totalTimeInForeground / 60_000L).toInt() }
    }
}
