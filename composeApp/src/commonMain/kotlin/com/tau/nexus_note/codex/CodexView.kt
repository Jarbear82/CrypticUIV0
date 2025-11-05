package com.tau.nexus_note.codex

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.EdgeEditState
import com.tau.nexus_note.datamodels.EdgeSchemaEditState
import com.tau.nexus_note.datamodels.NodeEditState
import com.tau.nexus_note.datamodels.NodeSchemaEditState
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.codex.crud.DeleteSchemaConfirmationDialog
import com.tau.nexus_note.codex.crud.update.EditItemView
// UPDATED: This now points to your new custom graph view
import com.tau.nexus_note.codex.graph.GraphView
import com.tau.nexus_note.codex.metadata.MetadataView
import com.tau.nexus_note.codex.schema.SchemaView
import kotlinx.coroutines.launch
import com.tau.nexus_note.viewmodels.DataViewTabs
import com.tau.nexus_note.viewmodels.CodexViewModel
import com.tau.nexus_note.viewmodels.ViewTabs
import com.tau.nexus_note.views.ListView
// ADDED: Import for GraphViewModel

@Composable
fun CodexView(viewModel: CodexViewModel) {
    val schema by viewModel.schemaViewModel.schema.collectAsState()
    val scope = rememberCoroutineScope()

    // Collect the state for the MetadataView
    val nodes by viewModel.metadataViewModel.nodeList.collectAsState()
    val edges by viewModel.metadataViewModel.edgeList.collectAsState()
    val itemToEdit by viewModel.metadataViewModel.itemToEdit.collectAsState()
    val primarySelectedItem by viewModel.metadataViewModel.primarySelectedItem.collectAsState()
    val secondarySelectedItem by viewModel.metadataViewModel.secondarySelectedItem.collectAsState()

    val graphViewModel = viewModel.graphViewModel

    val schemaToDelete by viewModel.schemaViewModel.schemaToDelete.collectAsState()
    val dependencyCount by viewModel.schemaViewModel.schemaDependencyCount.collectAsState()

    val editScreenState by viewModel.editCreateViewModel.editScreenState.collectAsState()


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
                    is NodeEditState, is EdgeEditState -> DataViewTabs.METADATA
                    is NodeSchemaEditState, is EdgeSchemaEditState -> DataViewTabs.SCHEMA
                    else -> DataViewTabs.METADATA
                }
                viewModel.selectDataTab(targetTab)
            }
        )
    }

    val onCancel: () -> Unit = {
        val originalItem = viewModel.metadataViewModel.itemToEdit.value
        viewModel.editCreateViewModel.cancelAllEditing()
        viewModel.metadataViewModel.clearSelectedItem()

        // Navigate back to the correct tab based on what *was* being edited
        val targetTab = when (originalItem) {
            // FIX: Use new EditState classes and SchemaDefinitionItem
            is NodeEditState, is EdgeEditState,
            is SchemaDefinitionItem -> DataViewTabs.SCHEMA
            is String -> DataViewTabs.SCHEMA
            else -> DataViewTabs.SCHEMA
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
                                    if (fullItem is NodeEditState) {
                                        viewModel.editCreateViewModel.initiateNodeEdit(fullItem)
                                        viewModel.selectDataTab(DataViewTabs.EDIT)
                                    }
                                }
                            },
                            onEditEdgeClick = {
                                scope.launch {
                                    val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                    if (fullItem is EdgeEditState) {
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
                        nodes = nodes,
                        edges = edges,
                        primarySelectedItem = primarySelectedItem,
                        secondarySelectedItem = secondarySelectedItem,
                        onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEditNodeClick = {
                            scope.launch {
                                val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                if (fullItem is NodeEditState) {
                                    viewModel.editCreateViewModel.initiateNodeEdit(fullItem)
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onEditEdgeClick = {
                            scope.launch {
                                val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                if (fullItem is EdgeEditState) {
                                    viewModel.editCreateViewModel.initiateEdgeEdit(fullItem)
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onDeleteEdgeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onAddNodeClick = {
                            viewModel.editCreateViewModel.initiateNodeCreation(); viewModel.selectDataTab(
                            DataViewTabs.EDIT
                        )
                        },
                        onAddEdgeClick = {
                            viewModel.editCreateViewModel.initiateEdgeCreation(); viewModel.selectDataTab(
                            DataViewTabs.EDIT
                        )
                        },
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
                                    viewModel.editCreateViewModel.initiateNodeSchemaEdit(fullItem)
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onEditEdgeClick = {
                            scope.launch {
                                val fullItem = viewModel.metadataViewModel.setItemToEdit(it)
                                if (fullItem is SchemaDefinitionItem) {
                                    viewModel.editCreateViewModel.initiateEdgeSchemaEdit(fullItem)
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                }
                            }
                        },
                        onDeleteNodeClick = { viewModel.schemaViewModel.requestDeleteSchema(it) },
                        onDeleteEdgeClick = { viewModel.schemaViewModel.requestDeleteSchema(it) },
                        onAddNodeSchemaClick = {
                            viewModel.editCreateViewModel.initiateNodeSchemaCreation(); viewModel.selectDataTab(
                            DataViewTabs.EDIT
                        )
                        },
                        onAddEdgeSchemaClick = {
                            viewModel.editCreateViewModel.initiateEdgeSchemaCreation(); viewModel.selectDataTab(
                            DataViewTabs.EDIT
                        )
                        },
                        onAddNodeClick = {
                            viewModel.editCreateViewModel.initiateNodeCreation(); viewModel.selectDataTab(
                            DataViewTabs.EDIT
                        )
                        },
                        onAddEdgeClick = {
                            viewModel.editCreateViewModel.initiateEdgeCreation(); viewModel.selectDataTab(
                            DataViewTabs.EDIT
                        )
                        }
                    )
                    DataViewTabs.EDIT -> EditItemView(
                        editScreenState = editScreenState,

                        // Event Handlers
                        onSaveClick = onSave,
                        onCancelClick = onCancel,

                        // Node Creation Handlers
                        onNodeCreationSchemaSelected = { viewModel.editCreateViewModel.updateNodeCreationSchema(it) },
                        onNodeCreationPropertyChanged = { key, value ->
                            viewModel.editCreateViewModel.updateNodeCreationProperty(key, value)
                        },
                        onNodeCreationCreateClick = {
                            viewModel.editCreateViewModel.createNodeFromState {
                                viewModel.selectDataTab(
                                    DataViewTabs.METADATA
                                )
                            }
                        },

                        // Edge Creation Handlers
                        onEdgeCreationSchemaSelected = { viewModel.editCreateViewModel.updateEdgeCreationSchema(it) },
                        onEdgeCreationConnectionSelected = {
                            viewModel.editCreateViewModel.updateEdgeCreationConnection(
                                it
                            )
                        },
                        onEdgeCreationSrcSelected = { viewModel.editCreateViewModel.updateEdgeCreationSrc(it) },
                        onEdgeCreationDstSelected = { viewModel.editCreateViewModel.updateEdgeCreationDst(it) },
                        onEdgeCreationPropertyChanged = { key, value ->
                            viewModel.editCreateViewModel.updateEdgeCreationProperty(key, value)
                        },
                        onEdgeCreationCreateClick = {
                            viewModel.editCreateViewModel.createEdgeFromState {
                                viewModel.selectDataTab(
                                    DataViewTabs.METADATA
                                )
                            }
                        },

                        // Node Schema Creation Handlers
                        onNodeSchemaCreationCreateClick = {
                            viewModel.editCreateViewModel.createNodeSchemaFromState {
                                viewModel.selectDataTab(
                                    DataViewTabs.SCHEMA
                                )
                            }
                        },
                        onNodeSchemaTableNameChange = { viewModel.editCreateViewModel.onNodeSchemaTableNameChange(it) },
                        onNodeSchemaPropertyChange = { index, property ->
                            viewModel.editCreateViewModel.onNodeSchemaPropertyChange(
                                index,
                                property
                            )
                        },
                        onAddNodeSchemaProperty = { viewModel.editCreateViewModel.onAddNodeSchemaProperty() },
                        onRemoveNodeSchemaProperty = { viewModel.editCreateViewModel.onRemoveNodeSchemaProperty(it) },

                        // Edge Schema Creation Handlers
                        onEdgeSchemaCreationCreateClick = {
                            viewModel.editCreateViewModel.createEdgeSchemaFromState {
                                viewModel.selectDataTab(
                                    DataViewTabs.SCHEMA
                                )
                            }
                        },
                        onEdgeSchemaTableNameChange = { viewModel.editCreateViewModel.onEdgeSchemaTableNameChange(it) },
                        onEdgeSchemaCreationAddConnection = { src, dst ->
                            viewModel.editCreateViewModel.onAddEdgeSchemaConnection(
                                src,
                                dst
                            )
                        },
                        onEdgeSchemaCreationRemoveConnection = { index ->
                            viewModel.editCreateViewModel.onRemoveEdgeSchemaConnection(
                                index
                            )
                        },
                        onEdgeSchemaPropertyChange = { index, property ->
                            viewModel.editCreateViewModel.onEdgeSchemaPropertyChange(
                                index,
                                property
                            )
                        },
                        onAddEdgeSchemaProperty = { viewModel.editCreateViewModel.onAddEdgeSchemaProperty() },
                        onRemoveEdgeSchemaProperty = { viewModel.editCreateViewModel.onRemoveEdgeSchemaProperty(it) },

                        // Node Edit Handlers
                        // UPDATED: Renamed 'index' to 'key' for clarity
                        onNodeEditPropertyChange = { key, value ->
                            viewModel.editCreateViewModel.updateNodeEditProperty(
                                key,
                                value
                            )
                        },

                        // Edge Edit Handlers
                        onEdgeEditPropertyChange = { key, value ->
                            viewModel.editCreateViewModel.updateEdgeEditProperty(
                                key,
                                value
                            )
                        },

                        // Node Schema Edit Handlers
                        onNodeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateNodeSchemaEditLabel(it) },
                        onNodeSchemaEditPropertyChange = { index, prop ->
                            viewModel.editCreateViewModel.updateNodeSchemaEditProperty(
                                index,
                                prop
                            )
                        },
                        onNodeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditAddProperty() },
                        onNodeSchemaEditRemoveProperty = {
                            viewModel.editCreateViewModel.updateNodeSchemaEditRemoveProperty(
                                it
                            )
                        },

                        // Edge Schema Edit Handlers
                        onEdgeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateEdgeSchemaEditLabel(it) },
                        onEdgeSchemaEditPropertyChange = { index, prop ->
                            viewModel.editCreateViewModel.updateEdgeSchemaEditProperty(
                                index,
                                prop
                            )
                        },
                        onEdgeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditAddProperty() },
                        onEdgeSchemaEditRemoveProperty = {
                            viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveProperty(
                                it
                            )
                        },
                        onEdgeSchemaEditAddConnection = { src, dst ->
                            viewModel.editCreateViewModel.updateEdgeSchemaEditAddConnection(
                                src,
                                dst
                            )
                        },
                        onEdgeSchemaEditRemoveConnection = { index ->
                            viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveConnection(
                                index
                            )
                        },
                        allNodeSchemaNames = schema?.nodeSchemas?.map { it.name } ?: emptyList()


                    )
                }
            }
        }
    }
}
