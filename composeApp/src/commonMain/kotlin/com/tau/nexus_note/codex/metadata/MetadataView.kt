package com.tau.nexus_note.codex.metadata // UPDATED: Changed package to match your new structure

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.EdgeDisplayItem
import com.tau.nexus_note.NodeDisplayItem
import com.tau.nexus_note.utils.labelToColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataView(
    nodes: List<NodeDisplayItem>,
    edges: List<EdgeDisplayItem>,
    primarySelectedItem: Any?,
    secondarySelectedItem: Any?,
    onNodeClick: (NodeDisplayItem) -> Unit,
    onEdgeClick: (EdgeDisplayItem) -> Unit,
    onEditNodeClick: (NodeDisplayItem) -> Unit,
    onEditEdgeClick: (EdgeDisplayItem) -> Unit,
    onDeleteNodeClick: (NodeDisplayItem) -> Unit,
    onDeleteEdgeClick: (EdgeDisplayItem) -> Unit,
    onAddNodeClick: () -> Unit,
    onAddEdgeClick: () -> Unit,
    // ADDED: Refresh handlers
    onListAllClick: () -> Unit,
    onListNodesClick: () -> Unit,
    onListEdgesClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {

        // --- Selection Details ---
        Text("Selection Details", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        val primaryNode = primarySelectedItem as? NodeDisplayItem
        val secondaryNode = secondarySelectedItem as? NodeDisplayItem

        if (primaryNode != null && secondaryNode != null) {
            Text(
                "Source: ${primaryNode.label} : ${primaryNode.displayProperty}\n" +
                        "Destination: ${secondaryNode.label} : ${secondaryNode.displayProperty}",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (primaryNode != null) {
            Text(
                "Selected Node:\n" +
                        "${primaryNode.label} : ${primaryNode.displayProperty}",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (primarySelectedItem != null) {
            Text(
                "Selected Item: $primarySelectedItem",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text("No item selected.", style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // --- Data Refresh ---
        Text("Data Refresh", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onListAllClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("List All Nodes & Edges")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onListNodesClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("List Nodes")
            }
            Button(
                onClick = onListEdgesClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("List Edges")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // --- Node and Edge Lists (from original design) ---
        Column(modifier = Modifier.fillMaxSize()) {
            // Nodes List
            Column(modifier = Modifier.weight(1f)) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Hub, contentDescription = "Node")},
                    headlineContent = { Text("Nodes:", style = MaterialTheme.typography.headlineSmall) } ,
                    trailingContent = {
                        IconButton(onClick = onAddNodeClick) {
                            Icon(Icons.Default.Add, contentDescription = "New Node")
                        }
                    },
                )
                HorizontalDivider(color = Color.Black)
                LazyColumn {
                    items(nodes, key = { it.id }) { node ->
                        val isSelected = primarySelectedItem == node || secondarySelectedItem == node
                        val colorInfo = labelToColor(node.label)
                        ListItem(
                            // UPDATED: Use displayProperty
                            headlineContent = { Text("${node.label} : ${node.displayProperty}") },
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
                            },
                            colors = ListItemDefaults.colors( // Apply colors
                                containerColor = colorInfo.composeColor,
                                headlineColor = colorInfo.composeFontColor,
                                leadingIconColor = colorInfo.composeFontColor,
                                trailingIconColor = colorInfo.composeFontColor
                            )
                        )
                    }
                }
            }

            // Edges List
            Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Link, contentDescription = "Link")},
                    headlineContent = { Text("Edges:", style = MaterialTheme.typography.headlineSmall) } ,
                    trailingContent = {
                        IconButton(onClick = onAddEdgeClick) {
                            Icon(Icons.Default.Add, contentDescription = "New Edge")
                        }
                    },
                )
                HorizontalDivider(color = Color.Black)
                LazyColumn {
                    items(edges, key = { it.id }) { edge ->
                        // An edge is "selected" if its src and dst nodes are the selected items
                        val isSelected = primarySelectedItem == edge.src && secondarySelectedItem == edge.dst
                        // Get color based on the *edge* label
                        val colorInfo = labelToColor(edge.label)

                        ListItem(
                            headlineContent = { Column {
                                // UPDATED: Use displayProperty
                                Text(
                                    "Src: (${edge.src.label} : ${edge.src.displayProperty})",
                                    style= MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "[${edge.label}]",
                                    style=MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center
                                )
                                // UPDATED: Use displayProperty
                                Text(
                                    "Dst: (${edge.dst.label} : ${edge.dst.displayProperty})",
                                    style= MaterialTheme.typography.bodySmall
                                )
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

                            },
                            colors = ListItemDefaults.colors(
                                // Apply colors based on edge label
                                containerColor = colorInfo.composeColor,
                                headlineColor = colorInfo.composeFontColor,
                                leadingIconColor = colorInfo.composeFontColor,
                                trailingIconColor = colorInfo.composeFontColor
                            )
                        )
                    }
                }
            }
        }
    }
}
