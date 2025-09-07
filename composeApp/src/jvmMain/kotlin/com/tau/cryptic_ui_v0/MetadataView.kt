package com.tau.cryptic_ui_v0

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataView(
    dbMetaData: DBMetaData?,
    nodes: List<DisplayItem>,
    relationships: List<RelDisplayItem>,
    onNodeClick: (DisplayItem) -> Unit,
    onRelationshipClick: (RelDisplayItem) -> Unit,
    onDeleteNodeClick: (DisplayItem) -> Unit,
    onDeleteRelClick: (RelDisplayItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        dbMetaData?.let {
            Text("Database Info", style = MaterialTheme.typography.headlineSmall)
            Text("Name: ${it.name}")
            Text("Kuzu Version: ${it.version}")
            Text("Storage: ${it.storage}")
            Spacer(modifier = Modifier.height(16.dp))
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // Nodes List
            Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                Text("Nodes", style = MaterialTheme.typography.headlineSmall)
                LazyColumn {
                    items(nodes) { node ->
                        ListItem(
                            headlineContent = { Text("${node.label} : ${node.primaryKey}") },
                            supportingContent = { Text(node.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNodeClick(node) },
                            trailingContent = {
                                IconButton(onClick = { onDeleteNodeClick(node) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Node")
                                }
                            }
                        )
                    }
                }
            }

            // Relationships List
            Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                Text("Relationships", style = MaterialTheme.typography.headlineSmall)
                LazyColumn {
                    items(relationships) { rel ->
                        ListItem(
                            headlineContent = { Text("(${rel.srcLabel})-[${rel.label}]->(${rel.dstLabel})")},
                            supportingContent = { Text(rel.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRelationshipClick(rel) },
                            trailingContent = {
                                IconButton(onClick = { onDeleteRelClick(rel) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Relationship")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}