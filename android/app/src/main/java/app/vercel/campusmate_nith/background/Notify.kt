package app.vercel.campusmate_nith.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.vercel.campusmate_nith.MainActivity
import app.vercel.campusmate_nith.R

/** Notification channels and helpers. */
object Notify {
    const val UPDATES = "updates"
    const val CLASSES = "classes"
    const val STUDY = "study"
    const val STUDY_ALERT = "study_alert"

    /** Screens a notification, shortcut or widget may open. */
    val ROUTES = setOf(
        "home", "timetable", "board", "chat", "more", "assignments", "exams", "calendar", "attendance",
        "syllabus", "explain", "planner", "mess", "ask", "settings", "college-updates", "hostel-updates", "results", "study",
    )

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(UPDATES, "New notices, assignments and exams", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "When something new for your year and section is posted" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CLASSES, "Class reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "A reminder a few minutes before each class" },
        )
        nm.createNotificationChannel(
            NotificationChannel(STUDY, "Study timer", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "The Pomodoro countdown while study mode is on"; setShowBadge(false) },
        )
        // rings through study mode's own Do Not Disturb: alarm sound, and allowed to bypass it once access is granted
        nm.createNotificationChannel(
            NotificationChannel(STUDY_ALERT, "Study breaks", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When a Pomodoro or a break ends"
                setBypassDnd(true)
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            },
        )
    }

    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Opens the app on one screen. */
    fun openIntent(context: Context, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, route.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun post(context: Context, id: Int, channel: String, title: String, text: String, route: String, lines: List<String> = emptyList()) {
        if (!allowed(context)) return
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_campusmate)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent(context, route))
            .setAutoCancel(true)
            .setPriority(if (channel == CLASSES) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        if (lines.isNotEmpty()) {
            b.setStyle(NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach { s.addLine(it) } })
        } else {
            b.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        try {
            NotificationManagerCompat.from(context).notify(id, b.build())
        } catch (_: SecurityException) {
            // permission was taken away between the check and the post
        }
    }
}
