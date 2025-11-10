package com.tau.nexus_note.codex.crud.update // UPDATED: Package name

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.NodeEditState
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.datamodels.SchemaPropertyTypes

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
                // --- ADDED: Conditional Rendering ---
                when (schemaProperty.type) {
                    SchemaPropertyTypes.EDGE_REF -> {
                        EdgePropertySelector(
                            property = schemaProperty,
                            allEdges = state.allEdges,
                            currentValue = state.properties[schemaProperty.name],
                            onPropertyChanged = onPropertyChange
                        )
                    }
                    else -> {
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
                // --- END UPDATES ---
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


/**
 * A private composable for selecting an Edge for an "EdgeRef" property.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EdgePropertySelector(
    property: SchemaProperty,
    allEdges: List<EdgeDisplayItem>,
    currentValue: String?,
    onPropertyChanged: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Find the full display text for the currently selected edge ID
    val selectedEdgeText = remember(currentValue, allEdges) {
        val selectedId = currentValue?.toLongOrNull()
        allEdges.find { it.id == selectedId }
            ?.let { "${it.label}: ${it.src.displayProperty} -> ${it.dst.displayProperty}" }
            ?: "Select Edge..."
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = selectedEdgeText,
            onValueChange = {},
            readOnly = true,
            label = { Text("${property.name}: ${property.type}") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Add a "None" option
            DropdownMenuItem(
                text = { Text("None (Clear Selection)") },
                onClick = {
                    onPropertyChanged(property.name, "") // Pass empty string
                    expanded = false
                }
            )
            // List all available edges
            allEdges.forEach { edge ->
                val edgeText = "${edge.label}: ${edge.src.displayProperty} -> ${edge.dst.displayProperty}"
                DropdownMenuItem(
                    text = { Text(edgeText) },
                    onClick = {
                        onPropertyChanged(property.name, edge.id.toString())
                        expanded = false
                    }
                )
            }
        }
    }
}