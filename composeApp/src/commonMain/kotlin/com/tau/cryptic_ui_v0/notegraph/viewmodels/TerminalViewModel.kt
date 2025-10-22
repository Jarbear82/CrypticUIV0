package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.KuzuDBService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TerminalViewModel(dbService: KuzuDBService) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    val schemaViewModel = SchemaViewModel(dbService, viewModelScope)
    val metadataViewModel = MetadataViewModel(dbService, viewModelScope)
    val queryViewModel = QueryViewModel(dbService, viewModelScope, metadataViewModel)
    val creationViewModel = CreationViewModel(dbService, viewModelScope, schemaViewModel, metadataViewModel)


    private val _selectedDataTab = MutableStateFlow(DataViewTabs.METADATA)
    val selectedDataTab = _selectedDataTab.asStateFlow()

    fun selectDataTab(tab: DataViewTabs) {
        _selectedDataTab.value = tab
    }

    private val _selectedViewTab = MutableStateFlow(ViewTabs.QUERY)
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
    QUERY(0),
    GRAPH(1)
}