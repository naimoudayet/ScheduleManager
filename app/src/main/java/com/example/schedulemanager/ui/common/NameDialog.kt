package com.example.schedulemanager.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.schedulemanager.data.SaveNameResult
import com.example.schedulemanager.model.MAX_NAME_LENGTH
import com.example.schedulemanager.model.TileColors
import kotlinx.coroutines.launch

/**
 * Dialog to add or rename a classroom or subject. Errors (blank / duplicate name) are shown inline;
 * on success [onSaved] is called with the item's id and the caller closes the dialog.
 * Pass [initialColors] to also show a color picker (used for subjects).
 */
@Composable
fun NameDialog(
    title: String,
    label: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: suspend (name: String, colors: TileColors?) -> SaveNameResult,
    onSaved: (id: Long, colors: TileColors?) -> Unit,
    initialColors: TileColors? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var colors by remember { mutableStateOf(initialColors) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_NAME_LENGTH) { name = it; error = null } },
                    label = { Text(label) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                colors?.let { selected ->
                    Spacer(Modifier.height(8.dp))
                    Text("Color", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(12.dp))
                    ColorPalettePicker(selected = selected, onSelect = { colors = it }, swatchSize = 40.dp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && name.isNotBlank(),
                onClick = {
                    saving = true
                    scope.launch {
                        when (val result = onSave(name, colors)) {
                            is SaveNameResult.Saved -> onSaved(result.id, colors)
                            SaveNameResult.Blank -> error = "Enter a name"
                            SaveNameResult.Duplicate -> error = "This name already exists"
                        }
                        saving = false
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
