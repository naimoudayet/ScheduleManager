package com.example.schedulemanager.ui.schedule

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.schedulemanager.ScheduleApp
import com.example.schedulemanager.data.ImportResult
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.data.SaveSessionResult
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.SettingsRepository
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.NamedItem
import com.example.schedulemanager.model.Profile
import com.example.schedulemanager.model.SessionDraft
import com.example.schedulemanager.model.TileColors
import com.example.schedulemanager.share.SharedSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** The active person's whole week, for the layouts and the editor's clash check. null while loading. */
    val week: StateFlow<List<ClassSession>?> = repository.observeWeek().state(null)

    val classrooms: StateFlow<List<NamedItem>> = repository.observeItems(ItemKind.CLASSROOM).state(emptyList())
    val subjects: StateFlow<List<NamedItem>> = repository.observeItems(ItemKind.SUBJECT).state(emptyList())

    // ---- Parent mode ----

    val parentMode: StateFlow<Boolean> = settings.parentMode.state(false)
    val profiles: StateFlow<List<Profile>> = repository.profiles.state(emptyList())
    val activeProfileId: StateFlow<Long?> = repository.activeProfileId.state(null)
    /** Everyone's sessions, for the "Right now" strip. */
    val everyone: StateFlow<List<ClassSession>> = repository.observeEveryone().state(emptyList())

    fun setActiveProfile(id: Long) {
        viewModelScope.launch { repository.setActiveProfile(id) }
    }

    suspend fun addChild(name: String, color: Color): SaveNameResult = repository.addProfile(name, color)

    suspend fun importAsNewChild(schedule: SharedSchedule, name: String, color: Color): SaveNameResult =
        repository.importAsNewProfile(schedule, name, color)

    suspend fun importInto(profileId: Long, schedule: SharedSchedule, replace: Boolean): ImportResult =
        repository.importInto(profileId, schedule, replace)

    // ---- Sessions ----

    suspend fun saveSession(draft: SessionDraft): SaveSessionResult = repository.saveSession(draft)

    suspend fun deleteSession(id: Long) = repository.deleteSession(id)

    suspend fun importSchedule(schedule: SharedSchedule, replace: Boolean): ImportResult =
        repository.importSchedule(schedule, replace)

    suspend fun addItem(kind: ItemKind, name: String, colors: TileColors?): SaveNameResult =
        repository.saveItem(kind, null, name, colors)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ScheduleApp
                ScheduleViewModel(app.scheduleRepository, app.settingsRepository)
            }
        }
    }
}
