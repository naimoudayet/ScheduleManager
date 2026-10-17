package com.example.schedulemanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.schedulemanager.ScheduleApp
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.SettingsRepository
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
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

    /** Deletes all sessions, subjects and classrooms. The theme setting is kept. */
    suspend fun deleteEverything() = scheduleRepository.deleteEverything()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ScheduleApp
                SettingsViewModel(app.scheduleRepository, app.settingsRepository)
            }
        }
    }
}
