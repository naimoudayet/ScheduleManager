package com.example.schedulemanager.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.schedulemanager.ScheduleApp
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.data.SaveSessionResult
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.NamedItem
import com.example.schedulemanager.model.SessionDraft
import com.example.schedulemanager.model.TileColors
import com.example.schedulemanager.model.dayNames
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ScheduleViewModel(private val repository: ScheduleRepository) : ViewModel() {

    /** One live list per day; null until the first load finishes. */
    private val days: List<StateFlow<List<ClassSession>?>> = dayNames.indices.map { day ->
        repository.observeDay(day).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    val classrooms: StateFlow<List<NamedItem>> = items(ItemKind.CLASSROOM)
    val subjects: StateFlow<List<NamedItem>> = items(ItemKind.SUBJECT)

    private fun items(kind: ItemKind) =
        repository.observeItems(kind).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun sessions(day: Int): StateFlow<List<ClassSession>?> = days[day]

    suspend fun saveSession(draft: SessionDraft): SaveSessionResult = repository.saveSession(draft)

    suspend fun deleteSession(id: Long) = repository.deleteSession(id)

    suspend fun addItem(kind: ItemKind, name: String, colors: TileColors?): SaveNameResult =
        repository.saveItem(kind, null, name, colors)

    companion object {
        val Factory = viewModelFactory {
            initializer { ScheduleViewModel((this[APPLICATION_KEY] as ScheduleApp).scheduleRepository) }
        }
    }
}
