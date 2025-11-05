package com.tau.nexus_note.codex.crud.update // UPDATED: Package name

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
                OutlinedTextField(
                    value = state.properties[schemaProperty.name] ?: "",
                    onValueChange = {
                        onPropertyChange(schemaProperty.name, it) // UPDATED: Pass property name (string)
                    },
                    label = { Text("${schemaProperty.name} (${schemaProperty.type})") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    // TODO: Add logic for different property types (e.g., Image picker)
                )
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
