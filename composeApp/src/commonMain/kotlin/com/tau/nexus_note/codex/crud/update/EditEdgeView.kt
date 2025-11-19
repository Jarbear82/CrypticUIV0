package com.tau.nexus_note.codex.crud.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.datamodels.EdgeEditState

@Composable
fun EditEdgeView(
    state: EdgeEditState,
    onPropertyChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Edit Edge: ${state.schema.name}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Using onSurfaceVariant for secondary, "muted" text
            Text(
                "From: ${state.src.label} (${state.src.displayProperty})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "To: ${state.dst.label} (${state.dst.displayProperty})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Properties", style = MaterialTheme.typography.titleMedium)
            if (state.schema.properties.isEmpty()) {
                Text(
                    "No properties to edit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.schema.properties.forEach { schemaProperty ->
                    val currentValue = state.properties[schemaProperty.name] ?: ""
                    val modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    val onValueChange = { value: String -> onPropertyChange(schemaProperty.name, value) }

                    when (schemaProperty.type) {
                        CodexPropertyDataTypes.NUMBER -> {
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = {
                                    if (it.isEmpty() || it == "-" || it.matches(Regex("-?\\d*(\\.\\d*)?"))) {
                                        onValueChange(it)
                                    }
                                },
                                label = { Text("${schemaProperty.name} (Number)") },
                                modifier = modifier,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        CodexPropertyDataTypes.LONG_TEXT -> {
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = onValueChange,
                                label = { Text("${schemaProperty.name} (LongText)") },
                                modifier = modifier,
                                singleLine = false,
                                maxLines = 5
                            )
                        }
                        CodexPropertyDataTypes.DATE -> {
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = onValueChange,
                                label = { Text("${schemaProperty.name} (Date)") },
                                placeholder = { Text("YYYY-MM-DD") },
                                modifier = modifier
                            )
                        }
                        CodexPropertyDataTypes.IMAGE, CodexPropertyDataTypes.AUDIO -> {
                            Row(
                                modifier = modifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = currentValue,
                                    onValueChange = onValueChange,
                                    label = { Text("${schemaProperty.name} (${schemaProperty.type.displayName} Path)") },
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fixed Buttons
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