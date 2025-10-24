package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.tau.cryptic_ui_v0.Schema
import com.tau.cryptic_ui_v0.SchemaNode
import com.tau.cryptic_ui_v0.SchemaEdge

@Composable
fun SchemaView(
    schema: Schema?,
    primarySelectedItem: Any?,
    secondarySelectedItem: Any?,
    onNodeClick: (SchemaNode) -> Unit,
    onEdgeClick: (SchemaEdge) -> Unit,
    onEditNodeClick: (SchemaNode) -> Unit,
    onEditEdgeClick: (SchemaEdge) -> Unit,
    onDeleteNodeClick: (SchemaNode) -> Unit,
    onDeleteEdgeClick: (SchemaEdge) -> Unit,
    onAddNodeSchemaClick: () -> Unit,
    onAddEdgeSchemaClick: () -> Unit
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
                headlineContent = { Text("Node Schemas:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp)) } ,
                trailingContent = {
                    IconButton(onClick = onAddNodeSchemaClick) {
                        Icon(Icons.Default.Add, contentDescription = "New Node Schema")
                    }
                }
            )

            HorizontalDivider(color = Color.Black)
            LazyColumn {
                items(schema.nodeTables) { table ->
                    val isSelected = primarySelectedItem == table
                    val colorInfo = labelToColor(table.label) // Get color info
                    ListItem(
                        headlineContent = { Text(table.label, style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clickable { onNodeClick(table) }
                            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier),
                        leadingContent = {
                            IconButton(onClick = { onEditNodeClick(table) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Node Schema")
                            }
                        },
                        supportingContent = {
                            Text(
                                text = table.properties.joinToString(separator = "\n") { prop ->
                                    if (prop.isPrimaryKey){
                                        "  - ${prop.key}: ${prop.valueDataType.toString()}: PK"
                                    } else {
                                        "  - ${prop.key}: ${prop.valueDataType.toString()}"
                                    }
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

        // --- Edge Schemas ---
        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
            ListItem(
                leadingContent = { Icon(Icons.Default.Timeline, contentDescription = "Node")},
                headlineContent = { Text("Edge Schemas:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) } ,
                trailingContent = {
                    IconButton(onClick = onAddEdgeSchemaClick) {
                        Icon(Icons.Default.Add, contentDescription = "New Edge Schema")
                    }
                }
            )
            HorizontalDivider(color = Color.Black)
            LazyColumn {
                items(schema.edgeTables) { table ->
                    val isSelected = primarySelectedItem == table
                    // Get color based on the *edge* label
                    val colorInfo = labelToColor(table.label)
                    ListItem(
                        headlineContent = { Text("${table.label}", style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clickable { onEdgeClick(table) }
                            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier),
                        leadingContent = {
                            IconButton(onClick = { onEditEdgeClick(table) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Edge Schema")
                            }
                        },
                        supportingContent = {
                            // Display all connection pairs
                            Column {
                                table.connections.forEach {
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