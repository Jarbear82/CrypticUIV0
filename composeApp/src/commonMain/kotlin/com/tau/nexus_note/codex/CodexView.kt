package com.tau.nexus_note.codex

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.EdgeSchemaEditState
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.NodeSchemaEditState
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.codex.crud.DeleteSchemaConfirmationDialog
import com.tau.nexus_note.codex.crud.update.EditItemView
import com.tau.nexus_note.codex.graph.GraphView
import com.tau.nexus_note.codex.metadata.MetadataView
import com.tau.nexus_note.codex.schema.SchemaView
import com.tau.nexus_note.codex.schema.SchemaData
import com.tau.nexus_note.codex.DataViewTabs
import com.tau.nexus_note.codex.CodexViewModel
import com.tau.nexus_note.codex.ViewTabs
import com.tau.nexus_note.datamodels.EditScreenState
import com.tau.nexus_note.views.ListView
import kotlinx.coroutines.launch

@Composable
fun CodexView(viewModel: CodexViewModel) {
    // Observe state from repository via the ViewModels
    val schema by viewModel.schemaViewModel.schema.collectAsState()
    val nodes by viewModel.metadataViewModel.nodeList.collectAsState()
    val edges by viewModel.metadataViewModel.edgeList.collectAsState()

    // Observe UI state from ViewModels
    val itemToEdit by viewModel.metadataViewModel.itemToEdit.collectAsState()
    val primarySelectedItem by viewModel.metadataViewModel.primarySelectedItem.collectAsState()
    val secondarySelectedItem by viewModel.metadataViewModel.secondarySelectedItem.collectAsState()
    val schemaToDelete by viewModel.schemaViewModel.schemaToDelete.collectAsState()
    val dependencyCount by viewModel.schemaViewModel.schemaDependencyCount.collectAsState()
    val editScreenState by viewModel.editCreateViewModel.editScreenState.collectAsState()
    val selectedDataTab by viewModel.selectedDataTab.collectAsState()
    val selectedViewTab by viewModel.selectedViewTab.collectAsState()

    val graphViewModel = viewModel.graphViewModel

    // --- Unified Save/Cancel Handlers ---

    val scope = rememberCoroutineScope() // Make sure you have this import
    val onSave: () -> Unit = {
        val state = viewModel.editCreateViewModel.editScreenState.value
        // Get the original item to know where to navigate back to
        val originalItem = viewModel.metadataViewModel.itemToEdit.value

        // Launch a coroutine to call the repository's suspend functions
        scope.launch {
            when (state) {
                is EditScreenState.CreateNode -> viewModel.repository.createNode(state.state)
                is EditScreenState.CreateEdge -> viewModel.repository.createEdge(state.state)
                is EditScreenState.CreateNodeSchema -> viewModel.repository.createNodeSchema(state.state)
                is EditScreenState.CreateEdgeSchema -> viewModel.repository.createEdgeSchema(state.state)
                is EditScreenState.EditNode -> viewModel.repository.updateNode(state.state)
                is EditScreenState.EditEdge -> viewModel.repository.updateEdge(state.state)
                is EditScreenState.EditNodeSchema -> viewModel.repository.updateNodeSchema(state.state)
                is EditScreenState.EditEdgeSchema -> viewModel.repository.updateEdgeSchema(state.state)
                is EditScreenState.None -> {}
            }
        }

        // This part runs immediately after launching the save
        viewModel.editCreateViewModel.cancelAllEditing()
        viewModel.metadataViewModel.clearSelectedItem()
        val targetTab = when (originalItem) {
            is NodeDisplayItem, is EdgeDisplayItem -> DataViewTabs.METADATA
            is SchemaDefinitionItem, is String -> DataViewTabs.SCHEMA // Handle "Create..." strings
            else -> DataViewTabs.METADATA
        }
        viewModel.selectDataTab(targetTab)
    }

    val onCancel: () -> Unit = {
        val originalItem = viewModel.metadataViewModel.itemToEdit.value
        viewModel.editCreateViewModel.cancelAllEditing()
        viewModel.metadataViewModel.clearSelectedItem()

        // Navigate back to the correct tab
        val targetTab = when (originalItem) {
            is NodeDisplayItem, is EdgeDisplayItem -> DataViewTabs.METADATA
            is SchemaDefinitionItem -> DataViewTabs.SCHEMA
            is String -> DataViewTabs.SCHEMA // Handle "Create..." strings
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
                    ViewTabs.LIST -> {
                        ListView(
                            nodes = nodes,
                            edges = edges,
                            primarySelectedItem = primarySelectedItem,
                            secondarySelectedItem = secondarySelectedItem,
                            onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                            onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                            onEditNodeClick = { item ->
                                // UPDATED: Call EditCreateViewModel directly
                                viewModel.editCreateViewModel.initiateNodeEdit(item)
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onEditEdgeClick = { item ->
                                // UPDATED: Call EditCreateViewModel directly
                                viewModel.editCreateViewModel.initiateEdgeEdit(item)
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            // UPDATED: Call MetadataViewModel to delete
                            onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                            onDeleteEdgeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                            onAddNodeClick = { viewModel.editCreateViewModel.initiateNodeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                            onAddEdgeClick = { viewModel.editCreateViewModel.initiateEdgeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) }
                        )
                    }
                    ViewTabs.GRAPH -> {
                        graphViewModel?.let {
                            GraphView(graphViewModel)
                        } ?: Text("Loading Graph...")
                    }
                }
            }


            // --------------- Right panel for Schema and Metadata tabs ------------------------------
            Column(modifier = Modifier.width(400.dp).padding(16.dp)) {

                val itemToDelete = schemaToDelete
                if (itemToDelete != null && selectedDataTab == DataViewTabs.SCHEMA) {
                    DeleteSchemaConfirmationDialog(
                        item = itemToDelete,
                        dependencyCount = dependencyCount,
                        onConfirm = { viewModel.schemaViewModel.confirmDeleteSchema() },
                        onDismiss = { viewModel.schemaViewModel.clearDeleteSchemaRequest() }
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
                        onEditNodeClick = { item ->
                            // UPDATED
                            viewModel.editCreateViewModel.initiateNodeEdit(item)
                            viewModel.selectDataTab(DataViewTabs.EDIT)
                        },
                        onEditEdgeClick = { item ->
                            // UPDATED
                            viewModel.editCreateViewModel.initiateEdgeEdit(item)
                            viewModel.selectDataTab(DataViewTabs.EDIT)
                        },
                        onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onDeleteEdgeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onAddNodeClick = { viewModel.editCreateViewModel.initiateNodeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeClick = { viewModel.editCreateViewModel.initiateEdgeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
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
                        onEditNodeClick = { item ->
                            // UPDATED
                            viewModel.editCreateViewModel.initiateNodeSchemaEdit(item)
                            viewModel.selectDataTab(DataViewTabs.EDIT)
                        },
                        onEditEdgeClick = { item ->
                            // UPDATED
                            viewModel.editCreateViewModel.initiateEdgeSchemaEdit(item)
                            viewModel.selectDataTab(DataViewTabs.EDIT)
                        },
                        // UPDATED: Call SchemaViewModel to request delete
                        onDeleteNodeClick = { viewModel.schemaViewModel.requestDeleteSchema(it) },
                        onDeleteEdgeClick = { viewModel.schemaViewModel.requestDeleteSchema(it) },
                        onAddNodeSchemaClick = { viewModel.editCreateViewModel.initiateNodeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeSchemaClick = { viewModel.editCreateViewModel.initiateEdgeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddNodeClick = { viewModel.editCreateViewModel.initiateNodeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeClick = { viewModel.editCreateViewModel.initiateEdgeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) }
                    )
                    DataViewTabs.EDIT -> EditItemView(
                        editScreenState = editScreenState,
                        onSaveClick = onSave,
                        onCancelClick = onCancel,

                        // Node Creation
                        onNodeCreationSchemaSelected = { viewModel.editCreateViewModel.updateNodeCreationSchema(it) },
                        onNodeCreationPropertyChanged = { k, v -> viewModel.editCreateViewModel.updateNodeCreationProperty(k, v) },
                        onNodeCreationCreateClick = onSave, // Create and Save are the same action now

                        // Edge Creation
                        onEdgeCreationSchemaSelected = { viewModel.editCreateViewModel.updateEdgeCreationSchema(it) },
                        onEdgeCreationConnectionSelected = { viewModel.editCreateViewModel.updateEdgeCreationConnection(it) },
                        onEdgeCreationSrcSelected = { viewModel.editCreateViewModel.updateEdgeCreationSrc(it) },
                        onEdgeCreationDstSelected = { viewModel.editCreateViewModel.updateEdgeCreationDst(it) },
                        onEdgeCreationPropertyChanged = { k, v -> viewModel.editCreateViewModel.updateEdgeCreationProperty(k, v) },
                        onEdgeCreationCreateClick = onSave, // Create and Save are the same action now

                        // Node Schema Creation
                        onNodeSchemaCreationCreateClick = onSave, // Create and Save are the same action now
                        onNodeSchemaTableNameChange = { viewModel.editCreateViewModel.onNodeSchemaTableNameChange(it) },
                        onNodeSchemaPropertyChange = { i, p -> viewModel.editCreateViewModel.onNodeSchemaPropertyChange(i, p) },
                        onAddNodeSchemaProperty = { viewModel.editCreateViewModel.onAddNodeSchemaProperty() },
                        onRemoveNodeSchemaProperty = { viewModel.editCreateViewModel.onRemoveNodeSchemaProperty(it) },

                        // Edge Schema Creation
                        onEdgeSchemaCreationCreateClick = onSave, // Create and Save are the same action now
                        onEdgeSchemaTableNameChange = { viewModel.editCreateViewModel.onEdgeSchemaTableNameChange(it) },
                        onEdgeSchemaCreationAddConnection = { s, d -> viewModel.editCreateViewModel.onAddEdgeSchemaConnection(s, d) },
                        onEdgeSchemaCreationRemoveConnection = { viewModel.editCreateViewModel.onRemoveEdgeSchemaConnection(it) },
                        onEdgeSchemaPropertyChange = { i, p -> viewModel.editCreateViewModel.onEdgeSchemaPropertyChange(i, p) },
                        onAddEdgeSchemaProperty = { viewModel.editCreateViewModel.onAddEdgeSchemaProperty() },
                        onRemoveEdgeSchemaProperty = { viewModel.editCreateViewModel.onRemoveEdgeSchemaProperty(it) },

                        // Node Edit
                        onNodeEditPropertyChange = { k, v -> viewModel.editCreateViewModel.updateNodeEditProperty(k, v) },

                        // Edge Edit
                        onEdgeEditPropertyChange = { k, v -> viewModel.editCreateViewModel.updateEdgeEditProperty(k, v) },

                        // Node Schema Edit
                        onNodeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateNodeSchemaEditLabel(it) },
                        onNodeSchemaEditPropertyChange = { i, p -> viewModel.editCreateViewModel.updateNodeSchemaEditProperty(i, p) },
                        onNodeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditAddProperty() },
                        onNodeSchemaEditRemoveProperty = { viewModel.editCreateViewModel.updateNodeSchemaEditRemoveProperty(it) },

                        // Edge Schema Edit
                        onEdgeSchemaEditLabelChange = { viewModel.editCreateViewModel.updateEdgeSchemaEditLabel(it) },
                        onEdgeSchemaEditPropertyChange = { i, p -> viewModel.editCreateViewModel.updateEdgeSchemaEditProperty(i, p) },
                        onEdgeSchemaEditAddProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditAddProperty() },
                        onEdgeSchemaEditRemoveProperty = { viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveProperty(it) },
                        onEdgeSchemaEditAddConnection = { s, d -> viewModel.editCreateViewModel.updateEdgeSchemaEditAddConnection(s, d) },
                        onEdgeSchemaEditRemoveConnection = { viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveConnection(it) },
                        allNodeSchemaNames = schema?.nodeSchemas?.map { it.name } ?: emptyList()
                    )
                }
            }
        }
    }
}