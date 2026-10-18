package com.example.schedulemanager.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.NamedItem
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.model.formatTime
import com.example.schedulemanager.ui.common.LocalUse24Hour
import com.example.schedulemanager.ui.theme.LocalAppTokens
import kotlinx.coroutines.launch

/** B · Today first: a bottom sheet of grouped field cards, with a big Save button at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayEditor(state: EditorState, save: (close: () -> Unit) -> Unit, onDismiss: () -> Unit) {
    val tokens = LocalAppTokens.current
    val use24Hour = LocalUse24Hour.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Slide the sheet down, then tell the caller
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    state.close = close

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = tokens.background) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (state.editing == null) "Add session" else "Edit session",
                color = tokens.ink,
                fontFamily = tokens.displayFont,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(tokens.surface)) {
                FieldRow(
                    kind = ItemKind.SUBJECT,
                    items = state.subjects,
                    selected = state.subject,
                    onSelect = { state.subjectId = it },
                    onAddNew = { state.addingItem = ItemKind.SUBJECT },
                )
                HorizontalDivider(color = tokens.outline.copy(alpha = 0.6f))
                FieldRow(
                    kind = ItemKind.CLASSROOM,
                    items = state.classrooms,
                    selected = state.classroom,
                    onSelect = { state.classroomId = it },
                    onAddNew = { state.addingItem = ItemKind.CLASSROOM },
                )
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(tokens.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayNames.forEachIndexed { index, name ->
                        val selected = index == state.day
                        Surface(
                            onClick = { state.day = index },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) tokens.ink else tokens.background,
                            contentColor = if (selected) tokens.background else tokens.ink,
                            modifier = Modifier.weight(1f).height(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(name.take(3), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
                val clash = state.clash != null || !state.timesValid
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigTime("Starts", formatTime(state.start, use24Hour), highlight = clash, Modifier.weight(1f)) {
                        state.pickingTime = TimeField.START
                    }
                    BigTime("Ends", formatTime(state.end, use24Hour), highlight = !state.timesValid, Modifier.weight(1f)) {
                        state.pickingTime = TimeField.END
                    }
                }
            }

            TimeStatus(state)

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { save(close) },
                enabled = state.canSave,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save session", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            if (state.editing != null) {
                TextButton(onClick = { state.confirmingDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Delete session", color = tokens.danger, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** A labelled row that opens a menu of subjects or classrooms, with "New …" at the bottom. */
@Composable
private fun FieldRow(
    kind: ItemKind,
    items: List<NamedItem>,
    selected: NamedItem?,
    onSelect: (Long) -> Unit,
    onAddNew: () -> Unit,
) {
    val tokens = LocalAppTokens.current
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, color = tokens.surface, contentColor = tokens.ink, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                val swatch = selected?.colors
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(swatch?.background ?: tokens.background)
                        .then(if (swatch != null) Modifier.border(2.dp, swatch.foreground, RoundedCornerShape(12.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (kind == ItemKind.CLASSROOM) Icon(Icons.Outlined.Home, contentDescription = null, tint = tokens.muted, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(kind.singular, color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        selected?.name ?: "Choose a ${kind.singular.lowercase()}",
                        color = if (selected != null) tokens.ink else tokens.muted,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = tokens.muted)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name) },
                    leadingIcon = item.colors?.let { c ->
                        { Box(Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).background(c.background).border(1.5.dp, c.foreground, RoundedCornerShape(8.dp))) }
                    },
                    onClick = { onSelect(item.id); open = false },
                )
            }
            if (items.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("New ${kind.singular.lowercase()}…") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { open = false; onAddNew() },
            )
        }
    }
}

@Composable
private fun BigTime(label: String, value: String, highlight: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (highlight) tokens.dangerContainer else tokens.background,
        contentColor = tokens.ink,
        border = BorderStroke(2.dp, if (highlight) tokens.danger else tokens.background),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(value, fontFamily = tokens.displayFont, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}
