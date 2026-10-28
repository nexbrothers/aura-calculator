package app.aurio.limits

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.aurio.AurioApplication
import app.aurio.MainActivity
import app.aurio.R
import app.aurio.data.UsageAccess
import app.aurio.domain.AuraScoring
import app.aurio.domain.DebugTime
import app.aurio.domain.LimitAlert
import app.aurio.domain.LimitWatch
import app.aurio.domain.SystemAurioClock
import app.aurio.domain.WatchedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.aurio.data.BackgroundWork
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * "5 min left" and "limit reached" for limited apps, worked out on the phone from usage stats.
 * Each check posts what is due and books the next one (LimitWatch says when). Scoring never
 * depends on any of this: a late or missing warning changes no aura.
 */
object LimitCheck {
    /**
     * Posts any due warnings and books the next check, or cancels it when there is nothing to
     * watch. [minutesToday] is today's usage when the caller has just read it; null reads it here.
     */
    suspend fun run(app: AurioApplication, minutesToday: Map<String, Int>? = null) = BackgroundWork.lock.withLock {
        val settings = app.settings.settings.first()
        val granted = UsageAccess.isGranted(app) || DebugTime.enabled
        AccessNotice.update(app, granted)
        if (!settings.onboardingDone || !settings.limitWarnings || !granted || !LimitNotifications.canPost(app)) {
            LimitAlarm.cancel(app)
            return@withLock
        }
        val repo = app.repository
        val day = SystemAurioClock.today()
        repo.recordTerms(day)
        val minutes = minutesToday
            ?: if (DebugTime.enabled) DebugTime.usageByDate[day] ?: emptyMap()
            else UsageAccess.minutesSoFar(app, day, notBefore = repo.measureFrom(day))
        val apps = repo.watchedApps(day, minutes)
        val sent = repo.limitAlertsSent(day)
        val due = LimitWatch.alertsDue(apps, sent)
        if (due.isNotEmpty()) {
            val now = Instant.ofEpochMilli(SystemAurioClock.nowMillis()).atZone(SystemAurioClock.zone())
            val quiet = LimitWatch.inQuietHours(now.hour * 60 + now.minute, settings.quietStartMinute, settings.quietEndMinute)
            due.forEach { (watched, alert) -> LimitNotifications.post(app, watched, alert, silent = quiet) }
            repo.saveLimitAlertsSent(day, sent + due.map { (watched, alert) -> watched.packageName to alert })
        }
        val next = LimitWatch.minutesUntilNextCheck(apps, sent + due.map { (watched, alert) -> watched.packageName to alert })
        if (next == null) LimitAlarm.cancel(app) else LimitAlarm.schedule(app, SystemAurioClock.nowMillis() + next * 60_000L)
    }

    /** Runs a check off the main thread; failures only skip this check. */
    fun runAsync(app: AurioApplication, onDone: () -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                run(app)
            } catch (e: Exception) {
                Log.w("LimitCheck", "Limit check failed", e)
            } finally {
                onDone()
            }
        }
    }
}

/**
 * The wake-up for the next limit check. Exact when the user allows exact alarms; otherwise
 * Android may deliver it some minutes late, and the warning then says the minutes left at
 * that moment. No special permission is needed either way.
 */
object LimitAlarm {
    const val ACTION_CHECK = "app.aurio.action.LIMIT_CHECK"

    fun schedule(context: Context, atMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } catch (_: SecurityException) {
            // Exact alarms were turned off between the check and the call.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 2,
        Intent(context, LimitCheckReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Runs a check when its alarm fires, and after a restart (which clears alarms). */
class LimitCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LimitAlarm.ACTION_CHECK && intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val done = goAsync()
        LimitCheck.runAsync(context.applicationContext as AurioApplication) { done.finish() }
    }
}

object LimitNotifications {
    private const val CHANNEL = "limit_warnings"
    private const val ID = 2

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Limit warnings", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "When a limited app has 5 minutes left today, and when its limit is reached" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** False when notifications, or just this channel, are turned off in Android settings. */
    fun canPost(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        return manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Removes any limit warning still showing (Delete all data). */
    fun clear(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.id == ID }.forEach { manager.cancel(it.tag, ID) }
    }

    /** One notification per app (tagged by package), so "reached" replaces that app's "5 min left". */
    fun post(context: Context, app: WatchedApp, alert: LimitAlert, silent: Boolean) {
        val open = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_LIMITS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text, publicTitle) = when (alert) {
            LimitAlert.FEW_LEFT -> Triple(
                "${app.label}: ${app.minutesLeft} min left",
                "Today's limit is ${app.limitMinutes} min. Staying under adds +${AuraScoring.UNDER_LIMIT_BONUS} at midnight.",
                "A limit is close",
            )
            LimitAlert.REACHED -> Triple(
                "${app.label}: limit reached",
                "${app.usedMinutes} of ${app.limitMinutes} min used. Each minute over is −${AuraScoring.PENALTY_PER_MINUTE} aura, " +
                    "up to −${AuraScoring.MAX_PENALTY_PER_APP} today.",
                "A limit was reached",
            )
        }
        // On a locked phone show only that a limit is close, never which app or how long.
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aurio)
            .setContentTitle(publicTitle)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aurio)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(silent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(app.packageName, ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: nothing to show.
        }
    }
}
