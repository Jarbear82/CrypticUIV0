package com.tau.nexus_note.views

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.codex.SearchableListHeader
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.utils.labelToColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListView(
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
    nodeSearchText: String,
    onNodeSearchChange: (String) -> Unit,
    edgeSearchText: String,
    onEdgeSearchChange: (String) -> Unit,
    nodeVisibility: Map<Long, Boolean>,
    onToggleNodeVisibility: (Long) -> Unit,
    edgeVisibility: Map<Long, Boolean>,
    onToggleEdgeVisibility: (Long) -> Unit
) {
    // User requested "side by side lists"
    Row(modifier = Modifier.fillMaxSize()) {
        // --- Nodes List ---
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            SearchableListHeader(
                title = "Nodes:",
                searchText = nodeSearchText,
                onSearchTextChange = onNodeSearchChange,
                onAddClick = onAddNodeClick,
                addContentDescription = "New Node",
                leadingContent = { Icon(Icons.Default.Hub, contentDescription = "Node")},
            )
            HorizontalDivider(color = Color.Black)
            LazyColumn {
                val filteredNodes = nodes.filter {
                    it.label.contains(nodeSearchText, ignoreCase = true) ||
                            it.displayProperty.contains(nodeSearchText, ignoreCase = true)
                }
                items(filteredNodes) { node ->
                    val isSelected = primarySelectedItem == node || secondarySelectedItem == node
                    val colorInfo = labelToColor(node.label)
                    ListItem(
                        headlineContent = { Text("${node.label} : ${node.displayProperty}", style = MaterialTheme.typography.bodyMedium) }, // Smaller text
                        leadingContent = {
                            IconButton(onClick = { onToggleNodeVisibility(node.id) }) {
                                val isVisible = nodeVisibility[node.id] ?: true
                                Icon(
                                    imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isVisible) "Hide Node" else "Show Node"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNodeClick(node); println("${node.label} clicked!") }
                            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier),
                        trailingContent = {
                            Row{
                                IconButton(onClick = { onEditNodeClick(node) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Node")
                                }
                                IconButton(onClick = { onDeleteNodeClick(node) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Node")
                                }
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

        // --- Edges List ---
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            SearchableListHeader(
                title = "Edges:",
                searchText = edgeSearchText,
                onSearchTextChange = onEdgeSearchChange,
                onAddClick = onAddEdgeClick,
                addContentDescription = "New Edge",
                leadingContent = { Icon(Icons.Default.Link, contentDescription = "Link")},
            )
            HorizontalDivider(color = Color.Black)
            LazyColumn {
                // --- UPDATED: Filter the list ---
                val filteredEdges = edges.filter {
                    it.label.contains(edgeSearchText, ignoreCase = true) ||
                            it.src.displayProperty.contains(edgeSearchText, ignoreCase = true) ||
                            it.dst.displayProperty.contains(edgeSearchText, ignoreCase = true)
                }
                items(filteredEdges) { edge ->
                    // An edge is "selected" if its src and dst nodes are the selected items
                    val isSelected = primarySelectedItem == edge.src && secondarySelectedItem == edge.dst
                    // Get color based on the *edge* label
                    val colorInfo = labelToColor(edge.label)

                    ListItem(
                        headlineContent = { Column {
                            Text(
                                "Src: (${edge.src.label} : ${edge.src.displayProperty})",
                                style= MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "[${edge.label}]",
                                style=MaterialTheme.typography.titleMedium, // Smaller text
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Dst: (${edge.dst.label} : ${edge.dst.displayProperty})",
                                style= MaterialTheme.typography.bodySmall
                            )
                        }},
                        leadingContent = {
                            IconButton(onClick = { onToggleEdgeVisibility(edge.id) }) {
                                val isVisible = edgeVisibility[edge.id] ?: true
                                Icon(
                                    imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isVisible) "Hide Edge" else "Show Edge"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdgeClick(edge) }
                            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier),
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onEditEdgeClick(edge) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Edge")
                                }
                                IconButton(onClick = { onDeleteEdgeClick(edge) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Edge")
                                }
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