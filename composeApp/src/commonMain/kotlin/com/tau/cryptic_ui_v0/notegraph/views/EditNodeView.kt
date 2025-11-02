package com.tau.cryptic_ui_v0.notegraph.views // UPDATED: Package name

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.ClusterDisplayItem
import com.tau.cryptic_ui_v0.NodeEditState // UPDATED: Uses new state class

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNodeView(
    state: NodeEditState,
    onPropertyChange: (String, String) -> Unit, // UPDATED: Key is now a String
    // --- ADDED ---
    onClusterChange: (ClusterDisplayItem?) -> Unit,
    // --- END ADDED ---
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    var clusterExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Edit Node: ${state.schema.name}", style = MaterialTheme.typography.headlineSmall) // UPDATED: Use schema.name
        Spacer(modifier = Modifier.height(16.dp))

        // --- ADDED: Cluster Assignment Dropdown ---
        ExposedDropdownMenuBox(
            expanded = clusterExpanded,
            onExpandedChange = { clusterExpanded = !clusterExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            val selectedCluster = state.availableClusters.find { it.id == state.clusterId }
            val displayText = selectedCluster?.let { "${it.label} : ${it.displayProperty}" } ?: "None (Unclustered)"

            OutlinedTextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Assign to Cluster") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clusterExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = clusterExpanded,
                onDismissRequest = { clusterExpanded = false }
            ) {
                // "None" option
                DropdownMenuItem(
                    text = { Text("None (Unclustered)") },
                    onClick = {
                        onClusterChange(null)
                        clusterExpanded = false
                    }
                )
                // All available clusters
                state.availableClusters.forEach { cluster ->
                    DropdownMenuItem(
                        text = { Text("${cluster.label} : ${cluster.displayProperty}") },
                        onClick = {
                            onClusterChange(cluster)
                            clusterExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // --- END ADDED ---


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
