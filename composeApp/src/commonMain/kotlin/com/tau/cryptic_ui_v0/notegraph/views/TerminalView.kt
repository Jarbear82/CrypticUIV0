package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.NodeTable
import com.tau.cryptic_ui_v0.EdgeTable
import com.tau.cryptic_ui_v0.NodeSchemaEditState
import com.tau.cryptic_ui_v0.SchemaNode
import com.tau.cryptic_ui_v0.EdgeSchemaEditState
import com.tau.cryptic_ui_v0.SchemaEdge
import com.tau.cryptic_ui_v0.notegraph.graph.GraphView
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


    // --- COLLECT THE NEW CONSOLIDATED STATE ---
    val editScreenState by viewModel.editCreateViewModel.editScreenState.collectAsState()
    // (Old individual states are no longer needed)


    // Data Tabs
    val selectedDataTab by viewModel.selectedDataTab.collectAsState()

    // View Tabs
    val selectedViewTab by viewModel.selectedViewTab.collectAsState()

    // --- Unified Save/Cancel Handlers ---

    val onSave: () -> Unit = {
        val currentState = viewModel.editCreateViewModel.getCurrentEditState()
        viewModel.metadataViewModel.saveEditedItem(
            editedState = currentState,
            onFinished = {
                // This code now runs *after* the save is complete
                viewModel.editCreateViewModel.cancelAllEditing() // Clear the *edited* state

                // Navigate back to the correct tab
                val targetTab = when (currentState) {
                    is NodeTable, is EdgeTable -> DataViewTabs.METADATA
                    is NodeSchemaEditState, is EdgeSchemaEditState -> DataViewTabs.SCHEMA
                    else -> DataViewTabs.METADATA
                }
                viewModel.selectDataTab(targetTab)
            }
        )
    }

    val onCancel: () -> Unit = {
        val originalItem = viewModel.metadataViewModel.itemToEdit.value
        viewModel.editCreateViewModel.cancelAllEditing() // Clear edited state
        viewModel.metadataViewModel.clearSelectedItem() // Clear original state

        // Navigate back to the correct tab based on what *was* being edited
        val targetTab = when (originalItem) {
            is NodeTable, is EdgeTable -> DataViewTabs.METADATA
            is SchemaNode, is SchemaEdge -> DataViewTabs.SCHEMA
            is String -> DataViewTabs.SCHEMA // For "Create..." strings
            else -> DataViewTabs.METADATA
        }
        viewModel.selectDataTab(targetTab)
    }


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
                        GraphView(nodes = nodes, edges = edges, modifier = Modifier.fillMaxSize())
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
                        onEditNodeClick = {
                            scope.launch {
                                val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                if (fullItem is NodeTable) {
                                    viewModel.editCreateViewModel.initiateNodeEdit(fullItem)
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onEditEdgeClick = {
                            scope.launch {
                                val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                if (fullItem is EdgeTable) {
                                    viewModel.editCreateViewModel.initiateEdgeEdit(fullItem)
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onDeleteEdgeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onAddNodeClick = { viewModel.editCreateViewModel.initiateNodeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeClick = { viewModel.editCreateViewModel.initiateEdgeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) }
                    )
                    DataViewTabs.SCHEMA -> SchemaView(
                        schema = schema,
                        primarySelectedItem = primarySelectedItem,
                        secondarySelectedItem = secondarySelectedItem,
                        onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEditNodeClick = {
                            scope.launch {
                                val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                if (fullItem is SchemaNode) {
                                    viewModel.editCreateViewModel.initiateNodeSchemaEdit(fullItem)
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onEditEdgeClick = {
                            scope.launch {
                                val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                if (fullItem is SchemaEdge) {
                                    viewModel.editCreateViewModel.initiateEdgeSchemaEdit(fullItem)
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onDeleteNodeClick = { viewModel.schemaViewModel.deleteSchemaNode(it) },
                        onDeleteEdgeClick = { viewModel.schemaViewModel.deleteSchemaEdge(it) },
                        onAddNodeSchemaClick = { viewModel.editCreateViewModel.initiateNodeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeSchemaClick = { viewModel.editCreateViewModel.initiateEdgeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) }
                    )
                    DataViewTabs.EDIT -> EditItemView(
                        // --- PASS THE NEW STATE ---
                        editScreenState = editScreenState,

                        // Event Handlers
                        onSaveClick = onSave,
                        onCancelClick = onCancel,

                        // Node Creation Handlers
                        onNodeCreationSchemaSelected = { viewModel.editCreateViewModel.updateNodeCreationSchema(it) },
                        onNodeCreationPropertyChanged = { key, value ->
                            viewModel.editCreateViewModel.updateNodeCreationProperty(key, value)
                        },
                        onNodeCreationCreateClick = { viewModel.editCreateViewModel.createNodeFromState { viewModel.selectDataTab(DataViewTabs.METADATA) } },

                        // Edge Creation Handlers
                        onEdgeCreationSchemaSelected = { viewModel.editCreateViewModel.updateEdgeCreationSchema(it) },
                        onEdgeCreationConnectionSelected = { viewModel.editCreateViewModel.updateEdgeCreationConnection(it) },
                        onEdgeCreationSrcSelected = { viewModel.editCreateViewModel.updateEdgeCreationSrc(it) },
                        onEdgeCreationDstSelected = { viewModel.editCreateViewModel.updateEdgeCreationDst(it) },
                        onEdgeCreationPropertyChanged = { key, value ->
                            viewModel.editCreateViewModel.updateEdgeCreationProperty(key, value)
                        },
                        onEdgeCreationCreateClick = { viewModel.editCreateViewModel.createEdgeFromState { viewModel.selectDataTab(DataViewTabs.METADATA) } },

                        // Node Schema Creation Handlers
                        onNodeSchemaCreationCreateClick = { state -> viewModel.editCreateViewModel.createNodeSchemaFromState(state) { viewModel.selectDataTab(DataViewTabs.SCHEMA)} },
                        onNodeSchemaTableNameChange = { viewModel.editCreateViewModel.onNodeSchemaTableNameChange(it) },
                        onNodeSchemaPropertyChange = { index, property -> viewModel.editCreateViewModel.onNodeSchemaPropertyChange(index, property) },
                        onAddNodeSchemaProperty = { viewModel.editCreateViewModel.onAddNodeSchemaProperty() },
                        onRemoveNodeSchemaProperty = { viewModel.editCreateViewModel.onRemoveNodeSchemaProperty(it) },

                        // Edge Schema Creation Handlers
                        onEdgeSchemaCreationCreateClick = { state -> viewModel.editCreateViewModel.createEdgeSchemaFromState(state) { viewModel.selectDataTab(DataViewTabs.SCHEMA)} },
                        onEdgeSchemaTableNameChange = { viewModel.editCreateViewModel.onEdgeSchemaTableNameChange(it) },
                        onEdgeSchemaCreationAddConnection = { src, dst -> viewModel.editCreateViewModel.onAddEdgeSchemaConnection(src, dst) },
                        onEdgeSchemaCreationRemoveConnection = { index -> viewModel.editCreateViewModel.onRemoveEdgeSchemaConnection(index) },
                        onEdgeSchemaPropertyChange = { index, property -> viewModel.editCreateViewModel.onEdgeSchemaPropertyChange(index, property) },
                        onAddEdgeSchemaProperty = { viewModel.editCreateViewModel.onAddEdgeSchemaProperty() },
                        onRemoveEdgeSchemaProperty = { viewModel.editCreateViewModel.onRemoveEdgeSchemaProperty(it) },

                        // Node Edit Handlers
                        onNodeEditPropertyChange = { index, value -> viewModel.editCreateViewModel.updateNodeEditProperty(index, value) },

                        // Edge Edit Handlers
                        onEdgeEditPropertyChange = { index, value -> viewModel.editCreateViewModel.updateEdgeEditProperty(index, value) },

                        // Node Schema Edit Handlers
                        onNodeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateNodeSchemaEditLabel(it) },
                        onNodeSchemaEditPropertyChange = { index, prop -> viewModel.editCreateViewModel.updateNodeSchemaEditProperty(index, prop) },
                        onNodeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditAddProperty() },
                        onNodeSchemaEditRemoveProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditRemoveProperty(it) },

                        // Edge Schema Edit Handlers
                        onEdgeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateEdgeSchemaEditLabel(it) },
                        onEdgeSchemaEditPropertyChange = { index, prop -> viewModel.editCreateViewModel.updateEdgeSchemaEditProperty(index, prop) },
                        onEdgeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditAddProperty() },
                        onEdgeSchemaEditRemoveProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveProperty(it) }
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