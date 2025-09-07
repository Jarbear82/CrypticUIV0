package com.tau.cryptic_ui_v0

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MetadataView(viewModel: TerminalViewModel) {
    val nodes by viewModel.nodeList.collectAsState()
    val relationships by viewModel.relationshipList.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val metaData by viewModel.dbMetaData.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    val tabs = remember(selectedItem) {
        if (selectedItem != null) listOf("Items", "Selected") else listOf("Items")
    }

    LaunchedEffect(selectedItem) {
        if (selectedItem != null) {
            selectedTab = tabs.indexOf("Selected")
        } else {
            selectedTab = 0
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (tabs.size > 1) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }

        when (tabs.getOrNull(selectedTab)) {
            "Items", null -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        metaData?.let {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Database Info",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text("Name: ${it.name}")
                                Text("Version: ${it.version}")
                                Text("Storage: ${it.storage}")
                            }
                        }
                        Divider()
                        Text("Nodes", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                    }
                    items(nodes) { item ->
                        ListItem(
                            headlineContent = { Text("${item.label} : ${item.primaryKey}") },
                            modifier = Modifier.clickable {
                                viewModel.selectItem(item)
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteItem(item) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete")
                                }
                            }
                        )
                    }
                    item {
                        Divider()
                        Text("Relationships", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                    }
                    items(relationships) { item ->
                        ListItem(
                            headlineContent = { Text("${item.label} : ${item.primaryKey}") },
                            modifier = Modifier.clickable {
                                viewModel.selectItem(item)
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteItem(item) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete")
                                }
                            }
                        )
                    }
                }
            }
            "Selected" -> {
                selectedItem?.let { item ->
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Label: ${item.label}", style = MaterialTheme.typography.headlineSmall)
                            IconButton(onClick = { viewModel.clearSelectedItem() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        Text("ID: ${item.id}", style = MaterialTheme.typography.bodyMedium)
                        Text("Primary Key: ${item.primaryKey}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Properties:", style = MaterialTheme.typography.titleMedium)
                        item.properties.forEach { (key, value) ->
                            Text("$key: $value")
                        }
                    }
                }
            }
        }
    }
}