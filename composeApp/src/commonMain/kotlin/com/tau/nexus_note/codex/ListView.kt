package com.tau.nexus_note.codex

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.utils.labelToColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListView(
// MODIFIED: Use paginated lists
    paginatedNodes: List<NodeDisplayItem>,
    paginatedEdges: List<EdgeDisplayItem>,
// NEW: Handlers for pagination
    onLoadMoreNodes: () -> Unit,
    onLoadMoreEdges: () -> Unit,
// --- Unchanged props ---
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
// --- NEW: List states for pagination ---
    val nodeLazyListState = rememberLazyListState()
    val edgeLazyListState = rememberLazyListState()

// Derived state to check if we're at the end of the list
    val isAtNodeListEnd by remember {
        derivedStateOf {
            val layoutInfo = nodeLazyListState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) {
                false
            } else {
                val lastVisibleItem = visibleItemsInfo.lastOrNull()
                lastVisibleItem != null && lastVisibleItem.index + 1 == layoutInfo.totalItemsCount
            }
        }
    }

    val isAtEdgeListEnd by remember {
        derivedStateOf {
            val layoutInfo = edgeLazyListState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) {
                false
            } else {
                val lastVisibleItem = visibleItemsInfo.lastOrNull()
                lastVisibleItem != null && lastVisibleItem.index + 1 == layoutInfo.totalItemsCount
            }
        }
    }

// Effect to load more when end is reached
    LaunchedEffect(isAtNodeListEnd) {
        if (isAtNodeListEnd) {
            onLoadMoreNodes()
        }
    }

    LaunchedEffect(isAtEdgeListEnd) {
        if (isAtEdgeListEnd) {
            onLoadMoreEdges()
        }
    }


    Row(modifier = Modifier.fillMaxSize()) {
        // --- Nodes List ---
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            SearchableListHeader(
                title = "Nodes:",
                searchText = nodeSearchText,
                onSearchTextChange = onNodeSearchChange,
                onAddClick = onAddNodeClick,
                addContentDescription = "New Node",
                leadingContent = {
                    Icon(
                        Icons.Default.Hub,
                        contentDescription = "Node",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            LazyColumn(state = nodeLazyListState) { // <-- NEW: Added state
                val filteredNodes = paginatedNodes.filter { // <-- MODIFIED: Use paginatedNodes
                    it.label.contains(nodeSearchText, ignoreCase = true) ||
                            it.displayProperty.contains(nodeSearchText, ignoreCase = true)
                }
                items(filteredNodes, key = { it.id }) { node ->
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
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
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
                leadingContent = {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = "Link",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            LazyColumn(state = edgeLazyListState) {
                val filteredEdges = paginatedEdges.filter {
                    it.label.contains(edgeSearchText, ignoreCase = true) ||
                            it.src.displayProperty.contains(edgeSearchText, ignoreCase = true) ||
                            it.dst.displayProperty.contains(edgeSearchText, ignoreCase = true)
                }
                items(filteredEdges, key = { it.id }) { edge ->
                    val isSelected = primarySelectedItem == edge.src && secondarySelectedItem == edge.dst
                    val colorInfo = labelToColor(edge.label)

                    ListItem(
                        headlineContent = { Column {
                            Text(
                                "Src: (${edge.src.label} : ${edge.src.displayProperty})",
                                style= MaterialTheme.typography.bodySmall,
                                color = colorInfo.composeFontColor.copy(alpha = 0.8f) // Muted
                            )
                            Text(
                                "[${edge.label}]",
                                style=MaterialTheme.typography.titleMedium, // Smaller text
                                textAlign = TextAlign.Center,
                                color = colorInfo.composeFontColor // Main color
                            )
                            Text(
                                "Dst: (${edge.dst.label} : ${edge.dst.displayProperty})",
                                style= MaterialTheme.typography.bodySmall,
                                color = colorInfo.composeFontColor.copy(alpha = 0.8f) // Muted
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
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
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