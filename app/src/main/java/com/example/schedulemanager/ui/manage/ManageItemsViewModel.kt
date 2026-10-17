package com.example.schedulemanager.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.schedulemanager.ScheduleApp
import com.example.schedulemanager.data.DeleteItemResult
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.NamedItem
import com.example.schedulemanager.model.TileColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Backs the Classrooms and Subjects screens; [kind] picks which one. */
class ManageItemsViewModel(
    val kind: ItemKind,
    private val repository: ScheduleRepository,
) : ViewModel() {

    /** null until the first load finishes. */
    val items: StateFlow<List<NamedItem>?> =
        repository.observeItems(kind).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Adds an item when [id] is null, otherwise renames it. [colors] is used for subjects only. */
    suspend fun save(id: Long?, name: String, colors: TileColors?): SaveNameResult =
        repository.saveItem(kind, id, name, colors)

    suspend fun delete(id: Long): DeleteItemResult = repository.deleteItem(kind, id)

    companion object {
        fun factory(kind: ItemKind) = viewModelFactory {
            initializer { ManageItemsViewModel(kind, (this[APPLICATION_KEY] as ScheduleApp).scheduleRepository) }
        }
    }
}
