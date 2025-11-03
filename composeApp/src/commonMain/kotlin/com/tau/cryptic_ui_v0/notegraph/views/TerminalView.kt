package com.tau.cryptic_ui_v0.notegraph.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.SchemaDefinitionItem
// UPDATED: This now points to your new custom graph view
import com.tau.cryptic_ui_v0.notegraph.graph.GraphView
import kotlinx.coroutines.launch
import com.tau.cryptic_ui_v0.viewmodels.DataViewTabs
import com.tau.cryptic_ui_v0.viewmodels.TerminalViewModel
import com.tau.cryptic_ui_v0.viewmodels.ViewTabs
import com.tau.cryptic_ui_v0.views.ListView
// ADDED: Import for GraphViewModel
import com.tau.cryptic_ui_v0.notegraph.graph.GraphViewmodel

@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    val schema by viewModel.schemaViewModel.schema.collectAsState()
    // REMOVED: val queryResult by viewModel.queryViewModel.queryResult.collectAsState()
    // REMOVED: val query by viewModel.queryViewModel.query
    val scope = rememberCoroutineScope() // Get a coroutine scope

    // Collect the state for the MetadataView
    val nodes by viewModel.metadataViewModel.nodeList.collectAsState() //
    val edges by viewModel.metadataViewModel.edgeList.collectAsState() //
    val itemToEdit by viewModel.metadataViewModel.itemToEdit.collectAsState()
    val primarySelectedItem by viewModel.metadataViewModel.primarySelectedItem.collectAsState()
    val secondarySelectedItem by viewModel.metadataViewModel.secondarySelectedItem.collectAsState()

    // ADDED: Get the GraphViewModel from the TerminalViewModel
    val graphViewModel = viewModel.graphViewModel

    // --- ADDED: Collect state for the delete schema dialog ---
    val schemaToDelete by viewModel.schemaViewModel.schemaToDelete.collectAsState()
    val dependencyCount by viewModel.schemaViewModel.schemaDependencyCount.collectAsState()
    // --- END ADDED ---


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
                    // FIX: Use new EditState classes
                    is com.tau.cryptic_ui_v0.NodeEditState, is com.tau.cryptic_ui_v0.EdgeEditState -> DataViewTabs.METADATA
                    is com.tau.cryptic_ui_v0.NodeSchemaEditState, is com.tau.cryptic_ui_v0.EdgeSchemaEditState -> DataViewTabs.SCHEMA
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
            // FIX: Use new EditState classes and SchemaDefinitionItem
            is com.tau.cryptic_ui_v0.NodeEditState, is com.tau.cryptic_ui_v0.EdgeEditState -> DataViewTabs.METADATA
            is SchemaDefinitionItem -> DataViewTabs.SCHEMA
            is String -> DataViewTabs.SCHEMA // For "Create..." states
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
                    ViewTabs.LIST -> { // Changed from QUERY
                        // Added ListView
                        ListView(
                            nodes = nodes,
                            edges = edges,
                            primarySelectedItem = primarySelectedItem,
                            secondarySelectedItem = secondarySelectedItem,
                            onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                            onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                            onEditNodeClick = {
                                scope.launch {
                                    val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                    if (fullItem is com.tau.cryptic_ui_v0.NodeEditState) {
                                        viewModel.editCreateViewModel.initiateNodeEdit(fullItem)
                                        viewModel.selectDataTab(DataViewTabs.EDIT)
                                    }
                                }
                            },
                            onEditEdgeClick = {
                                scope.launch {
                                    val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                    if (fullItem is com.tau.cryptic_ui_v0.EdgeEditState) {
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
                    }
                    ViewTabs.GRAPH -> {
                        // Pass the collected nodes and edges to the GraphView
                        // UPDATED: Pass the GraphViewModel
                        graphViewModel?.let {
                            GraphView(graphViewModel)
                        } ?: Text("Loading Graph...")
                    }
                }
            }


            // --------------- Right panel for Schema and Metadata tabs ------------------------------

            Column(modifier = Modifier.width(400.dp).padding(16.dp)) {

                // --- ADDED: Confirmation Dialog ---
                // This dialog will appear on top of this Column when state is set
                val itemToDelete = schemaToDelete
                if (itemToDelete != null && selectedDataTab == DataViewTabs.SCHEMA) {
                    DeleteSchemaConfirmationDialog(
                        item = itemToDelete,
                        dependencyCount = dependencyCount,
                        onConfirm = {
                            viewModel.schemaViewModel.confirmDeleteSchema()
                        },
                        onDismiss = {
                            viewModel.schemaViewModel.clearDeleteSchemaRequest()
                        }
                    )
                }
                // --- END ADDED ---


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
                        // REMOVED: dbMetaData = null,
                        nodes = nodes,
                        edges = edges,
                        primarySelectedItem = primarySelectedItem,
                        secondarySelectedItem = secondarySelectedItem,
                        onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEditNodeClick = {
                            scope.launch {
                                val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                // FIX: Check for new EditState class
                                if (fullItem is com.tau.cryptic_ui_v0.NodeEditState) {
                                    viewModel.editCreateViewModel.initiateNodeEdit(fullItem)
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onEditEdgeClick = {
                            scope.launch {
                                val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                // FIX: Check for new EditState class
                                if (fullItem is com.tau.cryptic_ui_v0.EdgeEditState) {
                                    viewModel.editCreateViewModel.initiateEdgeEdit(fullItem)
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onDeleteEdgeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onAddNodeClick = { viewModel.editCreateViewModel.initiateNodeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeClick = { viewModel.editCreateViewModel.initiateEdgeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        // ADDED: Pass refresh handlers
                        onListAllClick = { viewModel.metadataViewModel.listAll() },
                        onListNodesClick = { viewModel.metadataViewModel.listNodes() },
                        onListEdgesClick = { viewModel.metadataViewModel.listEdges() }
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
                                // FIX: Check for SchemaDefinitionItem
                                if (fullItem is SchemaDefinitionItem) {
                                    viewModel.editCreateViewModel.initiateNodeSchemaEdit(fullItem) // FIX: Uncomment
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onEditEdgeClick = {
                            scope.launch {
                                val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                if (fullItem is SchemaDefinitionItem) {
                                    viewModel.editCreateViewModel.initiateEdgeSchemaEdit(fullItem) // FIX: Uncomment
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onDeleteNodeClick = { viewModel.schemaViewModel.requestDeleteSchema(it) },
                        onDeleteEdgeClick = { viewModel.schemaViewModel.requestDeleteSchema(it) },
                        onAddNodeSchemaClick = { viewModel.editCreateViewModel.initiateNodeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeSchemaClick = { viewModel.editCreateViewModel.initiateEdgeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddNodeClick = { viewModel.editCreateViewModel.initiateNodeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeClick = { viewModel.editCreateViewModel.initiateEdgeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) }
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
                        onNodeSchemaCreationCreateClick = { viewModel.editCreateViewModel.createNodeSchemaFromState { viewModel.selectDataTab(DataViewTabs.SCHEMA)} },
                        onNodeSchemaTableNameChange = { viewModel.editCreateViewModel.onNodeSchemaTableNameChange(it) },
                        onNodeSchemaPropertyChange = { index, property -> viewModel.editCreateViewModel.onNodeSchemaPropertyChange(index, property) },
                        onAddNodeSchemaProperty = { viewModel.editCreateViewModel.onAddNodeSchemaProperty() },
                        onRemoveNodeSchemaProperty = { viewModel.editCreateViewModel.onRemoveNodeSchemaProperty(it) },

                        // Edge Schema Creation Handlers
                        onEdgeSchemaCreationCreateClick = { viewModel.editCreateViewModel.createEdgeSchemaFromState { viewModel.selectDataTab(DataViewTabs.SCHEMA)} },
                        onEdgeSchemaTableNameChange = { viewModel.editCreateViewModel.onEdgeSchemaTableNameChange(it) },
                        onEdgeSchemaCreationAddConnection = { src, dst -> viewModel.editCreateViewModel.onAddEdgeSchemaConnection(src, dst) },
                        onEdgeSchemaCreationRemoveConnection = { index -> viewModel.editCreateViewModel.onRemoveEdgeSchemaConnection(index) },
                        onEdgeSchemaPropertyChange = { index, property -> viewModel.editCreateViewModel.onEdgeSchemaPropertyChange(index, property) },
                        onAddEdgeSchemaProperty = { viewModel.editCreateViewModel.onAddEdgeSchemaProperty() },
                        onRemoveEdgeSchemaProperty = { viewModel.editCreateViewModel.onRemoveEdgeSchemaProperty(it) },

                        // Node Edit Handlers
                        // UPDATED: Renamed 'index' to 'key' for clarity
                        onNodeEditPropertyChange = { key, value -> viewModel.editCreateViewModel.updateNodeEditProperty(key, value) },

                        // Edge Edit Handlers
                        // UPDATED: Renamed 'index' to 'key' for clarity
                        onEdgeEditPropertyChange = { key, value -> viewModel.editCreateViewModel.updateEdgeEditProperty(key, value) },

                        // Node Schema Edit Handlers
                        onNodeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateNodeSchemaEditLabel(it) },
                        onNodeSchemaEditPropertyChange = { index, prop -> viewModel.editCreateViewModel.updateNodeSchemaEditProperty(index, prop) },
                        onNodeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditAddProperty() },
                        onNodeSchemaEditRemoveProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditRemoveProperty(it) },

                        // Edge Schema Edit Handlers
                        onEdgeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateEdgeSchemaEditLabel(it) },
                        onEdgeSchemaEditPropertyChange = { index, prop -> viewModel.editCreateViewModel.updateEdgeSchemaEditProperty(index, prop) },
                        onEdgeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditAddProperty() },
                        onEdgeSchemaEditRemoveProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveProperty(it) },
                        onEdgeSchemaEditAddConnection = { src, dst -> viewModel.editCreateViewModel.updateEdgeSchemaEditAddConnection(src, dst) },
                        onEdgeSchemaEditRemoveConnection = { index -> viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveConnection(index) },
                        allNodeSchemaNames = schema?.nodeSchemas?.map { it.name } ?: emptyList()


                    )
                }
            }
        }
    }
}
