package com.example.schedulemanager.ui.share

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.schedulemanager.ScheduleApp
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.SettingsRepository
import com.example.schedulemanager.share.ScheduleCodec
import com.example.schedulemanager.share.qrCodeBitmap
import com.example.schedulemanager.ui.common.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ShareState {
    data object Loading : ShareState
    data object Empty : ShareState
    /** [owner] names whose schedule it is in parent mode ("Lina"), null otherwise or for "Me". */
    data class Ready(val qr: Bitmap, val sessions: Int, val subjects: Int, val classrooms: Int, val owner: String?) : ShareState
}

/** Shares whoever's schedule is showing, so a parent can pass on a child's schedule too. */
class ShareViewModel(repository: ScheduleRepository, settings: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow<ShareState>(ShareState.Loading)
    val state: StateFlow<ShareState> = _state

    init {
        // Encoding and drawing the QR code is quick, but keep it off the main thread
        viewModelScope.launch(Dispatchers.Default) {
            val schedule = repository.exportSchedule()
            val activeId = repository.activeProfileId.first()
            val person = repository.profiles.first().firstOrNull { it.id == activeId }
            val owner = if (settings.parentMode.first() && person != null && !person.isMe) person.name else null
            _state.value = if (schedule.sessions.isEmpty()) {
                ShareState.Empty
            } else {
                ShareState.Ready(
                    qr = qrCodeBitmap(ScheduleCodec.encode(schedule)),
                    sessions = schedule.sessions.size,
                    subjects = schedule.subjects.size,
                    classrooms = schedule.classrooms.size,
                    owner = owner,
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ScheduleApp
                ShareViewModel(app.scheduleRepository, app.settingsRepository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScheduleScreen(
    onBack: () -> Unit,
    viewModel: ShareViewModel = viewModel(factory = ShareViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    MaxBrightnessWhileShown()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                ShareState.Loading -> CircularProgressIndicator()
                ShareState.Empty -> Text(
                    "Your schedule is empty.\nAdd some sessions first, then share them.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                is ShareState.Ready -> ReadyContent(s)
            }
        }
    }
}

@Composable
private fun ReadyContent(state: ShareState.Ready) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        // Always dark-on-white, even in dark theme: scanners expect it
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
        ) {
            Image(
                bitmap = state.qr.asImageBitmap(),
                contentDescription = "QR code of your schedule",
                // Nearest-neighbour scaling keeps the modules sharp
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(12.dp),
            )
        }

        Text(
            text = (state.owner?.let { "$it's schedule · " } ?: "") +
                "${plural(state.sessions, "session")} · ${plural(state.subjects, "subject")} · " +
                plural(state.classrooms, "classroom"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "On the other phone, open Schedule Manager,\ntap ⋮ and choose Scan",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Screen brightness up while the QR code is on screen, back to normal when leaving. */
@Composable
private fun MaxBrightnessWhileShown() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        val attributes = window.attributes
        val previous = attributes.screenBrightness
        attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = attributes
        onDispose {
            window.attributes = window.attributes.also { it.screenBrightness = previous }
        }
    }
}

private fun plural(count: Int, word: String) = if (count == 1) "1 $word" else "$count ${word}s"
