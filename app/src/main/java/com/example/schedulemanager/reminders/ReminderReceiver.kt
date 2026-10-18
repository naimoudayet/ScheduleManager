package com.example.schedulemanager.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.schedulemanager.ScheduleApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fired by the reminder alarm: shows the notification, then sets the alarm for the next session. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ScheduleApp
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        val pending = goAsync()
        app.appScope.launch {
            try {
                // Load the session now rather than trusting the alarm, in case it was edited or deleted
                val session = app.reminderScheduler.remindedSessions().firstOrNull { it.id == sessionId }
                if (session != null && app.settingsRepository.remindersEnabled.first()) {
                    // In parent mode, say whose session it is ("Adam · Physics starts in 5 minutes")
                    val person = app.scheduleRepository.profiles.first().firstOrNull { it.id == session.profileId }
                    val prefix = if (app.settingsRepository.parentMode.first() && person != null && !person.isMe) person.name else null
                    ReminderNotifications.show(context, session, app.settingsRepository.use24Hour.first(), prefix)
                }
                app.reminderScheduler.reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }
}

/**
 * Alarms are lost on reboot, and a clock / time-zone change or a newly granted exact-alarm
 * permission makes the current one wrong, so set it again on each of these.
 */
class RescheduleRemindersReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val app = context.applicationContext as ScheduleApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.reminderScheduler.reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
