package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MetadataViewModel(
    private val repository: KuzuRepository,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel
) {
    private val _dbMetaData = MutableStateFlow<DBMetaData?>(null)
    val dbMetaData = _dbMetaData.asStateFlow()

    private val _nodeList = MutableStateFlow<List<NodeDisplayItem>>(emptyList())
    val nodeList = _nodeList.asStateFlow()

    private val _edgeList = MutableStateFlow<List<EdgeDisplayItem>>(emptyList())
    val edgeList = _edgeList.asStateFlow()

    private val _itemToEdit = MutableStateFlow<Any?>(null)
    val itemToEdit = _itemToEdit.asStateFlow()

    private val _primarySelectedItem = MutableStateFlow<Any?>(null)
    val primarySelectedItem = _primarySelectedItem.asStateFlow()

    private val _secondarySelectedItem = MutableStateFlow<Any?>(null)
    val secondarySelectedItem = _secondarySelectedItem.asStateFlow()


    init {
        viewModelScope.launch {
            repository.initialize()
            _dbMetaData.value = repository.getDBMetaData()
        }
    }

    fun addNodes(nodes: Set<NodeDisplayItem>) {
        if (nodes.isNotEmpty()) {
            println("Updating Nodes")
            _nodeList.update { (it + nodes).distinct() }
        }
    }

    fun addEdges(edges: Set<EdgeDisplayItem>) {
        if (edges.isNotEmpty()) {
            println("Updating Edges")
            _edgeList.update { (it + edges).distinct() }
        }
    }

    fun listNodes() {
        viewModelScope.launch {
            _nodeList.value = listNodes(repository)
        }
    }

    fun listEdges() {
        viewModelScope.launch {
            val edges = listEdges(repository)
            val nodesFromEdges = mutableSetOf<NodeDisplayItem>()
            edges.forEach {
                nodesFromEdges.add(it.src)
                nodesFromEdges.add(it.dst)
            }
            _edgeList.value = edges
            _nodeList.update { (it + nodesFromEdges).distinct() }
        }
    }


    fun listAll() {
        viewModelScope.launch {
            listNodes()
            listEdges()
        }
    }

    fun setItemToEdit(item: Any) {
        viewModelScope.launch {
            _itemToEdit.value = when (item) {
                is NodeDisplayItem -> getNode(repository, item)
                is EdgeDisplayItem -> getEdge(repository, item)
                is SchemaNode, is SchemaEdge, is String -> item
                else -> null
            }
        }
    }

    fun selectItem(item: Any) {
        val currentPrimary = _primarySelectedItem.value
        val currentSecondary = _secondarySelectedItem.value

        when (item) {
            is NodeDisplayItem -> {
                // Case 1: The clicked item is already primary -> Deselect it.
                if (item == currentPrimary) {
                    _primarySelectedItem.value = null
                }
                // Case 2: The clicked item is already secondary -> Deselect it.
                else if (item == currentSecondary) {
                    _secondarySelectedItem.value = null
                }
                // Case 3: Nothing is primary yet -> Make this item primary.
                else if (currentPrimary == null) {
                    _primarySelectedItem.value = item
                }
                // Case 4: Primary is set, but secondary is empty -> Make this item secondary.
                else if (currentSecondary == null) {
                    _secondarySelectedItem.value = item
                }
                // Case 5: Both primary and secondary are already set -> Replace primary and clear secondary.
                else {
                    _primarySelectedItem.value = item
                    _secondarySelectedItem.value = null
                }
            }
            is EdgeDisplayItem -> {
                // When an edge is selected, set its src and dst as primary and secondary.
                _primarySelectedItem.value = item.src
                _secondarySelectedItem.value = item.dst
            }
            else -> {
                // For any other type, set it as primary and clear secondary.
                _primarySelectedItem.value = item
                _secondarySelectedItem.value = null
            }
        }
    }

    fun deleteDisplayItem(item: Any) {
        viewModelScope.launch {
            when (item) {
                is NodeDisplayItem -> {
                    deleteNode(repository, item)
                    _nodeList.update { list -> list.filterNot { it == item } }
                    // Also remove any edges connected to the deleted node
                    _edgeList.update { list -> list.filterNot { it.src == item || it.dst == item } }
                }
                is EdgeDisplayItem -> {
                    deleteEdge(repository, item)
                    _edgeList.update { list -> list.filterNot { it == item } }
                }
            }
        }
    }

    fun clearNodeList() {
        _nodeList.value = emptyList()
    }

    fun clearEdgeList() {
        _edgeList.value = emptyList()
    }

    fun clearSelectedItem() {
        _itemToEdit.value = null
        _primarySelectedItem.value = null
        _secondarySelectedItem.value = null
    }


    fun onCleared() {
        repository.close()
    }
}