package com.example.schedulemanager

import android.app.Application
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.SettingsRepository
import com.example.schedulemanager.data.db.AppDatabase
import com.example.schedulemanager.reminders.ReminderNotifications
import com.example.schedulemanager.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Holds the app-wide singletons (database, repositories, reminder scheduling). */
class ScheduleApp : Application() {
    /** Lives as long as the process; used for work that must outlive a screen (e.g. receivers). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val scheduleRepository by lazy { ScheduleRepository(AppDatabase.create(this), settingsRepository) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val reminderScheduler by lazy { ReminderScheduler(this, scheduleRepository, settingsRepository) }

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)

        // Re-plan the next reminder whenever a session, a person or a reminder setting changes
        appScope.launch {
            combine(
                scheduleRepository.observeEveryone(),
                scheduleRepository.profiles,
                scheduleRepository.activeProfileId,
                settingsRepository.parentMode,
                settingsRepository.remindersEnabled,
            ) { _, _, _, _, _ -> }
                .collectLatest { reminderScheduler.reschedule() }
        }
    }
}
