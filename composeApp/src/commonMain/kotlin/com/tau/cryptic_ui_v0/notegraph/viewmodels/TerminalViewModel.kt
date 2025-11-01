package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.SqliteDbService // IMPORTED: Changed from KuzuDBService
import com.tau.cryptic_ui_v0.notegraph.graph.GraphViewmodel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// UPDATED: Constructor now takes SqliteDbService
class TerminalViewModel(private val dbService: SqliteDbService) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    // UPDATED: These ViewModels are now instantiated with the SqliteDbService
    val schemaViewModel = SchemaViewModel(dbService, viewModelScope)
    val metadataViewModel = MetadataViewModel(dbService, viewModelScope, schemaViewModel)

    // REMOVED: QueryViewModel is no longer needed
    // val queryViewModel = QueryViewModel(dbService, viewModelScope, metadataViewModel)

    // UPDATED: This ViewModel is also instantiated with the SqliteDbService
    val editCreateViewModel = EditCreateViewModel(dbService, viewModelScope, schemaViewModel, metadataViewModel)

    // ADDED: Create the GraphViewModel, passing the scope and metadata VM
    val graphViewModel = GraphViewmodel(viewModelScope, metadataViewModel)


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
