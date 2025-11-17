package com.tau.nexus_note.codex.crud.update // UPDATED: Package name

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.NodeEditState

@Composable
fun EditNodeView(
    state: NodeEditState,
    onPropertyChange: (String, String) -> Unit, // UPDATED: Key is now a String
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Edit Node: ${state.schema.name}", style = MaterialTheme.typography.headlineSmall) // UPDATED: Use schema.name
        Spacer(modifier = Modifier.height(16.dp))

        Text("Properties", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            // UPDATED: Iterate over schema properties, get values from state.properties map
            itemsIndexed(state.schema.properties) { index, schemaProperty ->
                val currentValue = state.properties[schemaProperty.name] ?: ""
                val modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                val onValueChange = { value: String -> onPropertyChange(schemaProperty.name, value) }

                when (schemaProperty.type) {
                    "Number" -> {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = {
                                // Only allow valid numeric input
                                if (it.isEmpty() || it == "-" || it.matches(Regex("-?\\d*(\\.\\d*)?"))) {
                                    onValueChange(it)
                                }
                            },
                            label = { Text("${schemaProperty.name} (Number)") },
                            modifier = modifier,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    "LongText" -> {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = onValueChange,
                            label = { Text("${schemaProperty.name} (LongText)") },
                            modifier = modifier,
                            singleLine = false,
                            maxLines = 5
                        )
                    }
                    "Date" -> {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = onValueChange,
                            label = { Text("${schemaProperty.name} (Date)") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = modifier
                        )
                    }
                    "Image", "Audio" -> {
                        Row(
                            modifier = modifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentValue, // This would be a file path
                                onValueChange = onValueChange,
                                label = { Text("${schemaProperty.name} (${schemaProperty.type} Path)") },
                                modifier = Modifier.weight(1f),
                                readOnly = true
                            )
                            Button(
                                onClick = { /* TODO: Launch file picker */ },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("...")
                            }
                        }
                    }
                    else -> { // Default "Text"
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = onValueChange,
                            label = { Text("${schemaProperty.name} (Text)") },
                            modifier = modifier,
                            singleLine = true
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = onSave) {
                Text("Save")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}