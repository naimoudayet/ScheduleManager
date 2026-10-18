package com.example.schedulemanager.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.schedulemanager.model.AppStyle
import com.example.schedulemanager.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val styleKey = stringPreferencesKey("app_style")
    private val use24HourKey = booleanPreferencesKey("use_24_hour")
    private val remindersKey = booleanPreferencesKey("reminders_enabled")
    private val parentModeKey = booleanPreferencesKey("parent_mode")
    private val activeProfileKey = longPreferencesKey("active_profile")

    val themeMode: Flow<ThemeMode> = context.settingsStore.data.map { prefs ->
        prefs[themeKey]?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[themeKey] = mode.name }
    }

    /** The visual theme (Timeline / Today first / Night bold). Defaults to Timeline. */
    val appStyle: Flow<AppStyle> = context.settingsStore.data.map { prefs ->
        prefs[styleKey]?.let { saved -> AppStyle.entries.firstOrNull { it.name == saved } } ?: AppStyle.TIMELINE
    }

    suspend fun setAppStyle(style: AppStyle) {
        context.settingsStore.edit { it[styleKey] = style.name }
    }

    /** true = "14:05", false = "2:05 PM". Defaults to 24-hour. */
    val use24Hour: Flow<Boolean> = context.settingsStore.data.map { it[use24HourKey] ?: true }

    suspend fun setUse24Hour(value: Boolean) {
        context.settingsStore.edit { it[use24HourKey] = value }
    }

    /** Notify 5 minutes before each session. Off by default. */
    val remindersEnabled: Flow<Boolean> = context.settingsStore.data.map { it[remindersKey] ?: false }

    suspend fun setRemindersEnabled(value: Boolean) {
        context.settingsStore.edit { it[remindersKey] = value }
    }

    /** Several schedules (one per child) with a switcher on the main screen. Off by default. */
    val parentMode: Flow<Boolean> = context.settingsStore.data.map { it[parentModeKey] ?: false }

    suspend fun setParentMode(value: Boolean) {
        context.settingsStore.edit { it[parentModeKey] = value }
    }

    /** Whose schedule is showing. Null until one is chosen; the repository then falls back to the first. */
    val activeProfileId: Flow<Long?> = context.settingsStore.data.map { it[activeProfileKey] }

    suspend fun setActiveProfileId(id: Long) {
        context.settingsStore.edit { it[activeProfileKey] = id }
    }
}
