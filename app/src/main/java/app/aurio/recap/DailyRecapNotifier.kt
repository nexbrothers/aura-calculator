package app.aurio.recap

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.aurio.AurioApplication
import app.aurio.MainActivity
import app.aurio.R
import app.aurio.domain.DailyRecap
import app.aurio.domain.DebugTime
import app.aurio.domain.SystemAurioClock
import app.aurio.work.SettleWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import app.aurio.data.BackgroundWork

/**
 * The morning recap of yesterday's settled aura. Off by default; sent once a day when quiet hours
 * end. Built on the phone from the saved score, like everything else.
 */
object RecapAlarm {
    const val ACTION_RECAP = "app.aurio.action.DAILY_RECAP"

    /** Books the next recap, or cancels it when the recap is off. Safe to call any time. */
    suspend fun schedule(app: AurioApplication) {
        val settings = app.settings.settings.first()
        if (!settings.onboardingDone || !settings.dailyRecap) {
            cancel(app)
            return
        }
        val minute = DailyRecap.minuteOfDay(settings.quietStartMinute, settings.quietEndMinute)
        val at = System.currentTimeMillis() + DailyRecap.millisUntilNext(System.currentTimeMillis(), SystemAurioClock.zone(), minute)
        // Inexact on purpose: a few minutes late is fine, and no special permission is needed.
        app.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(app))
    }

    fun scheduleAsync(app: AurioApplication) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                schedule(app)
            } catch (e: Exception) {
                Log.w("RecapAlarm", "Could not book the recap", e)
            }
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 3,
        Intent(context, RecapReceiver::class.java).setAction(ACTION_RECAP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

object RecapSender {
    /**
     * Posts the recap for yesterday if it has not gone out yet. Settles first, in case the
     * midnight run was deferred. [force] (Developer tools) sends it again.
     */
    suspend fun send(app: AurioApplication, force: Boolean = false) = BackgroundWork.lock.withLock {
        val settings = app.settings.settings.first()
        if (!force && (!settings.onboardingDone || !settings.dailyRecap)) return@withLock
        if (!DebugTime.enabled) SettleWorker.settle(app)
        val yesterday = SystemAurioClock.today().minusDays(1)
        if (!force && app.repository.recapSentFor() == yesterday) return@withLock
        val (day, previousTotal) = app.repository.settledDay(yesterday) ?: return@withLock
        RecapNotifications.post(app, DailyRecap.message(day.delta, day.totalAfter, day.partial, previousTotal))
        app.repository.markRecapSent(yesterday)
    }
}

class RecapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RecapAlarm.ACTION_RECAP) return
        val app = context.applicationContext as AurioApplication
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RecapSender.send(app)
            } catch (e: Exception) {
                Log.w("RecapReceiver", "Recap failed", e)
            } finally {
                try { RecapAlarm.schedule(app) } finally { done.finish() }
            }
        }
    }
}

object RecapNotifications {
    private const val CHANNEL = "daily_recap"
    private const val ID = 3

    /** Low importance: the recap never makes a sound or pops up. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Daily recap", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Yesterday's settled aura, each morning" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    fun post(context: Context, message: DailyRecap.Message) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_INSIGHTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // On a locked phone show only that a recap is ready, never the numbers.
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aurio)
            .setContentTitle("Your daily recap")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aurio)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        try {
            manager.notify(ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: nothing to show.
        }
    }
}
