package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
    selectedItem: Any?,
    onNodeClick: (NodeDisplayItem) -> Unit,
    onEdgeClick: (EdgeDisplayItem) -> Unit,
    onEditNodeClick: (NodeDisplayItem) -> Unit,
    onEditEdgeClick: (EdgeDisplayItem) -> Unit,
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

        selectedItem?.let {
            val text = when(it) {
                is NodeDisplayItem -> "Selected Node: ${it.label} : ${it.primarykeyProperty.value}"
                is EdgeDisplayItem -> "Selected Edge: ${it.label}"
                else -> "Selected: $it"
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
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
                        val isSelected = selectedItem == node
                        ListItem(
                            headlineContent = { Text("${node.label} : ${node.primarykeyProperty.value}") },
                            leadingContent = {
                                IconButton(onClick = { onEditNodeClick(node) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Node")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNodeClick(node); println("${node.label} clicked!") }
                                .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier),
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
                        val isSelected = selectedItem == edge
                        ListItem(
                            headlineContent = { Column {
                                Text("Src: (${edge.src.label} : ${edge.src.primarykeyProperty.value})", style= MaterialTheme.typography.bodySmall)
                                Text("[${edge.label}]", style=MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                                Text("Dst: (${edge.dst.label} : ${edge.dst.primarykeyProperty.value})", style= MaterialTheme.typography.bodySmall)
                            }},
                            leadingContent = {
                                IconButton(onClick = { onEditEdgeClick(edge) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Edge")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEdgeClick(edge) }
                                .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier),
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