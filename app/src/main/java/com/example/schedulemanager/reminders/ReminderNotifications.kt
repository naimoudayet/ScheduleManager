package com.example.schedulemanager.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.schedulemanager.R
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.currentDayIndex
import com.example.schedulemanager.model.currentMinutes
import com.example.schedulemanager.model.formatTimeRange

object ReminderNotifications {

    private const val CHANNEL_ID = "session_reminders"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Session reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A heads-up $REMINDER_LEAD_MINUTES minutes before each session starts"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** True when the app may post notifications (permission granted and not blocked in system settings). */
    fun canNotify(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** [personName] prefixes the title in parent mode, e.g. "Adam · Physics starts in 5 minutes". */
    fun show(context: Context, session: ClassSession, use24Hour: Boolean, personName: String? = null) {
        if (!canNotify(context)) return

        val minutes = minutesUntilStart(session, currentDayIndex(), currentMinutes())
        val what = when {
            minutes > 1 -> "${session.subject} starts in $minutes minutes"
            minutes == 1 -> "${session.subject} starts in 1 minute"
            minutes == 0 -> "${session.subject} starts now"
            else -> "${session.subject} has started"
        }
        val title = if (personName != null) "$personName · $what" else what
        val text = "${formatTimeRange(session.startMinutes, session.endMinutes, use24Hour)} · ${session.classroom}"

        // Tint with whichever of the subject's two colors is darker, so it stays visible
        val accent = listOf(session.backgroundColor, session.foregroundColor).minBy { it.luminance() }

        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(accent.toArgb())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(session.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check above and now; nothing to show
        }
    }
}
