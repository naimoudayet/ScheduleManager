package com.example.schedulemanager.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.MAX_NAME_LENGTH
import com.example.schedulemanager.model.Profile
import com.example.schedulemanager.model.ProfilePalette
import com.example.schedulemanager.model.formatMinutes
import com.example.schedulemanager.model.formatTime
import com.example.schedulemanager.share.SharedSchedule
import com.example.schedulemanager.ui.common.LocalUse24Hour
import com.example.schedulemanager.ui.schedule.Now
import com.example.schedulemanager.ui.schedule.Phase
import com.example.schedulemanager.ui.schedule.phase
import com.example.schedulemanager.ui.session.FullScreenEditorDialog
import com.example.schedulemanager.ui.theme.LocalAppTokens
import kotlinx.coroutines.launch

/** A round avatar: "Me", or the person's first letter, on their color. */
@Composable
fun PersonAvatar(initials: String, color: Color, size: Dp = 32.dp) {
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Main screen, parent mode: tap a person to see their week; + adds a child. */
@Composable
fun FamilyBar(profiles: List<Profile>, activeId: Long, onSelect: (Long) -> Unit, onAddChild: () -> Unit) {
    val tokens = LocalAppTokens.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        profiles.forEach { p ->
            val selected = p.id == activeId
            Surface(
                onClick = { onSelect(p.id) },
                shape = CircleShape,
                color = if (selected) p.color.copy(alpha = 0.14f) else tokens.surface,
                contentColor = tokens.ink,
                border = BorderStroke(2.dp, if (selected) p.color else tokens.outline),
                modifier = Modifier.height(44.dp).semantics { this.contentDescription = "${p.name}'s schedule" },
            ) {
                Row(Modifier.padding(start = 4.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(p.initials, p.color)
                    Spacer(Modifier.width(8.dp))
                    Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Surface(
            onClick = onAddChild,
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = tokens.accentText,
            border = BorderStroke(1.5.dp, tokens.outline),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = "Add a child") }
        }
    }
}

/** What a person is doing right now, for the "Right now" strip. */
data class PersonStatus(val what: String, val detail: String)

fun personStatus(today: List<ClassSession>, now: Now, use24Hour: Boolean): PersonStatus {
    today.firstOrNull { it.phase(now) == Phase.NOW }?.let {
        return PersonStatus(it.subject, "${it.classroom} · until ${formatTime(it.endMinutes, use24Hour)}")
    }
    today.firstOrNull { it.phase(now) == Phase.UPCOMING }?.let {
        return PersonStatus("Free", "${it.subject} at ${formatTime(it.startMinutes, use24Hour)} · in ${formatMinutes(it.startMinutes - now.minutes)}")
    }
    return if (today.isEmpty()) PersonStatus("Free", "No classes today") else PersonStatus("Done", "No more classes today")
}

/** Where is everyone right now? One card per person; tapping one shows their week. */
@Composable
fun RightNowStrip(profiles: List<Profile>, everyone: List<ClassSession>, now: Now, onSelect: (Long) -> Unit) {
    val tokens = LocalAppTokens.current
    val use24Hour = LocalUse24Hour.current
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            "RIGHT NOW · ${formatTime(now.minutes, use24Hour)}",
            color = tokens.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            profiles.forEach { p ->
                val today = everyone.filter { it.profileId == p.id && it.dayOfWeek == now.day }.sortedBy { it.startMinutes }
                val status = personStatus(today, now, use24Hour)
                Surface(
                    onClick = { onSelect(p.id) },
                    shape = RoundedCornerShape(16.dp),
                    color = tokens.surface,
                    contentColor = tokens.ink,
                    border = BorderStroke(1.5.dp, tokens.outline),
                    modifier = Modifier.widthIn(min = 150.dp, max = 220.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(p.color, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Text(status.what, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(status.detail, color = tokens.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Avatar colors to choose from. */
@Composable
fun PersonColorPicker(selected: Color, onSelect: (Color) -> Unit) {
    val tokens = LocalAppTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfilePalette.all.forEach { c ->
            val isSelected = c == selected
            Box(
                Modifier
                    .size(40.dp)
                    .then(if (isSelected) Modifier.border(2.dp, tokens.ink, CircleShape).padding(4.dp) else Modifier)
                    .clip(CircleShape)
                    .background(c)
                    .selectable(selected = isSelected, onClick = { onSelect(c) }, role = Role.RadioButton),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Add a child: name and color, then scan their QR code (recommended) or enter the schedule by hand.
 * With [scanned] (a schedule scanned before choosing who it's for) it just asks name and color and saves.
 */
@Composable
fun AddChildDialog(
    existingNames: List<String>,
    scanned: SharedSchedule?,
    onDismiss: () -> Unit,
    onScan: (name: String, color: Color) -> Unit,
    onCreate: suspend (name: String, color: Color) -> SaveNameResult,
) {
    val tokens = LocalAppTokens.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(ProfilePalette.all.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun validName(): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> { error = "Enter a name"; null }
            existingNames.any { it.equals(trimmed, ignoreCase = true) } -> { error = "There's already someone called $trimmed"; null }
            else -> trimmed
        }
    }

    fun create() {
        val valid = validName() ?: return
        saving = true
        scope.launch {
            when (onCreate(valid, color)) {
                is SaveNameResult.Saved -> onDismiss()
                SaveNameResult.Blank -> error = "Enter a name"
                SaveNameResult.Duplicate -> error = "There's already someone called $valid"
            }
            saving = false
        }
    }

    FullScreenEditorDialog(onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.ink) }
                Text("Add a child", color = tokens.ink, fontFamily = tokens.displayFont, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PersonAvatar(name.trim().take(1).uppercase().ifEmpty { "?" }, color, size = 64.dp)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= MAX_NAME_LENGTH) { name = it; error = null } },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1f),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("COLOR", color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    PersonColorPicker(color, onSelect = { color = it })
                }

                val shownName = name.trim().ifEmpty { "their" }
                if (scanned != null) {
                    Text(
                        "${plural(scanned.sessions.size, "session")} · ${plural(scanned.subjects.size, "subject")} · ${plural(scanned.classrooms.size, "classroom")}",
                        color = tokens.muted,
                        fontSize = 15.sp,
                    )
                    Button(
                        onClick = ::create,
                        enabled = !saving,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) { Text(if (name.isBlank()) "Save" else "Save for ${name.trim()}", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                } else {
                    Text(
                        if (name.isBlank()) "How do you want to add their schedule?" else "How do you want to add $shownName's schedule?",
                        color = tokens.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    MethodCard(
                        title = "Scan their QR code",
                        badge = "Fastest",
                        description = "On their phone: ⋮ → Share",
                        primary = true,
                        onClick = { validName()?.let { onScan(it, color) } },
                    )
                    MethodCard(
                        title = "Enter it myself",
                        badge = null,
                        description = "Start with an empty week",
                        primary = false,
                        onClick = { if (!saving) create() },
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodCard(title: String, badge: String?, description: String, primary: Boolean, onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (primary) tokens.accent.copy(alpha = 0.10f) else tokens.surface,
        contentColor = tokens.ink,
        border = BorderStroke(if (primary) 2.dp else 1.5.dp, if (primary) tokens.accent else tokens.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (badge != null) {
                    Text(
                        badge,
                        color = tokens.onAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(tokens.accent).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Text(description, color = tokens.muted, fontSize = 13.sp)
        }
    }
}

/** Rename / recolor a person. */
@Composable
fun EditPersonDialog(profile: Profile, onDismiss: () -> Unit, onSave: suspend (String, Color) -> SaveNameResult) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(profile.name) }
    var color by remember { mutableStateOf(profile.color) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${profile.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_NAME_LENGTH) { name = it; error = null } },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                if (!profile.isMe) PersonColorPicker(color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (onSave(name, color)) {
                        is SaveNameResult.Saved -> onDismiss()
                        SaveNameResult.Blank -> error = "Enter a name"
                        SaveNameResult.Duplicate -> error = "There's already someone called ${name.trim()}"
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** After ⋮ → Scan in parent mode: replace someone's schedule, or add it as a new child. */
@Composable
fun WhoseScheduleDialog(
    scanned: SharedSchedule,
    profiles: List<Profile>,
    onDismiss: () -> Unit,
    onReplace: (Profile) -> Unit,
    onNewChild: () -> Unit,
) {
    val tokens = LocalAppTokens.current
    var chosen by remember { mutableStateOf<Long?>(null) } // null = new child
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Whose schedule is this?") },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${plural(scanned.sessions.size, "session")} · ${plural(scanned.subjects.size, "subject")} · ${plural(scanned.classrooms.size, "classroom")}",
                    color = tokens.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                profiles.forEach { p ->
                    val selected = chosen == p.id
                    PersonOption(
                        selected = selected,
                        avatar = { PersonAvatar(p.initials, p.color, 36.dp) },
                        title = p.name,
                        note = if (selected) "Replaces their ${plural(p.sessionCount, "session")}" else plural(p.sessionCount, "session"),
                        noteColor = if (selected) tokens.danger else tokens.muted,
                        onSelect = { chosen = p.id },
                    )
                }
                PersonOption(
                    selected = chosen == null,
                    avatar = {
                        Box(Modifier.size(36.dp).background(tokens.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = tokens.accentText)
                        }
                    },
                    title = "A new child",
                    note = "Add them with this schedule",
                    noteColor = tokens.muted,
                    onSelect = { chosen = null },
                )
            }
        },
        confirmButton = {
            val person = profiles.firstOrNull { it.id == chosen }
            TextButton(onClick = { if (person != null) onReplace(person) else onNewChild() }) {
                Text(if (person != null) "Replace ${person.name}'s schedule" else "Continue", color = if (person != null) tokens.danger else tokens.accentText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PersonOption(
    selected: Boolean,
    avatar: @Composable () -> Unit,
    title: String,
    note: String,
    noteColor: Color,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        avatar()
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(note, color = noteColor, fontSize = 13.sp)
        }
    }
}

/** Confirms a scan that's already aimed at someone: a new child, or updating a person. */
@Composable
fun ScannedForPersonDialog(
    title: String,
    scanned: SharedSchedule,
    warning: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onScanAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAppTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${plural(scanned.sessions.size, "session")} · ${plural(scanned.subjects.size, "subject")} · ${plural(scanned.classrooms.size, "classroom")}",
                    fontSize = 15.sp,
                )
                WeekBars(scanned)
                if (warning != null) Text(warning, color = tokens.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onScanAgain) { Text("Scan again") } },
    )
}

/** Sessions per day as small bars, Monday to Sunday. */
@Composable
private fun WeekBars(scanned: SharedSchedule) {
    val tokens = LocalAppTokens.current
    val counts = (0..6).map { d -> scanned.sessions.count { it.dayOfWeek == d } }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((6 + 50f * counts[i] / max).dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (counts[i] > 0) tokens.accent else tokens.outline)
                )
                Text(label, color = tokens.muted, fontSize = 12.sp)
            }
        }
    }
}

fun plural(count: Int, word: String) = if (count == 1) "1 $word" else "$count ${word}s"
