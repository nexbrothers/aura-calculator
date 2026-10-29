package app.aurio.limits

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.aurio.AurioApplication
import app.aurio.R
import app.aurio.data.AurioStore
import app.aurio.data.UsageAccess
import kotlinx.coroutines.flow.first

/**
 * One notice each time usage access is turned off while limits are set: without it, limits stay
 * uncounted and Aurio is otherwise silent about it. Not shown while Aurio is open (Home says so).
 */
object AccessNotice {
    private const val CHANNEL = "app_status"
    private const val ID = 4

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "App status", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "When Waura can no longer count your limits" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    /** Call with the current access state: posts the notice once per off spell, and clears it when access is back. */
    suspend fun update(app: AurioApplication, granted: Boolean) {
        val repo = app.repository
        if (granted) {
            if (repo.accessNoticeShown()) {
                repo.setAccessNoticeShown(false)
                clear(app)
            }
            return
        }
        if (AurioStore.visible || repo.accessNoticeShown()) return
        if (!app.settings.settings.first().onboardingDone || !repo.hasLimitedApps()) return
        if (post(app)) repo.setAccessNoticeShown(true)
    }

    private fun post(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val open = PendingIntent.getActivity(context, 4, UsageAccess.settingsIntent(), PendingIntent.FLAG_IMMUTABLE)
        val text = "Your limits are not being counted, so they cannot add or cost aura. Tap to turn usage access back on."
        // Like every Aurio notification, the lock screen shows only a generic line.
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aurio)
            .setContentTitle("Waura needs attention")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aurio)
            .setContentTitle("Usage access is off")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        return try {
            manager.notify(ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
