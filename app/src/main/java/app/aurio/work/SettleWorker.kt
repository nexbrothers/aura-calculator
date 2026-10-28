package app.aurio.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.aurio.AurioApplication
import app.aurio.data.UsageAccess
import app.aurio.domain.SettleSchedule
import app.aurio.domain.SystemAurioClock
import app.aurio.limits.AccessNotice
import app.aurio.limits.LimitCheck
import app.aurio.recap.RecapAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Freezes finished days even when the app was not opened. The app also catches up on launch. */
class SettleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AurioApplication
        return try {
            val granted = UsageAccess.isGranted(applicationContext)
            AccessNotice.update(app, granted)
            settle(app)
            Result.success()
        } catch (e: Exception) {
            // Settlement is idempotent: a later run (or the next app open) picks up where this stopped.
            Log.w("SettleWorker", "Settlement failed; will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val NOW = "settle-now"
        private const val BACKUP = "settle-days"

        /** Freezes every finished day not settled yet. Idempotent; the daily recap calls it too. */
        suspend fun settle(app: AurioApplication) {
            if (!UsageAccess.isGranted(app)) app.repository.markAccessOff(SystemAurioClock.today())
            val startDay = app.repository.startDate()
            val installedAt = app.repository.installedAt()
            app.repository.settlePastDays { date ->
                UsageAccess.minutesForDate(app, date, notBefore = if (date == startDay) installedAt else null)
            }
        }

        /**
         * Books settlement for just after the next local midnight, and a 6-hour backup in case the
         * system defers the midnight run. Settlement is idempotent, so extra runs are safe.
         */
        fun schedule(context: Context) {
            MidnightAlarm.schedule(context)
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                BACKUP, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SettleWorker>(6, TimeUnit.HOURS).build(),
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SettleWorker>().build())
        }
    }
}

/**
 * A nightly alarm just after local midnight that starts settlement and books the next night.
 * Each booking is computed afresh from the clock and zone, so the time never drifts; it is also
 * re-booked on app start, after a restart, and when the clock or time zone changes.
 * Inexact on purpose: a few minutes late is fine, and no special permission is needed.
 */
object MidnightAlarm {
    const val ACTION_SETTLE = "app.aurio.action.SETTLE"

    fun schedule(context: Context) {
        val at = SystemAurioClock.nowMillis() + SettleSchedule.millisUntilNextRun(SystemAurioClock.nowMillis(), SystemAurioClock.zone())
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, MidnightReceiver::class.java).setAction(ACTION_SETTLE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MidnightAlarm.ACTION_SETTLE -> SettleWorker.runNow(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }
        MidnightAlarm.schedule(context)
        val app = context.applicationContext as AurioApplication
        // A new day (or a moved clock) resets which limit warnings are due, and moves the recap time.
        // Limit checks after a restart are booked by their own receiver.
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RecapAlarm.schedule(app)
                if (intent.action != Intent.ACTION_BOOT_COMPLETED) LimitCheck.run(app)
            } catch (e: Exception) {
                Log.w("MidnightReceiver", "Rescheduling failed", e)
            } finally {
                done.finish()
            }
        }
    }
}
