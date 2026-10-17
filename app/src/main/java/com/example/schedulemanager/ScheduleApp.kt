package com.example.schedulemanager

import android.app.Application
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.SettingsRepository
import com.example.schedulemanager.data.db.AppDatabase

/** Holds the app-wide singletons (database and repositories). */
class ScheduleApp : Application() {
    val scheduleRepository by lazy { ScheduleRepository(AppDatabase.create(this)) }
    val settingsRepository by lazy { SettingsRepository(this) }
}
