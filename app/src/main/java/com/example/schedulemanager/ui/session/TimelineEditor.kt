package com.example.schedulemanager.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.model.formatTime
import com.example.schedulemanager.ui.ScheduleTile
import com.example.schedulemanager.ui.common.LocalUse24Hour
import com.example.schedulemanager.ui.theme.LocalAppTokens

/** A · Timeline: a full-screen form where every choice is a chip, one tap each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineEditor(state: EditorState, save: (close: () -> Unit) -> Unit, onDismiss: () -> Unit) {
    val tokens = LocalAppTokens.current
    val use24Hour = LocalUse24Hour.current
    state.close = onDismiss

    FullScreenEditorDialog(onDismiss) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.ink) }
                Text(
                    if (state.editing == null) "New session" else "Edit session",
                    color = tokens.ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { save(onDismiss) },
                    enabled = state.canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            }

            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                ScheduleTile(session = state.preview(tokens.surfaceVariant, tokens.muted))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorSectionTitle("Subject")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.subjects.forEach { s ->
                            ChoiceChip(s.name, selected = s.id == state.subjectId, onClick = { state.subjectId = s.id }, swatch = s.colors)
                        }
                        NewChip("New", onClick = { state.addingItem = ItemKind.SUBJECT })
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorSectionTitle("Classroom")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.classrooms.forEach { c ->
                            ChoiceChip(c.name, selected = c.id == state.classroomId, onClick = { state.classroomId = c.id })
                        }
                        NewChip("New", onClick = { state.addingItem = ItemKind.CLASSROOM })
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorSectionTitle("Day")
                    // Seven equal shares of the width, so all days fit on any screen size
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayNames.forEachIndexed { index, name ->
                            val selected = index == state.day
                            Surface(
                                onClick = { state.day = index },
                                shape = CircleShape,
                                color = if (selected) tokens.accent else tokens.surface,
                                contentColor = if (selected) tokens.onAccent else tokens.ink,
                                border = BorderStroke(1.5.dp, if (selected) tokens.accent else tokens.outline),
                                modifier = Modifier.weight(1f).aspectRatio(1f).semantics { contentDescription = name },
                            ) {
                                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Text(name.take(1), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorSectionTitle("Time")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeTile("Starts", formatTime(state.start, use24Hour), Modifier.weight(1f)) { state.pickingTime = TimeField.START }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = tokens.muted)
                        TimeTile("Ends", formatTime(state.end, use24Hour), Modifier.weight(1f)) { state.pickingTime = TimeField.END }
                    }
                    TimeStatus(state)
                }

                if (state.editing != null) {
                    TextButton(onClick = { state.confirmingDelete = true }) {
                        Text("Delete session", color = tokens.danger, fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.height(24.dp)) {}
            }
        }
    }
}

@Composable
private fun TimeTile(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = tokens.surface,
        contentColor = tokens.ink,
        border = BorderStroke(1.5.dp, tokens.outline),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}
