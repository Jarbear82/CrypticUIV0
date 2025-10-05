package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.EdgeCreationState
import com.tau.cryptic_ui_v0.SchemaEdge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEdgeView(
    edgeCreationState: EdgeCreationState,
    onSchemaSelected: (SchemaEdge) -> Unit,
    onSrcSelected: (NodeDisplayItem) -> Unit,
    onDstSelected: (NodeDisplayItem) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    var schemaExpanded by remember { mutableStateOf(false) }
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

        edgeCreationState.selectedSchema?.let {
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
                    edgeCreationState.availableNodes.filter { it.label == edgeCreationState.selectedSchema.srcLabel }.forEach { node ->
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
                    edgeCreationState.availableNodes.filter { it.label == edgeCreationState.selectedSchema.dstLabel }.forEach { node ->
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
            it.properties.forEach { property ->
                OutlinedTextField(
                    value = edgeCreationState.properties[property.key] ?: "",
                    onValueChange = { onPropertyChanged(property.key, it) },
                    label = { Text("${property.key}: ${property.valueDataType}") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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