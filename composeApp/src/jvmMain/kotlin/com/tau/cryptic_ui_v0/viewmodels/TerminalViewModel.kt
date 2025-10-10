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


    private val _selectedTab = MutableStateFlow(TerminalViewTabs.METADATA)
    val selectedTab = _selectedTab.asStateFlow()

    fun selectTab(tab: TerminalViewTabs) {
        _selectedTab.value = tab
    }

    fun onCleared() {
        metadataViewModel.onCleared()
    }
}

enum class TerminalViewTabs(val value: Int) {
    METADATA(0),
    SCHEMA(1),
    EDIT(2)
}