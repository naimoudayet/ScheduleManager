package com.example.schedulemanager.ui.share

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.schedulemanager.data.ImportResult
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.model.Profile
import com.example.schedulemanager.share.InvalidScheduleCodeException
import com.example.schedulemanager.share.ScheduleCodec
import com.example.schedulemanager.share.SharedSchedule
import com.example.schedulemanager.ui.family.AddChildDialog
import com.example.schedulemanager.ui.family.ScannedForPersonDialog
import com.example.schedulemanager.ui.family.WhoseScheduleDialog
import com.example.schedulemanager.ui.family.plural
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

/** Who a scan is for. */
sealed interface ScanTarget {
    /** From ⋮ → Scan: in parent mode, ask afterwards. */
    data object Ask : ScanTarget
    /** From "Add a child" → Scan their QR code. */
    data class NewChild(val name: String, val color: Color) : ScanTarget
    /** From Family → Update from QR. */
    data class Person(val profile: Profile) : ScanTarget
}

/**
 * Scan → preview → import, plus adding a child. Call [scan] or [startAddChild] from a button, and put
 * [ImportFlowDialogs] in the same screen to show the previews, questions and errors.
 */
class ImportFlowState {
    internal var target by mutableStateOf<ScanTarget>(ScanTarget.Ask)
    internal var scanned by mutableStateOf<SharedSchedule?>(null)
    internal var scanError by mutableStateOf<String?>(null)
    /** The "Add a child" screen is open; [pendingSchedule] is set when a scan came first. */
    internal var addingChild by mutableStateOf(false)
    internal var pendingSchedule by mutableStateOf<SharedSchedule?>(null)

    /** Opens Google's QR scanner, which needs no camera permission. */
    fun scan(context: Context, onMessage: (String) -> Unit, target: ScanTarget = ScanTarget.Ask) {
        this.target = target
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                try {
                    scanned = ScheduleCodec.decode(barcode.rawValue.orEmpty())
                } catch (e: InvalidScheduleCodeException) {
                    scanError = e.message
                }
            }
            .addOnFailureListener { e ->
                // The scanner is a Google Play services module that may still be downloading
                val message = if (e is MlKitException && e.errorCode == MlKitException.UNAVAILABLE) {
                    "The scanner is still being installed. Try again in a moment."
                } else {
                    "Couldn't open the scanner"
                }
                onMessage(message)
            }
        // Cancelling the scan needs no feedback
    }

    fun startAddChild() {
        pendingSchedule = null
        addingChild = true
    }
}

@Composable
fun rememberImportFlowState() = remember { ImportFlowState() }

/** What the import flow can do; the screens wire these to their view model. */
class ImportActions(
    val parentMode: Boolean,
    val profiles: List<Profile>,
    val importIntoActive: suspend (SharedSchedule, replace: Boolean) -> ImportResult,
    val importInto: suspend (profileId: Long, SharedSchedule, replace: Boolean) -> ImportResult,
    val importAsNewChild: suspend (SharedSchedule, name: String, color: Color) -> SaveNameResult,
    val addChild: suspend (name: String, color: Color) -> SaveNameResult,
    /** Show this person's week */
    val activate: (profileId: Long) -> Unit,
)

@Composable
fun ImportFlowDialogs(state: ImportFlowState, actions: ImportActions, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun rescan() {
        val target = state.target
        state.scanned = null
        state.scan(context, onMessage, target)
    }

    state.scanned?.let { schedule ->
        when (val target = state.target) {
            ScanTarget.Ask -> if (!actions.parentMode) {
                ImportDialog(
                    schedule = schedule,
                    onDismiss = { state.scanned = null },
                    onImport = { replace ->
                        state.scanned = null
                        scope.launch { onMessage(importMessage(actions.importIntoActive(schedule, replace))) }
                    },
                )
            } else {
                WhoseScheduleDialog(
                    scanned = schedule,
                    profiles = actions.profiles,
                    onDismiss = { state.scanned = null },
                    onReplace = { person ->
                        state.scanned = null
                        scope.launch {
                            val result = actions.importInto(person.id, schedule, true)
                            actions.activate(person.id)
                            onMessage("Updated ${person.name}'s schedule · ${plural(result.added, "session")}")
                        }
                    },
                    onNewChild = {
                        state.scanned = null
                        state.pendingSchedule = schedule
                        state.addingChild = true
                    },
                )
            }

            is ScanTarget.NewChild -> ScannedForPersonDialog(
                title = "${target.name}'s schedule is ready",
                scanned = schedule,
                warning = null,
                confirmLabel = "Save for ${target.name}",
                onConfirm = {
                    state.scanned = null
                    scope.launch {
                        when (val result = actions.importAsNewChild(schedule, target.name, target.color)) {
                            is SaveNameResult.Saved -> {
                                actions.activate(result.id)
                                onMessage("Added ${target.name} · ${plural(schedule.sessions.size, "session")}")
                            }
                            SaveNameResult.Duplicate -> onMessage("There's already someone called ${target.name}")
                            SaveNameResult.Blank -> onMessage("Enter a name")
                        }
                    }
                },
                onScanAgain = ::rescan,
                onDismiss = { state.scanned = null },
            )

            is ScanTarget.Person -> ScannedForPersonDialog(
                title = "Update ${target.profile.name}'s schedule?",
                scanned = schedule,
                warning = if (target.profile.sessionCount > 0) "Replaces their ${plural(target.profile.sessionCount, "session")}" else null,
                confirmLabel = "Replace",
                onConfirm = {
                    state.scanned = null
                    scope.launch {
                        val result = actions.importInto(target.profile.id, schedule, true)
                        onMessage("Updated ${target.profile.name}'s schedule · ${plural(result.added, "session")}")
                    }
                },
                onScanAgain = ::rescan,
                onDismiss = { state.scanned = null },
            )
        }
    }

    if (state.addingChild) {
        val pending = state.pendingSchedule
        AddChildDialog(
            existingNames = actions.profiles.map { it.name },
            scanned = pending,
            onDismiss = { state.addingChild = false; state.pendingSchedule = null },
            onScan = { name, color ->
                state.addingChild = false
                state.scan(context, onMessage, ScanTarget.NewChild(name, color))
            },
            onCreate = { name, color ->
                val result = if (pending != null) actions.importAsNewChild(pending, name, color) else actions.addChild(name, color)
                if (result is SaveNameResult.Saved) {
                    actions.activate(result.id)
                    onMessage(if (pending != null) "Added $name · ${plural(pending.sessions.size, "session")}" else "Added $name")
                }
                result
            },
        )
    }

    state.scanError?.let { message ->
        AlertDialog(
            onDismissRequest = { state.scanError = null },
            title = { Text("Can't import") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { state.scanError = null; state.scan(context, onMessage, state.target) }) { Text("Scan again") }
            },
            dismissButton = { TextButton(onClick = { state.scanError = null }) { Text("Close") } },
        )
    }
}

private fun importMessage(result: ImportResult): String =
    if (result.skipped == 0) {
        "Imported ${plural(result.added, "session")}"
    } else {
        "Imported ${plural(result.added, "session")}, skipped ${result.skipped} that overlap yours"
    }

/** Without parent mode: add to or replace the one schedule. Nothing changes until the user confirms. */
@Composable
private fun ImportDialog(schedule: SharedSchedule, onDismiss: () -> Unit, onImport: (replace: Boolean) -> Unit) {
    var replace by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import schedule?") },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(
                    "${plural(schedule.sessions.size, "session")} · ${plural(schedule.subjects.size, "subject")} · " +
                        plural(schedule.classrooms.size, "classroom"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(12.dp))
                ImportOption(
                    selected = !replace,
                    title = "Add to my schedule",
                    description = "Sessions that overlap yours are skipped",
                    onSelect = { replace = false },
                )
                ImportOption(
                    selected = replace,
                    title = "Replace my schedule",
                    description = "Deletes your current sessions, subjects and classrooms first",
                    descriptionColor = MaterialTheme.colorScheme.error,
                    onSelect = { replace = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(replace) }) {
                Text(
                    if (replace) "Replace" else "Import",
                    color = if (replace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImportOption(
    selected: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = descriptionColor)
        }
    }
}
