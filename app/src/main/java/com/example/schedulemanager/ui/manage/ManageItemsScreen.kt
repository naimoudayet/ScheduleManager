package com.example.schedulemanager.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.schedulemanager.data.DeleteItemResult
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.NamedItem
import com.example.schedulemanager.model.SessionPalette
import com.example.schedulemanager.ui.common.NameDialog
import kotlinx.coroutines.launch

/** Add / rename dialog state: [item] is null when adding. */
private data class EditDialog(val item: NamedItem?)

/** Full CRUD screen for classrooms or subjects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageItemsScreen(
    kind: ItemKind,
    onBack: () -> Unit,
    viewModel: ManageItemsViewModel = viewModel(key = kind.name, factory = ManageItemsViewModel.factory(kind)),
) {
    val items by viewModel.items.collectAsState()
    var editDialog by remember { mutableStateOf<EditDialog?>(null) }
    var deleting by remember { mutableStateOf<NamedItem?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val singular = kind.singular.lowercase()

    fun showInUse(count: Int) {
        val sessions = if (count == 1) "1 session" else "$count sessions"
        scope.launch { snackbar.showSnackbar("Used by $sessions. Change or delete them first.") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kind.plural) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editDialog = EditDialog(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Add $singular")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        val list = items
        when {
            list == null -> Unit
            list.isEmpty() -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No ${kind.plural.lowercase()} yet\nTap + to add one",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding),
                // Extra bottom space so the FAB never covers the last row
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onEdit = { editDialog = EditDialog(item) },
                        onDelete = {
                            if (item.sessionCount > 0) showInUse(item.sessionCount) else deleting = item
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    editDialog?.let { dialog ->
        NameDialog(
            title = when {
                dialog.item == null -> "New $singular"
                kind == ItemKind.SUBJECT -> "Edit $singular"
                else -> "Rename $singular"
            },
            label = "${kind.singular} name",
            initialName = dialog.item?.name ?: "",
            // Subjects also pick a color
            initialColors = if (kind == ItemKind.SUBJECT) dialog.item?.colors ?: SessionPalette.all.first() else null,
            onDismiss = { editDialog = null },
            onSave = { name, colors -> viewModel.save(dialog.item?.id, name, colors) },
            onSaved = { _, _ -> editDialog = null },
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $singular?") },
            text = { Text(item.name) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        // Re-checked in the database, in case a session started using it meanwhile
                        val result = viewModel.delete(item.id)
                        if (result is DeleteItemResult.InUse) showInUse(result.sessionCount)
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ItemRow(
    item: NamedItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            leadingContent = {
                // Initial-letter avatar, in the subject's own colors when it has them
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(item.colors?.background ?: MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.name.take(1).uppercase(),
                        color = item.colors?.foreground ?: MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            headlineContent = { Text(item.name, fontWeight = FontWeight.Medium) },
            supportingContent = {
                Text(
                    when (item.sessionCount) {
                        0 -> "Not used"
                        1 -> "1 session"
                        else -> "${item.sessionCount} sessions"
                    }
                )
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit ${item.name}")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete ${item.name}")
                    }
                }
            },
        )
    }
}
