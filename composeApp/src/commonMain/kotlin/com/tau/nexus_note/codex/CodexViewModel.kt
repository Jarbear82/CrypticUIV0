package com.tau.nexus_note.codex

import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.SqliteDbService
import com.tau.nexus_note.codex.crud.EditCreateViewModel
import com.tau.nexus_note.codex.graph.GraphViewmodel
import com.tau.nexus_note.codex.metadata.MetadataViewModel
import com.tau.nexus_note.codex.schema.SchemaViewModel
import com.tau.nexus_note.settings.SettingsData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// --- UPDATED ---
// Constructor now accepts the settingsFlow
class CodexViewModel(
    private val dbService: SqliteDbService,
    private val settingsFlow: StateFlow<SettingsData>
) {
// --- END UPDATE ---

    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    // 1. Create the Repository, passing it the scope and dbService
    val repository = CodexRepository(dbService, viewModelScope)

    // 2. Create child ViewModels, passing them the *repository*
    val schemaViewModel = SchemaViewModel(repository, viewModelScope)
    val metadataViewModel = MetadataViewModel(repository, viewModelScope)
    val editCreateViewModel = EditCreateViewModel(repository, viewModelScope, schemaViewModel, metadataViewModel)

    // --- UPDATED ---
    // Pass the settingsFlow to the GraphViewmodel
    val graphViewModel = GraphViewmodel(
        viewModelScope = viewModelScope,
        settingsFlow = settingsFlow
    )
    // --- END UPDATE ---

    // Expose Repository Error Flow
    val errorFlow = repository.errorFlow
    fun clearError() = repository.clearError()

    init {
        // Trigger initial data load
        repository.refreshAll()

        // Combine lists with visibility state
        viewModelScope.launch {
            combine(
                metadataViewModel.nodeList,
                metadataViewModel.edgeList,
                metadataViewModel.nodeVisibility,
                metadataViewModel.edgeVisibility
            ) { nodes, edges, nodeViz, edgeViz ->

                // Filter nodes based on their own visibility
                val visibleNodes = nodes.filter { nodeViz[it.id] ?: true }
                val visibleNodeIds = visibleNodes.map { it.id }.toSet()

                // Filter edges based on their own visibility AND their nodes' visibility
                val visibleEdges = edges.filter {
                    (edgeViz[it.id] ?: true) &&
                            (it.src.id in visibleNodeIds) &&
                            (it.dst.id in visibleNodeIds)
                }

                // Pass the *filtered* lists to the graph
                visibleNodes to visibleEdges

            }.collectLatest { (visibleNodes, visibleEdges) ->
                graphViewModel.updateGraphData(visibleNodes, visibleEdges)
            }
        }

        // Correlate Schema visibility with Item visibility
        viewModelScope.launch {
            schemaViewModel.schemaVisibility.collectLatest { schemaVizMap ->
                schemaVizMap.forEach { (schemaId, isVisible) ->
                    // Find if this schema is for nodes or edges
                    val isNodeSchema = repository.schema.value?.nodeSchemas?.any { it.id == schemaId } ?: false
                    if (isNodeSchema) {
                        metadataViewModel.setNodeVisibilityForSchema(schemaId, isVisible)
                    } else {
                        metadataViewModel.setEdgeVisibilityForSchema(schemaId, isVisible)
                    }
                }
            }
        }
    }

    private val _selectedDataTab = MutableStateFlow(DataViewTabs.SCHEMA)
    val selectedDataTab = _selectedDataTab.asStateFlow()

    fun selectDataTab(tab: DataViewTabs) {
        if (_selectedDataTab.value == DataViewTabs.EDIT && tab != DataViewTabs.EDIT) {
            editCreateViewModel.cancelAllEditing()
            metadataViewModel.clearSelectedItem()
        }
        _selectedDataTab.value = tab
    }

    private val _selectedViewTab = MutableStateFlow(ViewTabs.GRAPH)
    val selectedViewTab = _selectedViewTab.asStateFlow()

    fun selectViewTab(tab: ViewTabs) {
        _selectedViewTab.value = tab
    }

    fun onCleared() {
        graphViewModel.onCleared()
        dbService.close()
    }
}

enum class DataViewTabs(val value: Int) {
    METADATA(0),
    SCHEMA(1),
    EDIT(2)
}

enum class ViewTabs(val value: Int) {
    LIST(0),
    GRAPH(1)
}