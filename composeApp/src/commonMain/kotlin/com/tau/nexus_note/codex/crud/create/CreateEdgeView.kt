package com.tau.nexus_note.codex.crud.create // UPDATED: Package name

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.EdgeCreationState
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.SchemaDefinitionItem


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEdgeView(
    edgeCreationState: EdgeCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onConnectionSelected: (ConnectionPair) -> Unit,
    onSrcSelected: (NodeDisplayItem) -> Unit,
    onDstSelected: (NodeDisplayItem) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    var schemaExpanded by remember { mutableStateOf(false) }
    var connectionExpanded by remember { mutableStateOf(false) }
    var srcExpanded by remember { mutableStateOf(false) }
    var dstExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(8.dp)) {
        Text("Create Edge", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // Schema Dropdown
        ExposedDropdownMenuBox(
            expanded = schemaExpanded,
            onExpandedChange = { schemaExpanded = !schemaExpanded }
        ) {
            OutlinedTextField(
                value = edgeCreationState.selectedSchema?.name ?: "Select Schema", // UPDATED: Use .name
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = schemaExpanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = schemaExpanded,
                onDismissRequest = { schemaExpanded = false }
            ) {
                edgeCreationState.schemas.forEach { schema ->
                    DropdownMenuItem(
                        text = { Text(schema.name) }, // UPDATED: Use .name
                        onClick = {
                            onSchemaSelected(schema)
                            schemaExpanded = false
                        }
                    )
                }
            }
        }

        // --- NEW Connection Pair Dropdown ---
        // Only show this after schema is selected
        edgeCreationState.selectedSchema?.let {
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = connectionExpanded,
                onExpandedChange = { connectionExpanded = !connectionExpanded }
            ) {
                OutlinedTextField(
                    // Display selected connection
                    value = edgeCreationState.selectedConnection?.let { "${it.src} -> ${it.dst}" } ?: "Select Connection Type",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = connectionExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = connectionExpanded,
                    onDismissRequest = { connectionExpanded = false }
                ) {
                    // UPDATED: Populate from the selected schema (nullable) connection list
                    (edgeCreationState.selectedSchema.connections ?: emptyList()).forEach { connection ->
                        DropdownMenuItem(
                            text = { Text("${connection.src} -> ${connection.dst}") },
                            onClick = {
                                onConnectionSelected(connection)
                                connectionExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // --- Source/Destination/Properties ---
        // Only show these after connection type is selected
        edgeCreationState.selectedConnection?.let {
            // Source Node Dropdown
            ExposedDropdownMenuBox(
                expanded = srcExpanded,
                onExpandedChange = { srcExpanded = !srcExpanded }
            ) {
                OutlinedTextField(
                    // UPDATED: Use displayProperty
                    value = edgeCreationState.src?.let { "${it.label} : ${it.displayProperty}" } ?: "Select Source Node",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = srcExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth().padding(top = 8.dp)
                )
                ExposedDropdownMenu(
                    expanded = srcExpanded,
                    onDismissRequest = { srcExpanded = false }
                ) {
                    // Filter nodes based on selected connection's src
                    edgeCreationState.availableNodes.filter { it.label == edgeCreationState.selectedConnection.src }.forEach { node ->
                        DropdownMenuItem(
                            // UPDATED: Use displayProperty
                            text = { Text("${node.label} : ${node.displayProperty}") },
                            onClick = {
                                onSrcSelected(node)
                                srcExpanded = false
                            }
                        )
                    }
                }
            }

            // Destination Node Dropdown
            ExposedDropdownMenuBox(
                expanded = dstExpanded,
                onExpandedChange = { dstExpanded = !dstExpanded }
            ) {
                OutlinedTextField(
                    // UPDATED: Use displayProperty
                    value = edgeCreationState.dst?.let { "${it.label} : ${it.displayProperty}" } ?: "Select Destination Node",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dstExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth().padding(top = 8.dp)
                )
                ExposedDropdownMenu(
                    expanded = dstExpanded,
                    onDismissRequest = { dstExpanded = false }
                ) {
                    // Filter nodes based on selected connection's dst
                    edgeCreationState.availableNodes.filter { it.label == edgeCreationState.selectedConnection.dst }.forEach { node ->
                        DropdownMenuItem(
                            // UPDATED: Use displayProperty
                            text = { Text("${node.label} : ${node.displayProperty}") },
                            onClick = {
                                onDstSelected(node)
                                dstExpanded = false
                            }
                        )
                    }
                }
            }

            // Properties
            // UPDATED: Iterate over properties from selectedSchema
            edgeCreationState.selectedSchema?.properties?.forEach { property ->
                OutlinedTextField(
                    value = edgeCreationState.properties[property.name] ?: "", // UPDATED: Use property.name
                    onValueChange = { onPropertyChanged(property.name, it) }, // UPDATED: Use property.name
                    label = { Text("${property.name}: ${property.type}") }, // UPDATED: Use property.name and .type
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Create/Cancel Buttons
        Row {
            Button(onClick = onCreateClick, enabled = edgeCreationState.src != null && edgeCreationState.dst != null) {
                Text("Create")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancelClick) {
                Text("Cancel")
            }
        }
    }
}
