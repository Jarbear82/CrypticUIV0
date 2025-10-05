package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.viewmodels.TerminalViewModel
import com.tau.cryptic_ui_v0.viewmodels.TerminalViewTabs
import kotlinx.coroutines.launch

@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    val schema by viewModel.schemaViewModel.schema.collectAsState()
    val queryResult by viewModel.queryViewModel.queryResult.collectAsState()
    val metaData by viewModel.metadataViewModel.dbMetaData.collectAsState()
    val query by viewModel.queryViewModel.query
    val scope = rememberCoroutineScope() // Get a coroutine scope

    // Collect the state for the MetadataView
    val nodes by viewModel.metadataViewModel.nodeList.collectAsState()
    val edges by viewModel.metadataViewModel.edgeList.collectAsState()
    val selectedItem by viewModel.metadataViewModel.selectedItem.collectAsState()
    val nodeCreationState by viewModel.metadataViewModel.nodeCreationState.collectAsState()
    val edgeCreationState by viewModel.metadataViewModel.edgeCreationState.collectAsState()
    val nodeSchemaCreationState by viewModel.schemaViewModel.nodeSchemaCreationState.collectAsState()
    val edgeSchemaCreationState by viewModel.schemaViewModel.edgeSchemaCreationState.collectAsState()


    // Tabs
    val selectedTab by viewModel.selectedTab.collectAsState()


    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ------------------ Left panel for controls and query results --------------------------

            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                item {


                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(onClick = {
                            scope.launch { // Launch a coroutine to call the suspend function
                                viewModel.schemaViewModel.showSchema()
                            }
                        }) {
                            Text("Show Schema")
                        }

                        Button(onClick = { viewModel.metadataViewModel.listNodes() }) {
                            Text("List Nodes")
                        }

                        Button(onClick = { viewModel.metadataViewModel.listEdges() }) {
                            Text("List Edges")
                        }

                        Button(onClick = { viewModel.metadataViewModel.listAll() }) {
                            Text("List All")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.queryViewModel.onQueryChange(it) },
                        label = { Text("Cypher Query") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.queryViewModel.executeQuery() },
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
                            onClick = { viewModel.selectTab(tab) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    TerminalViewTabs.METADATA -> MetadataView(
                        dbMetaData = metaData,
                        nodes = nodes,
                        edges = edges,
                        onNodeClick = { viewModel.metadataViewModel.selectItem(it); println("Item: $it is being selected"); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onEdgeClick = { viewModel.metadataViewModel.selectItem(it); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onDeleteEdgeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onAddNodeClick = { viewModel.metadataViewModel.initiateNodeCreation(); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onAddEdgeClick = { viewModel.metadataViewModel.initiateEdgeCreation(); viewModel.selectTab(TerminalViewTabs.SELECTED) }
                    )
                    TerminalViewTabs.SCHEMA -> SchemaView(
                        schema = schema,
                        onNodeClick = { viewModel.metadataViewModel.selectItem(it); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onEdgeClick = { viewModel.metadataViewModel.selectItem(it); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onDeleteNodeClick = { viewModel.schemaViewModel.deleteSchemaNode(it) },
                        onDeleteEdgeClick = { viewModel.schemaViewModel.deleteSchemaEdge(it) },
                        onAddNodeSchemaClick = { viewModel.metadataViewModel.initiateNodeSchemaCreation(); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onAddEdgeSchemaClick = { viewModel.metadataViewModel.initiateEdgeSchemaCreation(); viewModel.selectTab(TerminalViewTabs.SELECTED) }
                    )
                    TerminalViewTabs.SELECTED -> SelectedItemView(
                        selectedItem = selectedItem,
                        nodeCreationState = nodeCreationState,
                        edgeCreationState = edgeCreationState,
                        nodeSchemaCreationState = nodeSchemaCreationState,
                        edgeSchemaCreationState = edgeSchemaCreationState,
                        onClearSelection = { viewModel.metadataViewModel.clearSelectedItem(); viewModel.selectTab(TerminalViewTabs.METADATA) },
                        onNodeCreationSchemaSelected = { viewModel.metadataViewModel.updateNodeCreationSchema(it) },
                        onNodeCreationPropertyChanged = { key, value ->
                            viewModel.metadataViewModel.updateNodeCreationProperty(
                                key,
                                value
                            )
                        },
                        onNodeCreationCreateClick = { viewModel.metadataViewModel.createNodeFromState(); viewModel.selectTab(TerminalViewTabs.METADATA) },
                        onNodeCreationCancelClick = { viewModel.metadataViewModel.cancelNodeCreation(); viewModel.selectTab(TerminalViewTabs.METADATA) },
                        onEdgeCreationSchemaSelected = { viewModel.metadataViewModel.updateEdgeCreationSchema(it) },
                        onEdgeCreationSrcSelected = { viewModel.metadataViewModel.updateEdgeCreationSrc(it) },
                        onEdgeCreationDstSelected = { viewModel.metadataViewModel.updateEdgeCreationDst(it) },
                        onEdgeCreationPropertyChanged = { key, value ->
                            viewModel.metadataViewModel.updateEdgeCreationProperty(
                                key,
                                value
                            )
                        },
                        onEdgeCreationCreateClick = { viewModel.metadataViewModel.createEdgeFromState(); viewModel.selectTab(TerminalViewTabs.METADATA) },
                        onEdgeCreationCancelClick = { viewModel.metadataViewModel.cancelEdgeCreation(); viewModel.selectTab(TerminalViewTabs.METADATA) },
                        onNodeSchemaCreationCreateClick = { viewModel.schemaViewModel.createNodeSchemaFromState(it) { viewModel.metadataViewModel.clearSelectedItem(); viewModel.selectTab(TerminalViewTabs.SCHEMA)} },
                        onNodeSchemaCreationCancelClick = { viewModel.metadataViewModel.cancelNodeSchemaCreation(); viewModel.selectTab(TerminalViewTabs.SCHEMA) },
                        onEdgeSchemaCreationCreateClick = { viewModel.schemaViewModel.createEdgeSchemaFromState(it) { viewModel.metadataViewModel.clearSelectedItem(); viewModel.selectTab(TerminalViewTabs.SCHEMA)} },
                        onEdgeSchemaCreationCancelClick = { viewModel.metadataViewModel.cancelEdgeSchemaCreation(); viewModel.selectTab(TerminalViewTabs.SCHEMA) },
                        onNodeSchemaTableNameChange = { viewModel.schemaViewModel.onNodeSchemaTableNameChange(it) },
                        onNodeSchemaPropertyChange = { index, property -> viewModel.schemaViewModel.onNodeSchemaPropertyChange(index, property) },
                        onAddNodeSchemaProperty = { viewModel.schemaViewModel.onAddNodeSchemaProperty() },
                        onRemoveNodeSchemaProperty = { viewModel.schemaViewModel.onRemoveNodeSchemaProperty(it) },
                        onEdgeSchemaTableNameChange = { viewModel.schemaViewModel.onEdgeSchemaTableNameChange(it) },
                        onEdgeSchemaSrcTableChange = { viewModel.schemaViewModel.onEdgeSchemaSrcTableChange(it) },
                        onEdgeSchemaDstTableChange = { viewModel.schemaViewModel.onEdgeSchemaDstTableChange(it) },
                        onEdgeSchemaPropertyChange = { index, property -> viewModel.schemaViewModel.onEdgeSchemaPropertyChange(index, property) },
                        onAddEdgeSchemaProperty = { viewModel.schemaViewModel.onAddEdgeSchemaProperty() },
                        onRemoveEdgeSchemaProperty = { viewModel.schemaViewModel.onRemoveEdgeSchemaProperty(it) }
                    )
                }
            }
        }

        queryResult?.let {
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            ) {
                QueryView(it) {
                    viewModel.queryViewModel.clearQueryResult()
                }
            }
        }
    }
}