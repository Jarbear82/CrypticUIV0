package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.viewmodels.TerminalViewModel
import com.tau.cryptic_ui_v0.viewmodels.DataViewTabs
import com.tau.cryptic_ui_v0.viewmodels.ViewTabs
import kotlinx.coroutines.launch

@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    val schema by viewModel.schemaViewModel.schema.collectAsState()
    val queryResult by viewModel.queryViewModel.queryResult.collectAsState()
    val metaData by viewModel.metadataViewModel.dbMetaData.collectAsState()
    val query by viewModel.queryViewModel.query
    val scope = rememberCoroutineScope() // Get a coroutine scope

    // Collect the state for the MetadataView
    val nodes by viewModel.metadataViewModel.nodeList.collectAsState() //
    val edges by viewModel.metadataViewModel.edgeList.collectAsState() //
    val itemToEdit by viewModel.metadataViewModel.itemToEdit.collectAsState()
    val primarySelectedItem by viewModel.metadataViewModel.primarySelectedItem.collectAsState()
    val secondarySelectedItem by viewModel.metadataViewModel.secondarySelectedItem.collectAsState()


    // Collect states from CreationViewModel
    val nodeCreationState by viewModel.creationViewModel.nodeCreationState.collectAsState()
    val edgeCreationState by viewModel.creationViewModel.edgeCreationState.collectAsState()
    val nodeSchemaCreationState by viewModel.creationViewModel.nodeSchemaCreationState.collectAsState()
    val edgeSchemaCreationState by viewModel.creationViewModel.edgeSchemaCreationState.collectAsState()


    // Data Tabs
    val selectedDataTab by viewModel.selectedDataTab.collectAsState()

    // View Tabs
    val selectedViewTab by viewModel.selectedViewTab.collectAsState()


    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ------------------ Left panel for controls and query results --------------------------

            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                TabRow(selectedTabIndex = selectedViewTab.value) {
                    ViewTabs.entries.forEach { tab ->
                        Tab(
                            text = { Text(tab.name) },
                            selected = selectedViewTab.value == tab.value,
                            onClick = { viewModel.selectViewTab(tab) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedViewTab) {
                    ViewTabs.QUERY -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    }
                    ViewTabs.GRAPH -> {
                        // Pass the collected nodes and edges to the GraphView
                        GraphView(nodes = nodes, edges = edges)
                    }
                }
            }


            // --------------- Right panel for Schema and Metadata tabs ------------------------------

            Column(modifier = Modifier.width(400.dp).padding(16.dp)) {


                TabRow(selectedTabIndex = selectedDataTab.value) {
                    DataViewTabs.entries.forEach { tab ->
                        Tab(
                            text = { Text(tab.name) },
                            selected = selectedDataTab.value == tab.value,
                            onClick = { viewModel.selectDataTab(tab) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedDataTab) {
                    DataViewTabs.METADATA -> MetadataView(
                        dbMetaData = metaData,
                        nodes = nodes,
                        edges = edges,
                        primarySelectedItem = primarySelectedItem,
                        secondarySelectedItem = secondarySelectedItem,
                        onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEditNodeClick = { viewModel.metadataViewModel.setItemToEdit(it); println("Item: $it is being selected"); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onEditEdgeClick = { viewModel.metadataViewModel.setItemToEdit(it); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onDeleteEdgeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onAddNodeClick = { viewModel.creationViewModel.initiateNodeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeClick = { viewModel.creationViewModel.initiateEdgeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) }
                    )
                    DataViewTabs.SCHEMA -> SchemaView(
                        schema = schema,
                        primarySelectedItem = primarySelectedItem,
                        secondarySelectedItem = secondarySelectedItem,
                        onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEditNodeClick = { viewModel.metadataViewModel.setItemToEdit(it); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onEditEdgeClick = { viewModel.metadataViewModel.setItemToEdit(it); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onDeleteNodeClick = { viewModel.schemaViewModel.deleteSchemaNode(it) },
                        onDeleteEdgeClick = { viewModel.schemaViewModel.deleteSchemaEdge(it) },
                        onAddNodeSchemaClick = { viewModel.creationViewModel.initiateNodeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeSchemaClick = { viewModel.creationViewModel.initiateEdgeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) }
                    )
                    DataViewTabs.EDIT -> EditItemView(
                        editItem = itemToEdit,
                        nodeCreationState = nodeCreationState,
                        edgeCreationState = edgeCreationState,
                        nodeSchemaCreationState = nodeSchemaCreationState,
                        edgeSchemaCreationState = edgeSchemaCreationState,
                        onClearSelection = { viewModel.metadataViewModel.clearSelectedItem(); viewModel.selectDataTab(DataViewTabs.METADATA) },
                        onNodeCreationSchemaSelected = { viewModel.creationViewModel.updateNodeCreationSchema(it) },
                        onNodeCreationPropertyChanged = { key, value ->
                            viewModel.creationViewModel.updateNodeCreationProperty(
                                key,
                                value
                            )
                        },
                        onNodeCreationCreateClick = { viewModel.creationViewModel.createNodeFromState { viewModel.selectDataTab(DataViewTabs.METADATA) } },
                        onNodeCreationCancelClick = { viewModel.creationViewModel.cancelNodeCreation(); viewModel.selectDataTab(DataViewTabs.METADATA) },
                        onEdgeCreationSchemaSelected = { viewModel.creationViewModel.updateEdgeCreationSchema(it) },
                        onEdgeCreationSrcSelected = { viewModel.creationViewModel.updateEdgeCreationSrc(it) },
                        onEdgeCreationDstSelected = { viewModel.creationViewModel.updateEdgeCreationDst(it) },
                        onEdgeCreationPropertyChanged = { key, value ->
                            viewModel.creationViewModel.updateEdgeCreationProperty(
                                key,
                                value
                            )
                        },
                        onEdgeCreationCreateClick = { viewModel.creationViewModel.createEdgeFromState { viewModel.selectDataTab(DataViewTabs.METADATA) } },
                        onEdgeCreationCancelClick = { viewModel.creationViewModel.cancelEdgeCreation(); viewModel.selectDataTab(DataViewTabs.METADATA) },
                        onNodeSchemaCreationCreateClick = { viewModel.creationViewModel.createNodeSchemaFromState(it) { viewModel.metadataViewModel.clearSelectedItem(); viewModel.selectDataTab(DataViewTabs.SCHEMA)} },
                        onNodeSchemaCreationCancelClick = { viewModel.creationViewModel.cancelNodeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.SCHEMA) },
                        onEdgeSchemaCreationCreateClick = { viewModel.creationViewModel.createEdgeSchemaFromState(it) { viewModel.metadataViewModel.clearSelectedItem(); viewModel.selectDataTab(DataViewTabs.SCHEMA)} },
                        onEdgeSchemaCreationCancelClick = { viewModel.creationViewModel.cancelEdgeSchemaCreationFromState(); viewModel.selectDataTab(DataViewTabs.SCHEMA) },
                        onNodeSchemaTableNameChange = { viewModel.creationViewModel.onNodeSchemaTableNameChange(it) },
                        onNodeSchemaPropertyChange = { index, property -> viewModel.creationViewModel.onNodeSchemaPropertyChange(index, property) },
                        onAddNodeSchemaProperty = { viewModel.creationViewModel.onAddNodeSchemaProperty() },
                        onRemoveNodeSchemaProperty = { viewModel.creationViewModel.onRemoveNodeSchemaProperty(it) },
                        onEdgeSchemaTableNameChange = { viewModel.creationViewModel.onEdgeSchemaTableNameChange(it) },
                        onEdgeSchemaSrcTableChange = { viewModel.creationViewModel.onEdgeSchemaSrcTableChange(it) },
                        onEdgeSchemaDstTableChange = { viewModel.creationViewModel.onEdgeSchemaDstTableChange(it) },
                        onEdgeSchemaPropertyChange = { index, property -> viewModel.creationViewModel.onEdgeSchemaPropertyChange(index, property) },
                        onAddEdgeSchemaProperty = { viewModel.creationViewModel.onAddEdgeSchemaProperty() },
                        onRemoveEdgeSchemaProperty = { viewModel.creationViewModel.onRemoveEdgeSchemaProperty(it) }
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