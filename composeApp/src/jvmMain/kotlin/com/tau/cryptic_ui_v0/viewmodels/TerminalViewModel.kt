package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.KuzuRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TerminalViewModel(repository: KuzuRepository) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    val schemaViewModel = SchemaViewModel(repository, viewModelScope)
    val metadataViewModel = MetadataViewModel(repository, viewModelScope, schemaViewModel)
    val queryViewModel = QueryViewModel(repository, viewModelScope, metadataViewModel)
    val creationViewModel = CreationViewModel(repository, viewModelScope, schemaViewModel, metadataViewModel)


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