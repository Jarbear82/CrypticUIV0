package com.tau.nexus_note.codex.crud.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.EdgeEditState

@Composable
fun EditEdgeView(
    state: EdgeEditState,
    onPropertyChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Edit Edge: ${state.schema.name}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // --- UPDATED ---
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
        // --- END UPDATE ---

        Spacer(modifier = Modifier.height(16.dp))

        Text("Properties", style = MaterialTheme.typography.titleMedium)
        if (state.schema.properties.isEmpty()) { // UPDATED: Check schema for properties
            Text(
                "No properties to edit.",
                style = MaterialTheme.typography.bodySmall,
                // --- UPDATED ---
                color = MaterialTheme.colorScheme.onSurfaceVariant
                // --- END UPDATE ---
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                // UPDATED: Iterate over schema properties, get values from state.properties map
                itemsIndexed(state.schema.properties) { index, schemaProperty ->
                    OutlinedTextField(
                        value = state.properties[schemaProperty.name] ?: "",
                        onValueChange = {
                            onPropertyChange(schemaProperty.name, it) // UPDATED: Pass property name (string)
                        },
                        label = { Text("${schemaProperty.name} (${schemaProperty.type})") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
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