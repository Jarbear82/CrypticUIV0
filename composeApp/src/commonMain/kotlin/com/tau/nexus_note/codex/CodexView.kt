package com.tau.nexus_note.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.codex.crud.DeleteSchemaConfirmationDialog
import com.tau.nexus_note.codex.crud.update.EditItemView
import com.tau.nexus_note.codex.graph.DetangleSettingsDialog
import com.tau.nexus_note.codex.graph.GraphView
import com.tau.nexus_note.codex.metadata.MetadataView
import com.tau.nexus_note.codex.schema.SchemaView
import com.tau.nexus_note.views.ListView
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CodexView(viewModel: CodexViewModel) {
// Observe state from repository via the ViewModels
    val schema by viewModel.schemaViewModel.schema.collectAsState()
// Graph data (full list)
    val nodes by viewModel.metadataViewModel.nodeList.collectAsState()
    val edges by viewModel.metadataViewModel.edgeList.collectAsState()

// NEW: Paginated data for ListView
    val paginatedNodes by viewModel.metadataViewModel.paginatedNodes.collectAsState()
    val paginatedEdges by viewModel.metadataViewModel.paginatedEdges.collectAsState()


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

    val showDetangleDialog by graphViewModel.showDetangleDialog.collectAsState()

    val nodeSearchText by viewModel.metadataViewModel.nodeSearchText.collectAsState()
    val edgeSearchText by viewModel.metadataViewModel.edgeSearchText.collectAsState()
    val nodeSchemaSearchText by viewModel.schemaViewModel.nodeSchemaSearchText.collectAsState()
    val edgeSchemaSearchText by viewModel.schemaViewModel.edgeSchemaSearchText.collectAsState()

    val nodeVisibility by viewModel.metadataViewModel.nodeVisibility.collectAsState()
    val edgeVisibility by viewModel.metadataViewModel.edgeVisibility.collectAsState()
    val schemaVisibility by viewModel.schemaViewModel.schemaVisibility.collectAsState()


    LaunchedEffect(viewModel.editCreateViewModel) {
        viewModel.editCreateViewModel.navigationEventFlow.collectLatest {
            viewModel.selectDataTab(DataViewTabs.SCHEMA)
        }
    }

// --- LaunchedEffect to control graph simulation ---
    LaunchedEffect(selectedViewTab, graphViewModel) {

        if (selectedViewTab == ViewTabs.GRAPH) {
            graphViewModel.startSimulation()
        } else {
            graphViewModel.stopSimulation()
        }
    }


    val onSave: () -> Unit = {
        viewModel.editCreateViewModel.saveCurrentState()
        // After save, refresh paginated lists
        viewModel.metadataViewModel.refreshPaginatedLists()
    }

    val onCancel: () -> Unit = {
        viewModel.editCreateViewModel.cancelAllEditing()
        viewModel.metadataViewModel.clearSelectedItem()
        viewModel.selectDataTab(DataViewTabs.SCHEMA)
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ------------------ Left panel for controls and query results --------------------------
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                PrimaryTabRow(selectedTabIndex = selectedViewTab.value) {
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
                            // MODIFIED: Pass paginated lists and handlers
                            paginatedNodes = paginatedNodes,
                            paginatedEdges = paginatedEdges,
                            onLoadMoreNodes = viewModel.metadataViewModel::loadMoreNodes,
                            onLoadMoreEdges = viewModel.metadataViewModel::loadMoreEdges,
                            // --- Unchanged props ---
                            primarySelectedItem = primarySelectedItem,
                            secondarySelectedItem = secondarySelectedItem,
                            onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                            onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                            onEditNodeClick = { item ->
                                viewModel.editCreateViewModel.initiateNodeEdit(item)
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onEditEdgeClick = { item ->
                                viewModel.editCreateViewModel.initiateEdgeEdit(item)
                                viewModel.selectDataTab(DataViewTabs.EDIT)
                            },
                            onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                            onDeleteEdgeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                            onAddNodeClick = { viewModel.editCreateViewModel.initiateNodeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                            onAddEdgeClick = { viewModel.editCreateViewModel.initiateEdgeCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                            nodeSearchText = nodeSearchText,
                            onNodeSearchChange = viewModel.metadataViewModel::onNodeSearchChange,
                            edgeSearchText = edgeSearchText,
                            onEdgeSearchChange = viewModel.metadataViewModel::onEdgeSearchChange,
                            nodeVisibility = nodeVisibility,
                            onToggleNodeVisibility = viewModel.metadataViewModel::toggleNodeVisibility,
                            edgeVisibility = edgeVisibility,
                            onToggleEdgeVisibility = viewModel.metadataViewModel::toggleEdgeVisibility
                        )
                    }
                    ViewTabs.GRAPH -> {
                        // Pass data and callbacks into GraphView
                        graphViewModel.let {
                            val nodesState by it.graphNodes.collectAsState()
                            val edgesState by it.graphEdges.collectAsState()
                            val primary = primarySelectedItem
                            val secondary = secondarySelectedItem

                            // Get selected IDs
                            val primaryId = (primary as? NodeDisplayItem)?.id
                            val secondaryId = (secondary as? NodeDisplayItem)?.id

                            GraphView(
                                viewModel = it,
                                nodes = nodesState,
                                edges = edgesState,
                                primarySelectedId = primaryId,
                                secondarySelectedId = secondaryId,
                                onNodeTap = { nodeId ->
                                    // Find the NodeDisplayItem and select it
                                    val node = viewModel.metadataViewModel.nodeList.value.find { it.id == nodeId }
                                    if (node != null) {
                                        viewModel.metadataViewModel.selectItem(node)
                                    }
                                },
                                onAddNodeClick = {
                                    viewModel.editCreateViewModel.initiateNodeCreation()
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                },
                                onAddEdgeClick = {
                                    viewModel.editCreateViewModel.initiateEdgeCreation()
                                    viewModel.selectDataTab(DataViewTabs.EDIT)
                                },
                                onDetangleClick = {
                                    it.onShowDetangleDialog()
                                }
                            )
                        }
                    }
                }
            }


            Column(
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {

                val itemToDelete = schemaToDelete
                if (itemToDelete != null && selectedDataTab == DataViewTabs.SCHEMA) {
                    DeleteSchemaConfirmationDialog(
                        item = itemToDelete,
                        dependencyCount = dependencyCount,
                        onConfirm = { viewModel.schemaViewModel.confirmDeleteSchema() },
                        onDismiss = { viewModel.schemaViewModel.clearDeleteSchemaRequest() }
                    )
                }

                PrimaryTabRow(selectedTabIndex = selectedDataTab.value) {
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
                        // MODIFIED: Pass paginated lists
                        nodes = paginatedNodes,
                        edges = paginatedEdges,
                        // --- Unchanged props ---
                        primarySelectedItem = primarySelectedItem,
                        secondarySelectedItem = secondarySelectedItem,
                        onNodeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEdgeClick = { viewModel.metadataViewModel.selectItem(it) },
                        onEditNodeClick = { item ->
                            viewModel.editCreateViewModel.initiateNodeEdit(item)
                            viewModel.selectDataTab(DataViewTabs.EDIT)
                        },
                        onEditEdgeClick = { item ->
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
                            viewModel.editCreateViewModel.initiateNodeSchemaEdit(item)
                            viewModel.selectDataTab(DataViewTabs.EDIT)
                        },
                        onEditEdgeClick = { item ->
                            viewModel.editCreateViewModel.initiateEdgeSchemaEdit(item)
                            viewModel.selectDataTab(DataViewTabs.EDIT)
                        },
                        onDeleteNodeClick = { viewModel.schemaViewModel.requestDeleteSchema(it) },
                        onDeleteEdgeClick = { viewModel.schemaViewModel.requestDeleteSchema(it) },
                        onAddNodeSchemaClick = { viewModel.editCreateViewModel.initiateNodeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddEdgeSchemaClick = { viewModel.editCreateViewModel.initiateEdgeSchemaCreation(); viewModel.selectDataTab(DataViewTabs.EDIT) },
                        onAddNodeClick = { item ->
                            viewModel.editCreateViewModel.initiateNodeCreation(item)
                            viewModel.selectDataTab(DataViewTabs.EDIT)
                        },
                        onAddEdgeClick = { schema, connection ->
                            viewModel.editCreateViewModel.initiateEdgeCreation(schema, connection)
                            viewModel.selectDataTab(DataViewTabs.EDIT)
                        },
                        nodeSchemaSearchText = nodeSchemaSearchText,
                        onNodeSchemaSearchChange = viewModel.schemaViewModel::onNodeSchemaSearchChange,
                        edgeSchemaSearchText = edgeSchemaSearchText,
                        onEdgeSchemaSearchChange = viewModel.schemaViewModel::onEdgeSchemaSearchChange,
                        schemaVisibility = schemaVisibility,
                        onToggleSchemaVisibility = viewModel.schemaViewModel::toggleSchemaVisibility
                    )
                    DataViewTabs.EDIT -> EditItemView(
                        editScreenState = editScreenState,
                        onSaveClick = onSave,
                        onCancelClick = onCancel,

                        // Node Creation
                        onNodeCreationSchemaSelected = { viewModel.editCreateViewModel.updateNodeCreationSchema(it) },
                        onNodeCreationPropertyChanged = { k, v -> viewModel.editCreateViewModel.updateNodeCreationProperty(k, v) },

                        // Edge Creation
                        onEdgeCreationSchemaSelected = { viewModel.editCreateViewModel.updateEdgeCreationSchema(it) },
                        onEdgeCreationConnectionSelected = { viewModel.editCreateViewModel.updateEdgeCreationConnection(it) },
                        onEdgeCreationSrcSelected = { viewModel.editCreateViewModel.updateEdgeCreationSrc(it) },
                        onEdgeCreationDstSelected = { viewModel.editCreateViewModel.updateEdgeCreationDst(it) },
                        onEdgeCreationPropertyChanged = { k, v -> viewModel.editCreateViewModel.updateEdgeCreationProperty(k, v) },

                        // Node Schema Creation
                        onNodeSchemaTableNameChange = { viewModel.editCreateViewModel.onNodeSchemaTableNameChange(it) },
                        onNodeSchemaPropertyChange = { i, p -> viewModel.editCreateViewModel.onNodeSchemaPropertyChange(i, p) },
                        onAddNodeSchemaProperty = { viewModel.editCreateViewModel.onAddNodeSchemaProperty() },
                        onRemoveNodeSchemaProperty = { viewModel.editCreateViewModel.onRemoveNodeSchemaProperty(it) },

                        // Edge Schema Creation
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
                        onEdgeSchemaEditRemoveConnection = { viewModel.editCreateViewModel.updateEdgeSchemaEditRemoveConnection(it) }
                    )
                }
            }
        }

        // This dialog floats over everything
        if (showDetangleDialog) {
            graphViewModel.let { gvm ->
                DetangleSettingsDialog(
                    onDismiss = { gvm.onDismissDetangleDialog() },
                    onDetangle = { alg, params ->
                        gvm.startDetangle(alg, params)
                    }
                )
            }
        }
    }


}