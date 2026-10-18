package com.example.schedulemanager.ui.family

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.schedulemanager.ScheduleApp
import com.example.schedulemanager.data.ImportResult
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.SettingsRepository
import com.example.schedulemanager.model.Profile
import com.example.schedulemanager.share.SharedSchedule
import com.example.schedulemanager.ui.share.ImportActions
import com.example.schedulemanager.ui.share.ImportFlowDialogs
import com.example.schedulemanager.ui.share.ScanTarget
import com.example.schedulemanager.ui.share.rememberImportFlowState
import com.example.schedulemanager.ui.theme.LocalAppTokens
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FamilyViewModel(
    private val repository: ScheduleRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val profiles: StateFlow<List<Profile>> =
        repository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** The master "Session reminders" switch in Settings → Notifications */
    val remindersEnabled: StateFlow<Boolean> =
        settings.remindersEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    suspend fun updateProfile(profile: Profile, name: String, color: Color): SaveNameResult =
        repository.updateProfile(profile.id, name, color)

    suspend fun deleteProfile(id: Long): Boolean = repository.deleteProfile(id)

    fun setReminders(id: Long, on: Boolean) {
        viewModelScope.launch { repository.setProfileReminders(id, on) }
    }

    fun setActive(id: Long) {
        viewModelScope.launch { repository.setActiveProfile(id) }
    }

    suspend fun addChild(name: String, color: Color): SaveNameResult = repository.addProfile(name, color)

    suspend fun importAsNewChild(schedule: SharedSchedule, name: String, color: Color): SaveNameResult =
        repository.importAsNewProfile(schedule, name, color)

    suspend fun importInto(profileId: Long, schedule: SharedSchedule, replace: Boolean): ImportResult =
        repository.importInto(profileId, schedule, replace)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ScheduleApp
                FamilyViewModel(app.scheduleRepository, app.settingsRepository)
            }
        }
    }
}

/** Settings → Family: everyone's schedule, updates by QR, and who gets reminders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(onBack: () -> Unit, viewModel: FamilyViewModel = viewModel(factory = FamilyViewModel.Factory)) {
    val tokens = LocalAppTokens.current
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { m -> scope.launch { snackbar.showSnackbar(m) } }
    val importFlow = rememberImportFlowState()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var removing by remember { mutableStateOf<Profile?>(null) }

    Scaffold(
        containerColor = tokens.background,
        topBar = {
            TopAppBar(
                title = { Text("Family") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(profiles, key = { it.id }) { p ->
                PersonCard(
                    profile = p,
                    canRemove = profiles.size > 1,
                    onShow = { viewModel.setActive(p.id); onBack() },
                    onUpdateFromQr = { importFlow.scan(context, showMessage, ScanTarget.Person(p)) },
                    onEdit = { editing = p },
                    onRemove = { removing = p },
                )
            }
            item {
                Surface(
                    onClick = importFlow::startAddChild,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    contentColor = tokens.accentText,
                    border = BorderStroke(1.5.dp, tokens.outline),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add a child", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Text(
                    "REMIND ME 5 MINUTES BEFORE",
                    color = tokens.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (!remindersEnabled) {
                    Text(
                        "Session reminders are off. Turn them on in Settings → Notifications.",
                        color = tokens.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            items(profiles, key = { "reminder-${it.id}" }) { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(10.dp).background(p.color, CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = tokens.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (p.isMe) "\"Chemistry starts in 5 minutes\"" else "\"${p.name} · Chemistry starts in 5 minutes\"",
                            color = tokens.muted,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = p.reminders, onCheckedChange = { viewModel.setReminders(p.id, it) }, enabled = remindersEnabled)
                }
            }
        }
    }

    ImportFlowDialogs(
        state = importFlow,
        actions = ImportActions(
            parentMode = true,
            profiles = profiles,
            importIntoActive = { s, r -> viewModel.importInto(profiles.first().id, s, r) },
            importInto = viewModel::importInto,
            importAsNewChild = viewModel::importAsNewChild,
            addChild = viewModel::addChild,
            activate = { /* stay on this screen; the new person shows in the list */ },
        ),
        onMessage = showMessage,
    )

    editing?.let { p ->
        EditPersonDialog(p, onDismiss = { editing = null }, onSave = { name, color -> viewModel.updateProfile(p, name, color) })
    }

    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${p.name}?") },
            text = { Text("${p.name}'s schedule (${plural(p.sessionCount, "session")}, with its subjects and classrooms) will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch { if (viewModel.deleteProfile(p.id)) showMessage("Removed ${p.name}") }
                }) { Text("Remove", color = tokens.danger) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PersonCard(
    profile: Profile,
    canRemove: Boolean,
    onShow: () -> Unit,
    onUpdateFromQr: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = LocalAppTokens.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onShow,
        shape = RoundedCornerShape(18.dp),
        color = tokens.surface,
        contentColor = tokens.ink,
        border = BorderStroke(1.5.dp, tokens.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(profile.initials, profile.color, 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(profile.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val updated = DateUtils.getRelativeTimeSpanString(profile.updatedAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS)
                Text("${plural(profile.sessionCount, "session")} · updated ${updated.toString().lowercase()}", color = tokens.muted, fontSize = 13.sp)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Options for ${profile.name}") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Update from QR") }, onClick = { menuOpen = false; onUpdateFromQr() })
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                    if (canRemove) {
                        DropdownMenuItem(text = { Text("Remove", color = tokens.danger) }, onClick = { menuOpen = false; onRemove() })
                    }
                }
            }
        }
    }
}
