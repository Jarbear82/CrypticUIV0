package com.tau.nexus_note.codex.crud.create // UPDATED: Package name

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.NodeCreationState
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.datamodels.SchemaPropertyTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNodeView(
    nodeCreationState: NodeCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(8.dp)) {
        Text("Create Node", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = nodeCreationState.selectedSchema?.name ?: "Select Schema",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                nodeCreationState.schemas.forEach { schema ->
                    DropdownMenuItem(
                        text = { Text(schema.name) }, // UPDATED: Use .name
                        onClick = {
                            onSchemaSelected(schema)
                            expanded = false
                        }
                    )
                }
            }
        }

        // --- UPDATED: Iterate over properties from selectedSchema ---
        nodeCreationState.selectedSchema?.properties?.forEach { property ->
            when (property.type) {
                SchemaPropertyTypes.EDGE_REF -> {
                    // --- ADDED: Edge Reference Dropdown ---
                    EdgePropertySelector(
                        property = property,
                        allEdges = nodeCreationState.allEdges,
                        currentValue = nodeCreationState.properties[property.name],
                        onPropertyChanged = onPropertyChanged
                    )
                }
                else -> {
                    // --- Original Text Field ---
                    OutlinedTextField(
                        value = nodeCreationState.properties[property.name] ?: "",
                        onValueChange = { onPropertyChanged(property.name, it) },
                        label = { Text("${property.name}: ${property.type}") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        }
        // --- END UPDATES ---

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = onCreateClick) {
                Text("Create")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancelClick) {
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
        modifier = Modifier.padding(top = 8.dp)
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