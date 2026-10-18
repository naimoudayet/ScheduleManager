package com.example.schedulemanager.ui.session

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.data.SaveSessionResult
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.LAST_END_MINUTE
import com.example.schedulemanager.model.NamedItem
import com.example.schedulemanager.model.SessionDraft
import com.example.schedulemanager.model.SessionPalette
import com.example.schedulemanager.model.TileColors
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.model.findClash
import com.example.schedulemanager.model.formatTimeRange
import com.example.schedulemanager.model.suggestFreeSlot
import com.example.schedulemanager.model.time
import com.example.schedulemanager.ui.common.LocalUse24Hour
import com.example.schedulemanager.ui.common.NameDialog
import com.example.schedulemanager.ui.theme.LocalAppTokens
import com.example.schedulemanager.model.AppStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the editor is opened for. */
sealed interface EditorTarget {
    data class New(val dayOfWeek: Int) : EditorTarget
    data class Edit(val session: ClassSession) : EditorTarget
}

enum class TimeField { START, END }

/**
 * The editor's form and rules, shared by the three theme layouts. Clashes are checked live against
 * the whole week, so Save is only enabled for a session that fits; the repository checks again on save.
 */
@Stable
class EditorState(target: EditorTarget) {
    val editing: ClassSession? = (target as? EditorTarget.Edit)?.session

    var subjectId by mutableStateOf(editing?.subjectId)
    var classroomId by mutableStateOf(editing?.classroomId)
    var day by mutableIntStateOf(editing?.dayOfWeek ?: (target as EditorTarget.New).dayOfWeek)
    var start by mutableIntStateOf(editing?.startMinutes ?: time(8))
    var end by mutableIntStateOf(editing?.endMinutes ?: time(9))

    var saving by mutableStateOf(false)
    /** An error from saving (e.g. someone else's change); live problems are derived below. */
    var saveError by mutableStateOf<String?>(null)
    var pickingTime by mutableStateOf<TimeField?>(null)
    var addingItem by mutableStateOf<ItemKind?>(null)
    var confirmingDelete by mutableStateOf(false)

    // Kept current by the host; state so the layouts redraw when they change
    internal var week by mutableStateOf<List<ClassSession>>(emptyList())
    internal var subjects by mutableStateOf<List<NamedItem>>(emptyList())
    internal var classrooms by mutableStateOf<List<NamedItem>>(emptyList())

    /** How the current layout closes itself (the bottom sheet animates first); used after deleting. */
    internal var close: () -> Unit = {}

    val subject: NamedItem? get() = subjects.firstOrNull { it.id == subjectId }
    val classroom: NamedItem? get() = classrooms.firstOrNull { it.id == classroomId }
    val length: Int get() = end - start
    val timesValid: Boolean get() = end > start

    private val daySessions: List<ClassSession> get() = week.filter { it.dayOfWeek == day }

    /** The session the current choice overlaps, if any. */
    val clash: ClassSession? get() = if (timesValid) findClash(daySessions, start, end, editing?.id) else null

    /** A start time that keeps the same length and fits, offered as a one-tap fix for [clash]. */
    val suggestedStart: Int? get() = clash?.let { suggestFreeSlot(daySessions, length, from = start, ignoreId = editing?.id) }

    val canSave: Boolean get() = !saving && subject != null && classroom != null && timesValid && clash == null

    /** Moves the start and keeps the length the same (so the end follows). */
    fun moveStart(minutes: Int) {
        val keep = length
        start = minutes.coerceIn(0, LAST_END_MINUTE - 1)
        if (keep > 0) end = (start + keep).coerceAtMost(LAST_END_MINUTE)
        saveError = null
    }

    fun changeEnd(minutes: Int) {
        end = minutes.coerceIn(1, LAST_END_MINUTE)
        saveError = null
    }

    fun applySuggestion() {
        suggestedStart?.let(::moveStart)
    }

    fun preview(neutralBackground: androidx.compose.ui.graphics.Color, neutralInk: androidx.compose.ui.graphics.Color) = ClassSession(
        id = editing?.id ?: -1,
        dayOfWeek = day,
        startMinutes = start,
        endMinutes = end,
        classroomId = classroomId ?: -1,
        classroom = classroom?.name ?: "Classroom",
        subjectId = subjectId ?: -1,
        subject = subject?.name ?: "Subject",
        // Neutral until a subject (and so its color) is picked
        backgroundColor = subject?.colors?.background ?: neutralBackground,
        foregroundColor = subject?.colors?.foreground ?: neutralInk,
    )

    fun save(
        scope: CoroutineScope,
        onSave: suspend (SessionDraft) -> SaveSessionResult,
        use24Hour: Boolean,
        onSaved: () -> Unit,
    ) {
        val subjectId = subjectId ?: return
        val classroomId = classroomId ?: return
        saving = true
        scope.launch {
            val result = onSave(SessionDraft(editing?.id, day, start, end, classroomId, subjectId))
            when (result) {
                SaveSessionResult.Saved -> onSaved()
                is SaveSessionResult.Invalid -> saveError = result.message
                is SaveSessionResult.Clash -> saveError = with(result.with) {
                    "Overlaps $subject, ${formatTimeRange(startMinutes, endMinutes, use24Hour)}"
                }
            }
            saving = false
        }
    }
}

/**
 * Add / edit a session, in the active theme's layout: a full-screen form (Timeline), a bottom
 * sheet (Today first) or a time-first full-screen form (Night bold).
 */
@Composable
fun SessionEditor(
    target: EditorTarget,
    week: List<ClassSession>,
    classrooms: List<NamedItem>,
    subjects: List<NamedItem>,
    onSave: suspend (SessionDraft) -> SaveSessionResult,
    onDelete: suspend (Long) -> Unit,
    onAddItem: suspend (ItemKind, String, TileColors?) -> SaveNameResult,
    onDismiss: () -> Unit,
) {
    val state = remember(target) {
        EditorState(target).also {
            it.week = week
            it.subjects = subjects
            it.classrooms = classrooms
        }
    }
    SideEffect {
        state.week = week
        state.subjects = subjects
        state.classrooms = classrooms
    }

    val scope = rememberCoroutineScope()
    val use24Hour = LocalUse24Hour.current
    val style = LocalAppTokens.current.style

    // Each layout passes how it closes itself, so Save can close it after a successful save
    val save: (close: () -> Unit) -> Unit = { close -> state.save(scope, onSave, use24Hour, onSaved = close) }
    when (style) {
        AppStyle.TIMELINE -> TimelineEditor(state, save, onDismiss)
        AppStyle.TODAY -> TodayEditor(state, save, onDismiss)
        AppStyle.NIGHT -> NightEditor(state, save, onDismiss)
    }

    state.pickingTime?.let { field ->
        TimePickerDialog(
            title = if (field == TimeField.START) "Start time" else "End time",
            initialMinutes = if (field == TimeField.START) state.start else state.end,
            onDismiss = { state.pickingTime = null },
            onConfirm = { minutes ->
                if (field == TimeField.START) state.moveStart(minutes) else state.changeEnd(minutes)
                state.pickingTime = null
            },
        )
    }

    state.addingItem?.let { kind ->
        NameDialog(
            title = "New ${kind.singular.lowercase()}",
            label = "${kind.singular} name",
            initialName = "",
            initialColors = if (kind == ItemKind.SUBJECT) SessionPalette.all.first() else null,
            onDismiss = { state.addingItem = null },
            onSave = { name, pickedColors -> onAddItem(kind, name, pickedColors) },
            onSaved = { id, _ ->
                // Select the item that was just created
                when (kind) {
                    ItemKind.CLASSROOM -> state.classroomId = id
                    ItemKind.SUBJECT -> state.subjectId = id
                }
                state.addingItem = null
            },
        )
    }

    val editing = state.editing
    if (state.confirmingDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { state.confirmingDelete = false },
            title = { Text("Delete session?") },
            text = {
                Text("${editing.subject} on ${dayNames[editing.dayOfWeek]}, " +
                    formatTimeRange(editing.startMinutes, editing.endMinutes, use24Hour))
            },
            confirmButton = {
                TextButton(onClick = {
                    state.confirmingDelete = false
                    scope.launch {
                        onDelete(editing.id)
                        state.close()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { state.confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = LocalUse24Hour.current,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(time(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
