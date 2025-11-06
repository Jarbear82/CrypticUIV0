package com.tau.nexus_note.codex.metadata

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
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.NodeDisplayItem
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

    }
}
