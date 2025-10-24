package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.ConnectionPair
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.EdgeCreationState
import com.tau.cryptic_ui_v0.SchemaEdge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEdgeView(
    edgeCreationState: EdgeCreationState,
    onSchemaSelected: (SchemaEdge) -> Unit,
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
                value = edgeCreationState.selectedSchema?.label ?: "Select Schema",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = schemaExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = schemaExpanded,
                onDismissRequest = { schemaExpanded = false }
            ) {
                edgeCreationState.schemas.forEach { schema ->
                    DropdownMenuItem(
                        text = { Text(schema.label) },
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
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = connectionExpanded,
                    onDismissRequest = { connectionExpanded = false }
                ) {
                    // Populate from the selected schema connection list
                    edgeCreationState.selectedSchema.connections.forEach { connection ->
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
                    value = edgeCreationState.src?.let { "${it.label} : ${it.primarykeyProperty.value}" } ?: "Select Source Node",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = srcExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().padding(top = 8.dp)
                )
                ExposedDropdownMenu(
                    expanded = srcExpanded,
                    onDismissRequest = { srcExpanded = false }
                ) {
                    // Filter nodes based on selected connection's src
                    edgeCreationState.availableNodes.filter { it.label == edgeCreationState.selectedConnection.src }.forEach { node ->
                        DropdownMenuItem(
                            text = { Text("${node.label} : ${node.primarykeyProperty.value}") },
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
                    value = edgeCreationState.dst?.let { "${it.label} : ${it.primarykeyProperty.value}" } ?: "Select Destination Node",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dstExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().padding(top = 8.dp)
                )
                ExposedDropdownMenu(
                    expanded = dstExpanded,
                    onDismissRequest = { dstExpanded = false }
                ) {
                    // Filter nodes based on selected connection's dst
                    edgeCreationState.availableNodes.filter { it.label == edgeCreationState.selectedConnection.dst }.forEach { node ->
                        DropdownMenuItem(
                            text = { Text("${node.label} : ${node.primarykeyProperty.value}") },
                            onClick = {
                                onDstSelected(node)
                                dstExpanded = false
                            }
                        )
                    }
                }
            }

            // Properties
            edgeCreationState.selectedSchema?.properties?.forEach { property ->
                OutlinedTextField(
                    value = edgeCreationState.properties[property.key] ?: "",
                    onValueChange = { onPropertyChanged(property.key, it) },
                    label = { Text("${property.key}: ${property.valueDataType}") },
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