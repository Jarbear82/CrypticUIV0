package com.tau.cryptic_ui_v0.notegraph.views // UPDATED: Changed package to match your new structure

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.ClusterDisplayItem
import com.tau.cryptic_ui_v0.DBMetaData
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.notegraph.views.labelToColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataView(
    nodes: List<NodeDisplayItem>,
    // --- ADDED ---
    clusters: List<ClusterDisplayItem>,
    // --- END ADDED ---
    edges: List<EdgeDisplayItem>,
    primarySelectedItem: Any?,
    secondarySelectedItem: Any?,
    onNodeClick: (NodeDisplayItem) -> Unit,
    // --- ADDED ---
    onClusterClick: (ClusterDisplayItem) -> Unit,
    // --- END ADDED ---
    onEdgeClick: (EdgeDisplayItem) -> Unit,
    onEditNodeClick: (NodeDisplayItem) -> Unit,
    // --- ADDED ---
    onEditClusterClick: (ClusterDisplayItem) -> Unit,
    // --- END ADDED ---
    onEditEdgeClick: (EdgeDisplayItem) -> Unit,
    onDeleteNodeClick: (NodeDisplayItem) -> Unit,
    // --- ADDED ---
    onDeleteClusterClick: (ClusterDisplayItem) -> Unit,
    // --- END ADDED ---
    onDeleteEdgeClick: (EdgeDisplayItem) -> Unit,
    onAddNodeClick: () -> Unit,
    // --- ADDED ---
    onAddClusterClick: () -> Unit,
    // --- END ADDED ---
    onAddEdgeClick: () -> Unit,
    // ADDED: Refresh handlers
    onListAllClick: () -> Unit,
    onListNodesClick: () -> Unit,
    // --- ADDED ---
    onListClustersClick: () -> Unit,
    // --- END ADDED ---
    onListEdgesClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {

        // --- Selection Details ---
        Text("Selection Details", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // --- MODIFIED: Handle cluster selection ---
        val primaryNode = primarySelectedItem as? NodeDisplayItem
        val secondaryNode = secondarySelectedItem as? NodeDisplayItem
        val primaryCluster = primarySelectedItem as? ClusterDisplayItem

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
        } else if (primaryCluster != null) {
            Text(
                "Selected Cluster:\n" +
                        "${primaryCluster.label} : ${primaryCluster.displayProperty}",
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
        // --- END MODIFIED ---

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // --- Data Refresh ---
        Text("Data Refresh", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onListAllClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh All")
            Spacer(Modifier.width(8.dp))
            Text("List All")
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
            // --- ADDED ---
            Button(
                onClick = onListClustersClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("List Clusters")
            }
            // --- END ADDED ---
            Button(
                onClick = onListEdgesClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("List Edges")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // --- Node, Cluster, and Edge Lists ---
        Column(modifier = Modifier.fillMaxSize()) {
            // Nodes List
            Column(modifier = Modifier.weight(1f)) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Hub, contentDescription = "Node")},
                    headlineContent = { Text("Nodes:", style = MaterialTheme.typography.titleMedium) } , // MODIFIED: smaller title
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

            // --- ADDED: Clusters List ---
            Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Workspaces, contentDescription = "Cluster")},
                    headlineContent = { Text("Clusters:", style = MaterialTheme.typography.titleMedium) } ,
                    trailingContent = {
                        IconButton(onClick = onAddClusterClick) {
                            Icon(Icons.Default.Add, contentDescription = "New Cluster")
                        }
                    },
                )
                HorizontalDivider(color = Color.Black)
                LazyColumn {
                    items(clusters, key = { it.id }) { cluster ->
                        val isSelected = primarySelectedItem == cluster
                        val colorInfo = labelToColor(cluster.label)
                        ListItem(
                            headlineContent = { Text("${cluster.label} : ${cluster.displayProperty}") },
                            leadingContent = {
                                IconButton(onClick = { onEditClusterClick(cluster) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Cluster")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClusterClick(cluster) }
                                .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier),
                            trailingContent = {
                                IconButton(onClick = { onDeleteClusterClick(cluster) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Cluster")
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = colorInfo.composeColor,
                                headlineColor = colorInfo.composeFontColor,
                                leadingIconColor = colorInfo.composeFontColor,
                                trailingIconColor = colorInfo.composeFontColor
                            )
                        )
                    }
                }
            }
            // --- END ADDED ---

            // Edges List
            Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Link, contentDescription = "Link")},
                    headlineContent = { Text("Edges:", style = MaterialTheme.typography.titleMedium) } , // MODIFIED: smaller title
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
                                    style=MaterialTheme.typography.titleMedium, // MODIFIED: smaller title
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
