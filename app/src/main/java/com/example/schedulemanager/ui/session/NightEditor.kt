package com.example.schedulemanager.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.model.formatTime
import com.example.schedulemanager.ui.common.LocalUse24Hour
import com.example.schedulemanager.ui.theme.LocalAppTokens

/** C · Night bold: time first, with ±15 min steppers; subjects as a grid of their own colors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NightEditor(state: EditorState, save: (close: () -> Unit) -> Unit, onDismiss: () -> Unit) {
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
                ) { Text("Save", fontWeight = FontWeight.ExtraBold) }
            }

            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeStepper(
                        label = "FROM",
                        value = formatTime(state.start, use24Hour),
                        onPick = { state.pickingTime = TimeField.START },
                        onMinus = { state.moveStart(state.start - STEP_MINUTES) },
                        onPlus = { state.moveStart(state.start + STEP_MINUTES) },
                    )
                    TimeStepper(
                        label = "TO",
                        value = formatTime(state.end, use24Hour),
                        onPick = { state.pickingTime = TimeField.END },
                        onMinus = { state.changeEnd(state.end - STEP_MINUTES) },
                        onPlus = { state.changeEnd(state.end + STEP_MINUTES) },
                    )
                    TimeStatus(state)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayNames.forEachIndexed { index, name ->
                        val selected = index == state.day
                        Surface(
                            onClick = { state.day = index },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) tokens.accent else tokens.surface,
                            contentColor = if (selected) tokens.onAccent else tokens.ink,
                            modifier = Modifier.weight(1f).height(46.dp).semantics { contentDescription = name },
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(name.take(1), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorSectionTitle("Subject")
                    // Three equal columns of subject tiles, then a "New" tile
                    val tiles: List<(@Composable (Modifier) -> Unit)> = state.subjects.map { s ->
                        @Composable { m: Modifier -> SubjectTile(s.name, s.colors?.background, s.colors?.foreground, selected = s.id == state.subjectId, m) { state.subjectId = s.id } }
                    } + listOf(@Composable { m: Modifier -> NewChip("New", onClick = { state.addingItem = ItemKind.SUBJECT }, height = 64.dp, modifier = m) })
                    tiles.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { tile -> tile(Modifier.weight(1f)) }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
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

                if (state.editing != null) {
                    TextButton(onClick = { state.confirmingDelete = true }) {
                        Text("Delete session", color = tokens.danger, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** How far the −/+ buttons move a time. */
private const val STEP_MINUTES = 30

/**
 * FROM / TO row: the label sits above the time so the time gets the full width left of the
 * buttons, and it shrinks to fit (e.g. "10:45 PM" in 12-hour mode) instead of wrapping.
 */
@Composable
private fun TimeStepper(label: String, value: String, onPick: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    val tokens = LocalAppTokens.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = tokens.surface,
        contentColor = tokens.ink,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Change time", onClick = onPick)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(label, color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                BasicText(
                    value,
                    style = TextStyle(color = tokens.ink, fontFamily = tokens.displayFont, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 30.sp, stepSize = 1.sp),
                )
            }
            StepButton("−$STEP_MINUTES", "$STEP_MINUTES minutes earlier", onMinus)
            Spacer(Modifier.width(8.dp))
            StepButton("+$STEP_MINUTES", "$STEP_MINUTES minutes later", onPlus)
        }
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = tokens.surfaceVariant,
        contentColor = tokens.ink,
        modifier = Modifier.size(46.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun SubjectTile(name: String, background: Color?, ink: Color?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = background ?: tokens.surface,
        contentColor = ink ?: tokens.ink,
        // A ring in the accent marks the chosen subject
        border = if (selected) BorderStroke(3.dp, tokens.accent) else null,
        modifier = modifier.height(64.dp),
    ) {
        Box(Modifier.padding(10.dp), contentAlignment = Alignment.BottomStart) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
