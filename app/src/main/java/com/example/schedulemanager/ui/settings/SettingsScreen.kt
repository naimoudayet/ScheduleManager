package com.example.schedulemanager.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManage: (ItemKind) -> Unit,
    onOpenFamily: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val appStyle by viewModel.appStyle.collectAsState()
    val parentMode by viewModel.parentMode.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val use24Hour by viewModel.use24Hour.collectAsState()
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    var confirmingReset by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Themes")
            ThemePicker(selected = appStyle, onSelect = viewModel::setAppStyle)

            SectionHeader("Appearance", Modifier.padding(top = 16.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                val options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label) }
                }
            }

            SectionHeader("Time format", Modifier.padding(top = 16.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                val options = listOf(false to "12-hour", true to "24-hour")
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = use24Hour == value,
                        onClick = { viewModel.setUse24Hour(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label) }
                }
            }

            SectionHeader("Notifications", Modifier.padding(top = 16.dp))
            ReminderSettings(
                enabled = remindersEnabled,
                onEnabledChange = viewModel::setRemindersEnabled,
                canScheduleExact = viewModel::canScheduleExactAlarms,
                onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
            )

            SectionHeader("Manage", Modifier.padding(top = 16.dp))
            ItemKind.entries.forEach { kind ->
                val count by viewModel.counts.getValue(kind).collectAsState()
                ManageRow(
                    title = kind.plural,
                    count = count,
                    icon = when (kind) {
                        ItemKind.CLASSROOM -> Icons.Outlined.Home
                        ItemKind.SUBJECT -> Icons.AutoMirrored.Outlined.List
                    },
                    onClick = { onManage(kind) },
                )
            }

            SectionHeader("Family", Modifier.padding(top = 16.dp))
            ParentModeSettings(
                parentMode = parentMode,
                profiles = profiles,
                activeProfileId = activeProfileId,
                onTurnOn = viewModel::turnOnParentMode,
                onTurnOff = viewModel::turnOffParentMode,
                onOpenFamily = onOpenFamily,
            )

            SectionHeader("Danger zone", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error)
            DangerZoneCard(onReset = { confirmingReset = true })
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "All sessions, subjects and classrooms will be permanently deleted. " +
                        "This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingReset = false
                    scope.launch {
                        viewModel.deleteEverything()
                        snackbar.showSnackbar("All data deleted")
                    }
                }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DangerZoneCard(onReset: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Reset all data", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Delete every session, subject and classroom.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Reset") }
        }
    }
}

@Composable
private fun ManageRow(title: String, count: Int?, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = count?.let { { Text(if (it == 1) "1 item" else "$it items") } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
