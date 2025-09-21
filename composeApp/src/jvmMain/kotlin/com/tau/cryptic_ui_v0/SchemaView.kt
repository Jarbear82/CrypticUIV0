package com.tau.cryptic_ui_v0

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
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

@Composable
fun SchemaView(
    schema: Schema?,
    onNodeClick: (SchemaNode) -> Unit,
    onRelationshipClick: (SchemaRel) -> Unit,
    onDeleteNodeClick: (SchemaNode) -> Unit,
    onDeleteRelClick: (SchemaRel) -> Unit,
    onAddNodeSchemaClick: () -> Unit
) {
    if (schema == null) {
        Text("Schema not loaded.")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
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
                    ListItem(
                        headlineContent = { Text(table.label, style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier.padding(bottom = 8.dp).clickable { onNodeClick(table) },
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
                        }
                    )
                }
            }
        }



        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
            ListItem(
                leadingContent = { Icon(Icons.Default.Timeline, contentDescription = "Node")},
                headlineContent = { Text("Edge Schemas:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) } ,
                trailingContent = { Icon(Icons.Default.Add, contentDescription = "New Node") }
            )
            HorizontalDivider(color = Color.Black)
            LazyColumn {
                items(schema.relTables) { table ->
                    ListItem(
                        headlineContent = { Text("${table.label}", style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier.padding(bottom = 8.dp).clickable { onRelationshipClick(table) },
                        supportingContent = { Text("(${table.srcLabel} -> ${table.dstLabel})")},
                        trailingContent = {
                            IconButton(onClick = { onDeleteRelClick(table) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Relationship Schema")
                            }
                        }
                    )
                }
            }
        }
    }
}