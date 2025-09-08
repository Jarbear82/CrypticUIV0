// CrypticUIV0/composeApp/src/jvmMain/kotlin/com/tau/cryptic_ui_v0/TerminalView.kt
package com.tau.cryptic_ui_v0

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    val schema by viewModel.schema.collectAsState()
    val queryResult by viewModel.queryResult.collectAsState()
    val metaData by viewModel.dbMetaData.collectAsState()
    val query by viewModel.query
    val scope = rememberCoroutineScope() // Get a coroutine scope

    // Collect the state for the MetadataView
    val nodes by viewModel.nodeList.collectAsState()
    val relationships by viewModel.relationshipList.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()


    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ------------------ Left panel for controls and query results --------------------------

            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch { // Launch a coroutine to call the suspend function
                                viewModel.showSchema()
                            }
                        }) {
                            Text("Show Schema")
                        }
                        Button(onClick = { viewModel.listNodes() }) {
                            Text("List Nodes")
                        }
                        Button(onClick = { viewModel.listEdges() }) {
                            Text("List Rels")
                        }
                        Button(onClick = { viewModel.listAll() }) {
                            Text("List All")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        label = { Text("Cypher Query") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.executeQuery() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Execute")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // --------------- Right panel for Schema and Metadata tabs ------------------------------

            Column(modifier = Modifier.width(400.dp).padding(16.dp)) {
                var tabIndex by remember { mutableStateOf(0) }
                val tabs = listOf("Metadata", "Schema", "Selected Item")

                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title) },
                            selected = tabIndex == index,
                            onClick = { tabIndex = index }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (tabs[tabIndex]) {
                    "Metadata" -> MetadataView(
                        dbMetaData = metaData,
                        nodes = nodes,
                        relationships = relationships,
                        onNodeClick = { viewModel.selectItem(it) },
                        onRelationshipClick = { viewModel.selectItem(it) },
                        onDeleteNodeClick = { viewModel.deleteDisplayItem(it) },
                        onDeleteRelClick = { viewModel.deleteDisplayItem(it) }
                    )
                    "Schema" -> SchemaView(schema)
                    "Selected Item" -> SelectedItemView(
                        selectedItem = selectedItem,
                        onClearSelection = { viewModel.clearSelectedItem() }
                    )
                }
            }
        }

        queryResult?.let {
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            ) {
                QueryView(it) {
                    viewModel.clearQueryResult()
                }
            }
        }
    }
}