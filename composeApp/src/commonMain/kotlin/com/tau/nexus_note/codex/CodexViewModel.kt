package com.tau.nexus_note.viewmodels

import com.tau.nexus_note.SqliteDbService // IMPORTED: Changed from KuzuDBService
import com.tau.nexus_note.codex.graph.GraphViewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// UPDATED: Constructor now takes SqliteDbService
class CodexViewModel(private val dbService: SqliteDbService) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    // UPDATED: Re-ordered initialization and removed circular dependency.
    // 1. Create schemaViewModel first (it no longer depends on metadataViewModel in constructor)
    val schemaViewModel = SchemaViewModel(dbService, viewModelScope)

    // 2. Create metadataViewModel, passing in schemaViewModel
    val metadataViewModel = MetadataViewModel(dbService, viewModelScope, schemaViewModel)

    // 3. Set the dependency for schemaViewModel *after* metadataViewModel is created
    init {
        schemaViewModel.setDependencies(metadataViewModel)
    }
    // --- END UPDATE ---


    // REMOVED: QueryViewModel is no longer needed
    // val queryViewModel = QueryViewModel(dbService, viewModelScope, metadataViewModel)

    // UPDATED: This ViewModel is also instantiated with the SqliteDbService
    val editCreateViewModel = EditCreateViewModel(dbService, viewModelScope, schemaViewModel, metadataViewModel)

    // ADDED: Create the GraphViewModel, passing the scope, metadata VM, edit VM, and tab switch lambda
    val graphViewModel = GraphViewmodel(
        viewModelScope = viewModelScope,
        metadataViewModel = metadataViewModel,
        editCreateViewModel = editCreateViewModel,
        onSwitchToEditTab = { selectDataTab(DataViewTabs.EDIT) }
    )


    private val _selectedDataTab = MutableStateFlow(DataViewTabs.METADATA)
    val selectedDataTab = _selectedDataTab.asStateFlow()

    fun selectDataTab(tab: DataViewTabs) {
        // When user manually switches tabs away from EDIT, treat it as a "Cancel"
        if (_selectedDataTab.value == DataViewTabs.EDIT && tab != DataViewTabs.EDIT) {
            println("DEBUG: Manual tab switch away from EDIT detected. Clearing state.")
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
        // ADDED: Clear the graph view model to stop its simulation loop
        graphViewModel.onCleared()
        // UPDATED: Call close() directly on the SqliteDbService
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
