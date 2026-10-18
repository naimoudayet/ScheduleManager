package com.example.schedulemanager.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.SettingsRepository
import com.example.schedulemanager.model.ClassSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/**
 * Keeps exactly one alarm set: the next session reminder. When it fires, [ReminderReceiver]
 * shows the notification and calls [reschedule] again for the one after it.
 */
class ReminderScheduler(
    private val context: Context,
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    /** Without the "Alarms & reminders" permission (Android 12+), alarms can arrive a few minutes late. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun reschedule() = mutex.withLock {
        if (!settingsRepository.remindersEnabled.first()) {
            alarmManager.cancel(alarmIntent(sessionId = null))
            return@withLock
        }

        val now = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowDay = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
        val nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val next = nextReminder(remindedSessions(), nowDay, nowMinute)
        if (next == null) {
            alarmManager.cancel(alarmIntent(sessionId = null))
            return@withLock
        }

        // Move by calendar days, then set the wall-clock time, so daylight-saving changes don't shift it
        val target = nowMinute + next.minutesFromNow
        val triggerAt = now.apply {
            add(Calendar.DAY_OF_YEAR, target / (24 * 60))
            set(Calendar.HOUR_OF_DAY, (target % (24 * 60)) / 60)
            set(Calendar.MINUTE, target % 60)
        }.timeInMillis

        val intent = alarmIntent(next.session.id)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    /** In parent mode: every person with reminders on. Otherwise: only the person whose schedule is showing. */
    suspend fun remindedSessions(): List<ClassSession> {
        val everyone = scheduleRepository.getEveryone()
        return if (settingsRepository.parentMode.first()) {
            val reminded = scheduleRepository.profiles.first().filter { it.reminders }.map { it.id }.toSet()
            everyone.filter { it.profileId in reminded }
        } else {
            val active = scheduleRepository.activeProfileId.first()
            everyone.filter { it.profileId == active }
        }
    }

    /** Always the same request code, so setting a new alarm replaces the previous one. */
    private fun alarmIntent(sessionId: Long?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        if (sessionId != null) intent.putExtra(ReminderReceiver.EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
