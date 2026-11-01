package app.aurio.limits

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.aurio.MainActivity
import app.aurio.R
import app.aurio.domain.Rescue
import app.aurio.domain.RescueStyle
import app.aurio.domain.WatchedApp
import java.util.Calendar

/** Smart Rescue's nudge (wording and timing in domain/Rescue.kt). Its own channel, so it can be turned off alone. */
object RescueNotifications {
    private const val CHANNEL = "aura_rescue"
    private const val ID = 5

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Aura rescue", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "One reminder shortly before the time you usually go over a limit" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** False when notifications, or just this channel, are turned off in Android settings. */
    fun canPost(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        return manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Removes any nudge still showing (Delete all data). */
    fun clear(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.id == ID }.forEach { manager.cancel(it.tag, ID) }
    }

    /** The minute of the day as the phone's clock shows times (12 or 24 hour). */
    fun timeText(context: Context, minuteOfDay: Int): String {
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60); set(Calendar.MINUTE, minuteOfDay % 60)
        }
        return DateFormat.getTimeFormat(context).format(at.time)
    }

    fun post(context: Context, app: WatchedApp, style: RescueStyle, usualMinute: Int) {
        val open = PendingIntent.getActivity(
            context, 5,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_LIMITS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = Rescue.text(style, timeText(context, usualMinute))
        // On a locked phone show only a generic line, never which app or how long.
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aurio)
            .setContentTitle("A gentle reminder")
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aurio)
            .setContentTitle(Rescue.title(app))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
        if (style == RescueStyle.FOCUS_OFFER) {
            // Opens Focus with the short session chosen; the session starts only when the user taps Start.
            val focus = PendingIntent.getActivity(
                context, 6,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_FOCUS_SHORT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Focus for ${Rescue.FOCUS_OFFER_MINUTES} min", focus)
        }
        try {
            NotificationManagerCompat.from(context).notify(app.packageName, ID, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: nothing to show.
        }
    }
}
