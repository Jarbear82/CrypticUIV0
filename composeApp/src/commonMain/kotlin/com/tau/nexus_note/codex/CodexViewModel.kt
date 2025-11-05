package com.tau.nexus_note.codex

import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.SqliteDbService
import com.tau.nexus_note.codex.crud.EditCreateViewModel
import com.tau.nexus_note.codex.graph.GraphViewmodel
import com.tau.nexus_note.codex.metadata.MetadataViewModel
import com.tau.nexus_note.codex.schema.SchemaViewModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CodexViewModel(private val dbService: SqliteDbService) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    // 1. Create the Repository, passing it the scope and dbService
    val repository = CodexRepository(dbService, viewModelScope)

    // 2. Create child ViewModels, passing them the *repository*
    val schemaViewModel = SchemaViewModel(repository, viewModelScope)
    val metadataViewModel = MetadataViewModel(repository, viewModelScope)
    val editCreateViewModel = EditCreateViewModel(repository, viewModelScope, schemaViewModel, metadataViewModel)

    // 3. GraphViewModel now observes MetadataViewModel, which observes the repo.
    // This dependency chain is fine.
    val graphViewModel = GraphViewmodel(
        viewModelScope = viewModelScope,
        metadataViewModel = metadataViewModel,
        editCreateViewModel = editCreateViewModel,
        onSwitchToEditTab = { selectDataTab(DataViewTabs.EDIT) }
    )

    init {
        // Trigger initial data load
        repository.refreshAll()
    }

    private val _selectedDataTab = MutableStateFlow(DataViewTabs.METADATA)
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