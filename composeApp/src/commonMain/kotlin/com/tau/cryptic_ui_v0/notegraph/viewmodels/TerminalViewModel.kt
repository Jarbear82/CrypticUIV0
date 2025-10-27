package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.KuzuDBService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TerminalViewModel(dbService: KuzuDBService) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    val schemaViewModel = SchemaViewModel(dbService, viewModelScope)
    // MetadataViewModel now needs a reference to SchemaViewModel to refresh it after schema edits
    val metadataViewModel = MetadataViewModel(dbService, viewModelScope, schemaViewModel)
    val queryViewModel = QueryViewModel(dbService, viewModelScope, metadataViewModel)
    // Renamed CreationViewModel to EditCreateViewModel
    val editCreateViewModel = EditCreateViewModel(dbService, viewModelScope, schemaViewModel, metadataViewModel)


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

    private val _selectedViewTab = MutableStateFlow(ViewTabs.LIST) // Default to LIST
    val selectedViewTab = _selectedViewTab.asStateFlow()

    fun selectViewTab(tab: ViewTabs) {
        _selectedViewTab.value = tab
    }

    fun onCleared() {
        metadataViewModel.onCleared()
    }
}

enum class DataViewTabs(val value: Int) {
    METADATA(0),
    SCHEMA(1),
    EDIT(2)
}

enum class ViewTabs(val value: Int) {
    LIST(0), // Renamed from QUERY
    GRAPH(1)
}