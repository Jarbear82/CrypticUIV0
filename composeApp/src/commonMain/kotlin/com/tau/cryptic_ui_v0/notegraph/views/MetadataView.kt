package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.DBMetaData
import com.tau.cryptic_ui_v0.NodeDisplayItem

@Composable
fun MetadataView(
    dbMetaData: DBMetaData?,
    primarySelectedItem: Any?,
    secondarySelectedItem: Any?,
    query: String,
    onQueryChange: (String) -> Unit,
    onExecuteQuery: () -> Unit,
    // --- ADDED PARAMETERS ---
    onListAll: () -> Unit,
    onListNodes: () -> Unit,
    onListEdges: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()) // Make the whole panel scrollable
    ) {
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

        // Display selected items
        val primaryNode = primarySelectedItem as? NodeDisplayItem
        val secondaryNode = secondarySelectedItem as? NodeDisplayItem

        Text("Selection Details", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        if (primaryNode != null && secondaryNode != null) {
            ListItem(
                headlineContent = {
                    Text("Selected Nodes (Source -> Destination):")
                },
                supportingContent = {
                    Text("Source: ${primaryNode.label} : ${primaryNode.primarykeyProperty.value}\n" +
                            "Destination: ${secondaryNode.label} : ${secondaryNode.primarykeyProperty.value}")
                }
            )
        } else if (primaryNode != null) {
            ListItem(
                headlineContent = {
                    Text("Selected Node:")
                },
                supportingContent = {
                    Text("${primaryNode.label} : ${primaryNode.primarykeyProperty.value}")
                }
            )
        } else if (primarySelectedItem != null) {
            ListItem(
                headlineContent = {
                    Text("Selected Item:")
                },
                supportingContent = {
                    Text("$primarySelectedItem", style = MaterialTheme.typography.bodyMedium)
                }
            )
        } else {
            Text("No item selected.", modifier = Modifier.padding(start = 16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- NEW DATA REFRESH SECTION ---
        Text("Data Refresh", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onListAll,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("List All Nodes & Edges")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onListNodes,
                modifier = Modifier.weight(1f)
            ) {
                Text("List Nodes")
            }
            OutlinedButton(
                onClick = onListEdges,
                modifier = Modifier.weight(1f)
            ) {
                Text("List Edges")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        // --- END NEW SECTION ---


        // --- QUERY BOX ---
        Text("Cypher Query", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Enter Cypher Query") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 10 // Allow internal scrolling after 10 lines
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onExecuteQuery,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Execute")
        }
        // --- END QUERY BOX ---
    }
}