// CrypticUIV0/composeApp/src/jvmMain/kotlin/com/tau/cryptic_ui_v0/TerminalView.kt
package com.tau.cryptic_ui_v0

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

    // Tabs
    var selectedTab by remember { mutableStateOf(TerminalViewTabs.METADATA) }

    fun selectMetadata() { selectedTab = TerminalViewTabs.METADATA }
    fun selectSchema() { selectedTab = TerminalViewTabs.SCHEMA }
    fun selectSelected() { selectedTab = TerminalViewTabs.SELECTED }


    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ------------------ Left panel for controls and query results --------------------------

            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            Button(onClick = {
                                scope.launch { // Launch a coroutine to call the suspend function
                                    viewModel.showSchema()
                                }
                            }) {
                                Text("Show Schema")
                            }
                        }

                        item {
                            Button(onClick = { viewModel.listNodes() }) {
                                Text("List Nodes")
                            }
                        }

                        item {
                            Button(onClick = { viewModel.listEdges() }) {
                                Text("List Rels")
                            }
                        }

                        item {
                            Button(onClick = { viewModel.listAll() }) {
                                Text("List All")
                            }
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


                TabRow(selectedTabIndex = selectedTab.value) {
                    TerminalViewTabs.entries.forEach { tab ->
                        Tab(
                            text = { Text(tab.name) },
                            selected = selectedTab.value == tab.value,
                            onClick = { selectedTab = tab }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    TerminalViewTabs.METADATA -> MetadataView(
                        dbMetaData = metaData,
                        nodes = nodes,
                        relationships = relationships,
                        onNodeClick = { viewModel.selectItem(it); println("Item: $it is being selected"); selectSelected() },
                        onRelationshipClick = { viewModel.selectItem(it); selectSelected() },
                        onDeleteNodeClick = { viewModel.deleteDisplayItem(it) },
                        onDeleteRelClick = { viewModel.deleteDisplayItem(it) }
                    )
                    TerminalViewTabs.SCHEMA -> SchemaView(schema)
                    TerminalViewTabs.SELECTED -> SelectedItemView(
                        selectedItem = selectedItem,
                        onClearSelection = { viewModel.clearSelectedItem(); selectMetadata() }
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



enum class TerminalViewTabs(val value: Int) {
    METADATA(0),
    SCHEMA(1),
    SELECTED(2)
}

