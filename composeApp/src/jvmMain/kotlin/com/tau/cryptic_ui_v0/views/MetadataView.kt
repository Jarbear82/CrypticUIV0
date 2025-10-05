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
import com.tau.cryptic_ui_v0.EdgeDisplayItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataView(
    dbMetaData: DBMetaData?,
    nodes: List<NodeDisplayItem>,
    edges: List<EdgeDisplayItem>,
    onNodeClick: (NodeDisplayItem) -> Unit,
    onEdgeClick: (EdgeDisplayItem) -> Unit,
    onDeleteNodeClick: (NodeDisplayItem) -> Unit,
    onDeleteEdgeClick: (EdgeDisplayItem) -> Unit,
    onAddNodeClick: () -> Unit,
    onAddEdgeClick: () -> Unit
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

            // Edges List
            Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Link, contentDescription = "Link")},
                    headlineContent = { Text("Edges:", style = MaterialTheme.typography.headlineSmall) } ,
                    trailingContent = {
                        IconButton(onClick = onAddEdgeClick) {
                            Icon(Icons.Default.Add, contentDescription = "New Edge")
                        }
                    }
                )
                HorizontalDivider(color = Color.Black)
                LazyColumn {
                    items(edges) { edge ->
                        ListItem(
                            headlineContent = { Column {
                                Text("Src: (${edge.src.label} : ${edge.src.primarykeyProperty.value})", style= MaterialTheme.typography.bodySmall)
                                Text("[${edge.label}]", style=MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                                Text("Dst: (${edge.dst.label} : ${edge.dst.primarykeyProperty.value})", style= MaterialTheme.typography.bodySmall)
                            }},
                            // supportingContent = { Text(edge.) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEdgeClick(edge) },
                            trailingContent = {
                                IconButton(onClick = { onDeleteEdgeClick(edge) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Edge")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}