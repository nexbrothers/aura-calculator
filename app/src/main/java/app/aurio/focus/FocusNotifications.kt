package app.aurio.focus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.aurio.MainActivity
import app.aurio.R

/** The one notification Aurio sends today: a focus session has ended. Shown only on this phone. */
object FocusNotifications {
    private const val CHANNEL = "focus_sessions"
    private const val ID_SESSION_COMPLETE = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Focus sessions", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "When a focus session you started has ended" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Removes a "Session complete" still showing (Delete all data). */
    fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(ID_SESSION_COMPLETE)

    /** Posts "Session complete" if the user allows notifications. [earned] is the aura it added. */
    fun sessionComplete(context: Context, minutes: Int, earned: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (earned > 0) "$minutes min done. +$earned aura." else "$minutes min done. Today's focus aura cap is reached."
        // On a locked phone show only that a session ended, never the minutes or aura.
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aurio)
            .setContentTitle("Session complete")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aurio)
            .setContentTitle("Session complete")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        try {
            manager.notify(ID_SESSION_COMPLETE, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: nothing to show.
        }
    }
}
