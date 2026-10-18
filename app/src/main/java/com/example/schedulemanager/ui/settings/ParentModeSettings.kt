package com.example.schedulemanager.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.model.MAX_NAME_LENGTH
import com.example.schedulemanager.model.Profile
import com.example.schedulemanager.ui.family.PersonAvatar
import com.example.schedulemanager.ui.family.plural
import com.example.schedulemanager.ui.theme.LocalAppTokens
import kotlinx.coroutines.launch

/**
 * Settings → Family: the parent mode switch and, while it's on, a way into the Family screen.
 * Turning it on the first time asks whose the existing schedule is; turning it off asks whose to keep showing.
 */
@Composable
fun ParentModeSettings(
    parentMode: Boolean,
    profiles: List<Profile>,
    activeProfileId: Long?,
    onTurnOn: suspend (childName: String?) -> SaveNameResult,
    onTurnOff: (keepProfileId: Long) -> Unit,
    onOpenFamily: () -> Unit,
) {
    var settingUp by remember { mutableStateOf(false) }
    var turningOff by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun toggle(on: Boolean) {
        when {
            // Already has several people (it was on before): just turn it back on
            on && profiles.size > 1 -> scope.launch { onTurnOn(null) }
            on -> settingUp = true
            profiles.size > 1 -> turningOff = true
            else -> profiles.firstOrNull()?.let { onTurnOff(it.id) }
        }
    }

    ListItem(
        modifier = Modifier.clickable { toggle(!parentMode) },
        leadingContent = { Icon(Icons.Outlined.Face, contentDescription = null) },
        headlineContent = { Text("Parent mode") },
        supportingContent = { Text("Keep a schedule for each child and switch between them") },
        trailingContent = { Switch(checked = parentMode, onCheckedChange = ::toggle) },
    )
    if (parentMode) {
        ListItem(
            modifier = Modifier.clickable(onClick = onOpenFamily),
            leadingContent = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
            headlineContent = { Text("Family") },
            supportingContent = { Text(if (profiles.size == 1) "1 person" else "${profiles.size} people") },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )
    }

    if (settingUp) {
        val first = profiles.firstOrNull()
        SetupSheet(
            sessionCount = first?.sessionCount ?: 0,
            onDismiss = { settingUp = false },
            onConfirm = { childName -> onTurnOn(childName).also { if (it is SaveNameResult.Saved) settingUp = false } },
        )
    }

    if (turningOff) {
        TurnOffDialog(
            profiles = profiles,
            initial = activeProfileId ?: profiles.first().id,
            onDismiss = { turningOff = false },
            onConfirm = { keep -> turningOff = false; onTurnOff(keep) },
        )
    }
}

/** First time on: is the existing schedule the parent's own, or a child's? */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupSheet(sessionCount: Int, onDismiss: () -> Unit, onConfirm: suspend (childName: String?) -> SaveNameResult) {
    val tokens = LocalAppTokens.current
    val scope = rememberCoroutineScope()
    var mine by remember { mutableStateOf(true) }
    var childName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = tokens.background) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Turn on parent mode", color = tokens.ink, fontFamily = tokens.displayFont, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                if (sessionCount > 0) "You already have a schedule with ${plural(sessionCount, "session")}. Whose is it?"
                else "Is the schedule on this phone yours or a child's?",
                color = tokens.muted,
                fontSize = 15.sp,
            )
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SetupOption(selected = mine, title = "It's mine", description = "It stays as \"Me\". Add your children next.") { mine = true; error = null }
                SetupOption(selected = !mine, title = "It's my child's", description = "Give it their name") { mine = false }
                if (!mine) {
                    OutlinedTextField(
                        value = childName,
                        onValueChange = { if (it.length <= MAX_NAME_LENGTH) { childName = it; error = null } },
                        label = { Text("Child's name") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Button(
                onClick = {
                    if (!mine && childName.isBlank()) {
                        error = "Enter their name"
                        return@Button
                    }
                    scope.launch {
                        when (onConfirm(if (mine) null else childName.trim())) {
                            is SaveNameResult.Saved -> Unit
                            SaveNameResult.Blank -> error = "Enter their name"
                            SaveNameResult.Duplicate -> error = "That name is already used"
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("Turn on", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
}

@Composable
private fun SetupOption(selected: Boolean, title: String, description: String, onSelect: () -> Unit) {
    val tokens = LocalAppTokens.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) tokens.accent.copy(alpha = 0.08f) else tokens.surface,
        contentColor = tokens.ink,
        border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) tokens.accent else tokens.outline),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = null)
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(description, color = tokens.muted, fontSize = 13.sp)
            }
        }
    }
}

/** Turning off: whose schedule stays visible. The others are hidden, not deleted. */
@Composable
private fun TurnOffDialog(profiles: List<Profile>, initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var keep by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turn off parent mode?") },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose whose schedule to keep showing. The others are hidden, not deleted, and come back when you turn parent mode on again.")
                profiles.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = keep == p.id, onClick = { keep = p.id }, role = Role.RadioButton)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = keep == p.id, onClick = null)
                        PersonAvatar(p.initials, p.color)
                        Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(keep) }) { Text("Turn off") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
