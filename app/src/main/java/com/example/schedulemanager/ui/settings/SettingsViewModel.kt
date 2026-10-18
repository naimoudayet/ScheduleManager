package com.example.schedulemanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.schedulemanager.ScheduleApp
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.SettingsRepository
import com.example.schedulemanager.model.AppStyle
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.Profile
import com.example.schedulemanager.model.ProfilePalette
import com.example.schedulemanager.model.ThemeMode
import com.example.schedulemanager.reminders.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        settingsRepository.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** How many classrooms / subjects exist, shown next to each menu entry. null while loading. */
    val counts: Map<ItemKind, StateFlow<Int?>> = ItemKind.entries.associateWith { kind ->
        scheduleRepository.observeItems(kind).map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    val appStyle: StateFlow<AppStyle> =
        settingsRepository.appStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppStyle.TIMELINE)

    fun setAppStyle(style: AppStyle) {
        viewModelScope.launch { settingsRepository.setAppStyle(style) }
    }

    val use24Hour: StateFlow<Boolean> =
        settingsRepository.use24Hour.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setUse24Hour(value: Boolean) {
        viewModelScope.launch { settingsRepository.setUse24Hour(value) }
    }

    val remindersEnabled: StateFlow<Boolean> =
        settingsRepository.remindersEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The app re-plans the reminder alarm itself when this changes (see ScheduleApp). */
    fun setRemindersEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setRemindersEnabled(value) }
    }

    fun canScheduleExactAlarms(): Boolean = reminderScheduler.canScheduleExact()

    // ---- Parent mode ----

    val parentMode: StateFlow<Boolean> =
        settingsRepository.parentMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val profiles: StateFlow<List<Profile>> =
        scheduleRepository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeProfileId: StateFlow<Long?> =
        scheduleRepository.activeProfileId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Turns parent mode on. With only one person so far, the existing schedule is first labelled as
     * the parent's own ([childName] null) or a child's.
     */
    suspend fun turnOnParentMode(childName: String?): SaveNameResult {
        val result = if (profiles.value.size <= 1) {
            scheduleRepository.labelFirstProfile(childName, ProfilePalette.all.first())
        } else {
            SaveNameResult.Saved(activeProfileId.value ?: 0)
        }
        if (result is SaveNameResult.Saved) settingsRepository.setParentMode(true)
        return result
    }

    /** Turns parent mode off, keeping [keepProfileId]'s schedule on screen (the others are only hidden). */
    fun turnOffParentMode(keepProfileId: Long) {
        viewModelScope.launch {
            scheduleRepository.setActiveProfile(keepProfileId)
            settingsRepository.setParentMode(false)
        }
    }

    /** Deletes all sessions, subjects and classrooms. The theme setting is kept. */
    suspend fun deleteEverything() = scheduleRepository.deleteEverything()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ScheduleApp
                SettingsViewModel(app.scheduleRepository, app.settingsRepository, app.reminderScheduler)
            }
        }
    }
}
