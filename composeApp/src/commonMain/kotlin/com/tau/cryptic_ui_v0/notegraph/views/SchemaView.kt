package com.tau.cryptic_ui_v0.notegraph.views // UPDATED: Changed package to match your new structure

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.viewmodels.SchemaData
import com.tau.cryptic_ui_v0.SchemaDefinitionItem
import com.tau.cryptic_ui_v0.notegraph.views.labelToColor
@Composable
fun SchemaView(
    schema: SchemaData?,
    primarySelectedItem: Any?,
    secondarySelectedItem: Any?,
    // UPDATED: All parameters use the new SchemaDefinitionItem
    onNodeClick: (SchemaDefinitionItem) -> Unit,
    onEdgeClick: (SchemaDefinitionItem) -> Unit,
    onEditNodeClick: (SchemaDefinitionItem) -> Unit,
    onEditEdgeClick: (SchemaDefinitionItem) -> Unit,
    onDeleteNodeClick: (SchemaDefinitionItem) -> Unit,
    onDeleteEdgeClick: (SchemaDefinitionItem) -> Unit,
    onAddNodeSchemaClick: () -> Unit,
    onAddEdgeSchemaClick: () -> Unit,
    // --- ADDED ---
    onClusterClick: (SchemaDefinitionItem) -> Unit,
    onEditClusterClick: (SchemaDefinitionItem) -> Unit,
    onDeleteClusterClick: (SchemaDefinitionItem) -> Unit,
    onAddClusterSchemaClick: () -> Unit
    // --- END ADDED ---
) {
    if (schema == null) {
        Text("Schema not loaded.")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
            // --- Node Schemas ---
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Node Schema")},
                headlineContent = { Text("Node Schemas:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) } , // MODIFIED: smaller title
                trailingContent = {
                    IconButton(onClick = onAddNodeSchemaClick) {
                        Icon(Icons.Default.Add, contentDescription = "New Node Schema")
                    }
                }
            )

            HorizontalDivider(color = Color.Black)
            LazyColumn {
                // UPDATED: Use schema.nodeSchemas
                items(schema.nodeSchemas, key = { it.id }) { table ->
                    val isSelected = primarySelectedItem == table
                    // UPDATED: Use table.name
                    val colorInfo = labelToColor(table.name)
                    ListItem(
                        // UPDATED: Use table.name
                        headlineContent = { Text(table.name, style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clickable { onNodeClick(table) }
                            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier),
                        leadingContent = {
                            IconButton(onClick = { onEditNodeClick(table) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Node Schema")
                            }
                        },
                        // UPDATED: Use new SchemaProperty fields
                        supportingContent = {
                            Text(
                                text = table.properties.joinToString(separator = "\n") { prop ->
                                    val suffix = if (prop.isDisplayProperty) ": (Display)" else ""
                                    "  - ${prop.name}: ${prop.type}$suffix"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteNodeClick(table) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Node Schema")
                            }
                        },
                        colors = ListItemDefaults.colors( // Apply colors
                            containerColor = colorInfo.composeColor,
                            headlineColor = colorInfo.composeFontColor,
                            supportingColor = colorInfo.composeFontColor,
                            leadingIconColor = colorInfo.composeFontColor,
                            trailingIconColor = colorInfo.composeFontColor
                        )
                    )
                }
            }
        }

        // --- ADDED: Cluster Schemas ---
        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
            ListItem(
                leadingContent = { Icon(Icons.Default.Workspaces, contentDescription = "Cluster Schema")},
                headlineContent = { Text("Cluster Schemas:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) } ,
                trailingContent = {
                    IconButton(onClick = onAddClusterSchemaClick) {
                        Icon(Icons.Default.Add, contentDescription = "New Cluster Schema")
                    }
                }
            )
            HorizontalDivider(color = Color.Black)
            LazyColumn {
                items(schema.clusterSchemas, key = { it.id }) { table ->
                    val isSelected = primarySelectedItem == table
                    val colorInfo = labelToColor(table.name)
                    ListItem(
                        headlineContent = { Text(table.name, style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clickable { onClusterClick(table) }
                            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier),
                        leadingContent = {
                            IconButton(onClick = { onEditClusterClick(table) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Cluster Schema")
                            }
                        },
                        supportingContent = {
                            Text(
                                text = table.properties.joinToString(separator = "\n") { prop ->
                                    val suffix = if (prop.isDisplayProperty) ": (Display)" else ""
                                    "  - ${prop.name}: ${prop.type}$suffix"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteClusterClick(table) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Cluster Schema")
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = colorInfo.composeColor,
                            headlineColor = colorInfo.composeFontColor,
                            supportingColor = colorInfo.composeFontColor,
                            leadingIconColor = colorInfo.composeFontColor,
                            trailingIconColor = colorInfo.composeFontColor
                        )
                    )
                }
            }
        }
        // --- END ADDED ---

        // --- Edge Schemas ---
        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
            ListItem(
                leadingContent = { Icon(Icons.Default.Timeline, contentDescription = "Edge Schema")}, // Fixed content description
                headlineContent = { Text("Edge Schemas:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) } , // MODIFIED: smaller title
                trailingContent = {
                    IconButton(onClick = onAddEdgeSchemaClick) {
                        Icon(Icons.Default.Add, contentDescription = "New Edge Schema")
                    }
                }
            )
            HorizontalDivider(color = Color.Black)
            LazyColumn {
                // UPDATED: Use schema.edgeSchemas
                items(schema.edgeSchemas, key = { it.id }) { table ->
                    val isSelected = primarySelectedItem == table
                    // UPDATED: Use table.name
                    val colorInfo = labelToColor(table.name)
                    ListItem(
                        // UPDATED: Use table.name
                        headlineContent = { Text(table.name, style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clickable { onEdgeClick(table) }
                            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier),
                        leadingContent = {
                            IconButton(onClick = { onEditEdgeClick(table) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Edge Schema")
                            }
                        },
                        // UPDATED: Use table.connections (nullable)
                        supportingContent = {
                            Column {
                                (table.connections ?: emptyList()).forEach {
                                    Text("(${it.src}) -> (${it.dst})", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteEdgeClick(table) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Edge Schema")
                            }
                        },
                        colors = ListItemDefaults.colors(
                            // Apply colors based on edge label
                            containerColor = colorInfo.composeColor,
                            headlineColor = colorInfo.composeFontColor,
                            supportingColor = colorInfo.composeFontColor,
                            leadingIconColor = colorInfo.composeFontColor,
                            trailingIconColor = colorInfo.composeFontColor
                        )
                    )
                }
            }
        }
    }
}
