package app.aurio.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.aurio.AurioApplication
import app.aurio.data.AurioStore
import app.aurio.domain.SystemAurioClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Wakes the app when a focus session's saved end time arrives, so the session is completed and
 * the user is told even if Aurio was closed. The countdown never depends on this: the end time is
 * stored, and an overdue session is also completed whenever the app opens.
 */
object FocusAlarm {
    const val ACTION_END = "app.aurio.action.FOCUS_END"

    fun schedule(context: Context, endsAtMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        // Exact when the user allows it (Android 12+ asks for it); otherwise Android may deliver
        // a few minutes late in deep idle, which only delays the notification, never the credit.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pending)
        } catch (_: SecurityException) {
            // Exact alarms were turned off between the check and the call.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pending)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, FocusEndReceiver::class.java).setAction(ACTION_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Handles the end-time alarm, and a phone restart (which clears alarms): an overdue session is
 * completed and announced; one still running gets its alarm back.
 */
class FocusEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != FocusAlarm.ACTION_END) return
        val app = context.applicationContext as AurioApplication
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val running = app.repository.runningFocus() ?: return@launch
                if (running.endsAt > SystemAurioClock.nowMillis()) {
                    FocusAlarm.schedule(app, running.endsAt)
                    return@launch
                }
                val result = app.repository.completeFocus(running)
                // Only the one who completed it announces it, and only when Aurio is not on screen.
                if (result.justCompleted && !AurioStore.visible && app.settings.settings.first().focusReminders) {
                    FocusNotifications.sessionComplete(app, result.minutes, result.earned)
                }
            } finally {
                done.finish()
            }
        }
    }
}
