package com.example.schedulemanager.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.schedulemanager.reminders.REMINDER_LEAD_MINUTES
import com.example.schedulemanager.reminders.ReminderNotifications
import com.example.schedulemanager.ui.common.findActivity

/**
 * The "Session reminders" switch. Turning it on asks for the notification permission
 * (Android 13+); if it was denied for good or notifications are blocked, a dialog offers
 * to open the system settings, and reminders switch on once the user comes back with them allowed.
 */
@Composable
fun ReminderSettings(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    canScheduleExact: () -> Boolean,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current

    // Permission states can change in system settings, so re-read them every time the screen resumes
    var resumeCount by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeCount++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val notificationsAllowed = remember(resumeCount) { ReminderNotifications.canNotify(context) }
    val exactAllowed = remember(resumeCount) { canScheduleExact() }

    var showBlockedDialog by remember { mutableStateOf(false) }
    // Set when we send the user to system settings to allow notifications
    var enableWhenAllowed by remember { mutableStateOf(false) }

    LaunchedEffect(resumeCount) {
        if (enableWhenAllowed && notificationsAllowed) {
            enableWhenAllowed = false
            onEnabledChange(true)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted && ReminderNotifications.canNotify(context) -> onEnabledChange(true)
            // Denied once: Android will ask again next time, so just explain
            !granted && context.findActivity()?.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
            ) == true -> onMessage("Reminders need permission to show notifications")
            // Denied for good (Android no longer shows its dialog) or blocked in settings
            else -> showBlockedDialog = true
        }
    }

    fun turnOn() {
        when {
            ReminderNotifications.canNotify(context) -> onEnabledChange(true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            // Permission is fine but the user turned the app's notifications off
            else -> showBlockedDialog = true
        }
    }

    fun toggle(on: Boolean) = if (on) turnOn() else onEnabledChange(false)

    ListItem(
        modifier = Modifier.clickable { toggle(!enabled) },
        leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
        headlineContent = { Text("Session reminders") },
        supportingContent = { Text("Notify $REMINDER_LEAD_MINUTES minutes before each session starts") },
        trailingContent = { Switch(checked = enabled, onCheckedChange = ::toggle) },
    )

    if (enabled && !notificationsAllowed) {
        NoticeCard(
            text = "Notifications are turned off, so reminders can't be shown.",
            action = "Open settings",
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            onAction = { context.openNotificationSettings() },
        )
    } else if (enabled && !exactAllowed) {
        NoticeCard(
            text = "Reminders may arrive a few minutes late. Allow alarms to get them on time.",
            action = "Allow",
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            onAction = { context.openExactAlarmSettings() },
        )
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
            title = { Text("Allow notifications") },
            text = {
                Text(
                    "Notifications are turned off for this app. Turn them on in system settings " +
                        "to get a reminder before each session."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBlockedDialog = false
                    enableWhenAllowed = true
                    context.openNotificationSettings()
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showBlockedDialog = false }) { Text("Not now") }
            },
        )
    }
}

@Composable
private fun NoticeCard(text: String, action: String, container: Color, content: Color, onAction: () -> Unit) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action, color = content) }
        }
    }
}

private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
    startActivity(intent)
}

private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", packageName, null)))
}
