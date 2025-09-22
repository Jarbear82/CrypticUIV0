package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.DBMetaData
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.RelDisplayItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataView(
    dbMetaData: DBMetaData?,
    nodes: List<NodeDisplayItem>,
    relationships: List<RelDisplayItem>,
    onNodeClick: (NodeDisplayItem) -> Unit,
    onRelationshipClick: (RelDisplayItem) -> Unit,
    onDeleteNodeClick: (NodeDisplayItem) -> Unit,
    onDeleteRelClick: (RelDisplayItem) -> Unit,
    onAddNodeClick: () -> Unit,
    onAddRelClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        dbMetaData?.let {
            ListItem(
                leadingContent = { Icon(imageVector = Icons.Default.Storage, "Database") },
                headlineContent = { Text("Database Info", style = MaterialTheme.typography.headlineSmall) },
                supportingContent = {
                    Text("Name: ${it.name}\n" +
                            "Kuzu Version: ${it.version}\n" +
                            "Storage: ${it.storage}"
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
        Column(modifier = Modifier.fillMaxSize()) {
            // Nodes List
            Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Hub, contentDescription = "Node")},
                    headlineContent = { Text("Nodes:", style = MaterialTheme.typography.headlineSmall) } ,
                    trailingContent = {
                        IconButton(onClick = onAddNodeClick) {
                            Icon(Icons.Default.Add, contentDescription = "New Node")
                        }
                    }
                )
                HorizontalDivider(color = Color.Black)
                LazyColumn {
                    items(nodes) { node ->
                        ListItem(
                            headlineContent = { Text("${node.label} : ${node.primarykeyProperty.value}") },
                            // supportingContent = { Text(node.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNodeClick(node); println("${node.label} clicked!") },
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
                ListItem(
                    leadingContent = { Icon(Icons.Default.Link, contentDescription = "Link")},
                    headlineContent = { Text("Edges:", style = MaterialTheme.typography.headlineSmall) } ,
                    trailingContent = {
                        IconButton(onClick = onAddRelClick) {
                            Icon(Icons.Default.Add, contentDescription = "New Edge")
                        }
                    }
                )
                HorizontalDivider(color = Color.Black)
                LazyColumn {
                    items(relationships) { rel ->
                        ListItem(
                            headlineContent = { Column {
                                Text("Src: (${rel.src.label} : ${rel.src.primarykeyProperty.value})", style= MaterialTheme.typography.bodySmall)
                                Text("[${rel.label}]", style=MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                                Text("Dst: (${rel.dst.label} : ${rel.dst.primarykeyProperty.value})", style= MaterialTheme.typography.bodySmall)
                            }},
                            // supportingContent = { Text(rel.) },
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