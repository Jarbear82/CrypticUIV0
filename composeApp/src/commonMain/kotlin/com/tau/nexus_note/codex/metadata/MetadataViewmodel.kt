package com.tau.nexus_note.codex.metadata

import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.NodeDisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MetadataViewModel(
    private val repository: CodexRepository,
    private val viewModelScope: CoroutineScope
) {

    // --- State is now observed from the repository ---
    val nodeList = repository.nodeList
    val edgeList = repository.edgeList

    // --- This ViewModel still owns selection state (UI state) ---
    private val _primarySelectedItem = MutableStateFlow<Any?>(null)
    val primarySelectedItem = _primarySelectedItem.asStateFlow()

    private val _secondarySelectedItem = MutableStateFlow<Any?>(null)
    val secondarySelectedItem = _secondarySelectedItem.asStateFlow()

    // This state is just a marker for *what* to edit, used by EditCreateViewModel
    private val _itemToEdit = MutableStateFlow<Any?>(null)
    val itemToEdit = _itemToEdit.asStateFlow()

    // --- Public API ---

    fun listNodes() {
        repository.refreshNodes()
    }

    fun listEdges() {
        repository.refreshEdges()
    }

    fun listAll() {
        repository.refreshAll()
    }

    fun setItemToEdit(item: Any): Any? {
        // This just stores the item now, EditCreateViewModel will fetch full state
        _itemToEdit.value = item
        return item
    }

    fun selectItem(item: Any) {
        val currentPrimary = _primarySelectedItem.value
        val currentSecondary = _secondarySelectedItem.value

        when (item) {
            is NodeDisplayItem -> {
                if (item == currentPrimary) {
                    _primarySelectedItem.value = null
                } else if (item == currentSecondary) {
                    _secondarySelectedItem.value = null
                } else if (currentPrimary == null) {
                    _primarySelectedItem.value = item
                } else if (currentSecondary == null) {
                    _secondarySelectedItem.value = item
                } else {
                    _primarySelectedItem.value = item
                    _secondarySelectedItem.value = null
                }
            }
            is EdgeDisplayItem -> {
                _primarySelectedItem.value = item.src
                _secondarySelectedItem.value = item.dst
            }
            else -> { // Includes SchemaDefinitionItem
                _primarySelectedItem.value = item
                _secondarySelectedItem.value = null
            }
        }
    }

    fun deleteDisplayItem(item: Any) {
        // Delegate to repository
        when (item) {
            is NodeDisplayItem -> repository.deleteNode(item.id)
            is EdgeDisplayItem -> repository.deleteEdge(item.id)
        }
    }

    fun clearSelectedItem() {
        _itemToEdit.value = null
        _primarySelectedItem.value = null
        _secondarySelectedItem.value = null
    }
}