package com.example.schedulemanager.ui.session

import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.data.SaveSessionResult
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.NamedItem
import com.example.schedulemanager.model.SessionDraft
import com.example.schedulemanager.model.SessionPalette
import com.example.schedulemanager.model.TileColors
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.model.formatTime
import com.example.schedulemanager.model.time
import com.example.schedulemanager.ui.ScheduleTile
import com.example.schedulemanager.ui.common.NameDialog
import kotlinx.coroutines.launch

/** What the editor is opened for. */
sealed interface EditorTarget {
    data class New(val dayOfWeek: Int) : EditorTarget
    data class Edit(val session: ClassSession) : EditorTarget
}

private enum class TimeField { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorSheet(
    target: EditorTarget,
    classrooms: List<NamedItem>,
    subjects: List<NamedItem>,
    onSave: suspend (SessionDraft) -> SaveSessionResult,
    onDelete: suspend (Long) -> Unit,
    onAddItem: suspend (ItemKind, String, TileColors?) -> SaveNameResult,
    onDismiss: () -> Unit,
) {
    val editing = (target as? EditorTarget.Edit)?.session

    var subjectId by remember { mutableStateOf(editing?.subjectId) }
    var classroomId by remember { mutableStateOf(editing?.classroomId) }
    var day by remember { mutableIntStateOf(editing?.dayOfWeek ?: (target as EditorTarget.New).dayOfWeek) }
    var start by remember { mutableIntStateOf(editing?.startMinutes ?: time(8)) }
    var end by remember { mutableIntStateOf(editing?.endMinutes ?: time(9)) }

    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf<TimeField?>(null) }
    var addingItem by remember { mutableStateOf<ItemKind?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Close with the sheet's slide-down animation, then tell the caller
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val classroomName = classrooms.firstOrNull { it.id == classroomId }?.name
    val subject = subjects.firstOrNull { it.id == subjectId }
    val subjectName = subject?.name
    val timesValid = end > start
    val canSave = !saving && subjectName != null && classroomName != null && timesValid

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (editing == null) "New session" else "Edit session",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            // Live preview of the tile being edited
            ScheduleTile(
                session = ClassSession(
                    id = editing?.id ?: -1,
                    dayOfWeek = day,
                    startMinutes = start,
                    endMinutes = end,
                    classroomId = classroomId ?: -1,
                    classroom = classroomName ?: "Classroom",
                    subjectId = subjectId ?: -1,
                    subject = subjectName ?: "Subject",
                    // Neutral until a subject (and so its color) is picked
                    backgroundColor = subject?.colors?.background ?: MaterialTheme.colorScheme.surfaceVariant,
                    foregroundColor = subject?.colors?.foreground ?: MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            ItemPicker(
                kind = ItemKind.SUBJECT,
                items = subjects,
                selectedName = subjectName,
                onSelect = { subjectId = it; error = null },
                onAddNew = { addingItem = ItemKind.SUBJECT },
            )

            ItemPicker(
                kind = ItemKind.CLASSROOM,
                items = classrooms,
                selectedName = classroomName,
                onSelect = { classroomId = it; error = null },
                onAddNew = { addingItem = ItemKind.CLASSROOM },
            )

            SectionLabel("Day")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dayNames.forEachIndexed { index, name ->
                    FilterChip(
                        selected = day == index,
                        onClick = { day = index; error = null },
                        label = { Text(name.take(3)) },
                    )
                }
            }

            SectionLabel("Time")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeButton("Start", start, Modifier.weight(1f)) { pickingTime = TimeField.START }
                TimeButton("End", end, Modifier.weight(1f)) { pickingTime = TimeField.END }
            }
            if (!timesValid) {
                Text(
                    "End time must be after start time",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editing != null) {
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = ::close) { Text("Cancel") }
                Spacer(Modifier.size(8.dp))
                Button(
                    enabled = canSave,
                    onClick = {
                        val roomId = classroomId ?: return@Button
                        val subId = subjectId ?: return@Button
                        saving = true
                        scope.launch {
                            val draft = SessionDraft(
                                id = editing?.id,
                                dayOfWeek = day,
                                startMinutes = start,
                                endMinutes = end,
                                classroomId = roomId,
                                subjectId = subId,
                            )
                            when (val result = onSave(draft)) {
                                SaveSessionResult.Saved -> close()
                                is SaveSessionResult.Invalid -> error = result.message
                                is SaveSessionResult.Clash -> error = with(result.with) {
                                    "Clashes with $subject " +
                                        "(${formatTime(startMinutes)}–${formatTime(endMinutes)}) " +
                                        "on ${dayNames[dayOfWeek]}"
                                }
                            }
                            saving = false
                        }
                    },
                ) { Text("Save") }
            }
            Spacer(Modifier.size(8.dp))
        }
    }

    pickingTime?.let { field ->
        TimePickerDialog(
            title = if (field == TimeField.START) "Start time" else "End time",
            initialMinutes = if (field == TimeField.START) start else end,
            onDismiss = { pickingTime = null },
            onConfirm = { minutes ->
                if (field == TimeField.START) {
                    // Keep the same length when moving the start time
                    val length = end - start
                    start = minutes
                    if (length > 0) end = minOf(minutes + length, time(23, 59))
                } else {
                    end = minutes
                }
                error = null
                pickingTime = null
            },
        )
    }

    addingItem?.let { kind ->
        NameDialog(
            title = "New ${kind.singular.lowercase()}",
            label = "${kind.singular} name",
            initialName = "",
            initialColors = if (kind == ItemKind.SUBJECT) SessionPalette.all.first() else null,
            onDismiss = { addingItem = null },
            onSave = { name, pickedColors -> onAddItem(kind, name, pickedColors) },
            onSaved = { id, _ ->
                // Select the item that was just created
                when (kind) {
                    ItemKind.CLASSROOM -> classroomId = id
                    ItemKind.SUBJECT -> subjectId = id
                }
                error = null
                addingItem = null
            },
        )
    }

    if (confirmingDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete session?") },
            text = {
                Text(
                    "${editing.subject} on ${dayNames[editing.dayOfWeek]}, " +
                        "${formatTime(editing.startMinutes)}–${formatTime(editing.endMinutes)}"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    scope.launch {
                        onDelete(editing.id)
                        close()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/** Dropdown to pick a classroom or subject, with a "New …" entry at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemPicker(
    kind: ItemKind,
    items: List<NamedItem>,
    selectedName: String?,
    onSelect: (Long) -> Unit,
    onAddNew: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(kind.singular) },
            placeholder = { Text("Choose a ${kind.singular.lowercase()}") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name) },
                    onClick = { onSelect(item.id); expanded = false },
                )
            }
            if (items.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("New ${kind.singular.lowercase()}…") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { expanded = false; onAddNew() },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TimeButton(label: String, minutes: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(formatTime(minutes), style = MaterialTheme.typography.titleMedium)
        }
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
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
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
