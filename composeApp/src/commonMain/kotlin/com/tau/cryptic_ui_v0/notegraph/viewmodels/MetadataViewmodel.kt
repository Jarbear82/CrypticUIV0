package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MetadataViewModel(
    private val dbService: KuzuDBService,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel // Added to refresh schema
) {
    private val _dbMetaData = MutableStateFlow<DBMetaData?>(null)
    val dbMetaData = _dbMetaData.asStateFlow()

    private val _nodeList = MutableStateFlow<List<NodeDisplayItem>>(emptyList())
    val nodeList = _nodeList.asStateFlow()

    private val _edgeList = MutableStateFlow<List<EdgeDisplayItem>>(emptyList())
    val edgeList = _edgeList.asStateFlow()

    // This stores the ORIGINAL item being edited
    private val _itemToEdit = MutableStateFlow<Any?>(null)
    val itemToEdit = _itemToEdit.asStateFlow()

    private val _primarySelectedItem = MutableStateFlow<Any?>(null)
    val primarySelectedItem = _primarySelectedItem.asStateFlow()

    private val _secondarySelectedItem = MutableStateFlow<Any?>(null)
    val secondarySelectedItem = _secondarySelectedItem.asStateFlow()


    init {
        viewModelScope.launch {
            _dbMetaData.value = dbService.getDBMetaData()
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
            _nodeList.value = listNodes(dbService)
        }
    }

    fun listEdges() {
        viewModelScope.launch {
            val edges = listEdges(dbService)
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

    /**
     * Fetches the full item to edit and stores it in _itemToEdit.
     * This stored item is the "original" version for comparison.
     * Returns the fetched item so the caller can pass it to EditCreateViewModel.
     */
    suspend fun setItemToEdit(item: Any): Any? {
        val fetchedItem = when (item) {
            is NodeDisplayItem -> getNode(dbService, item)
            is EdgeDisplayItem -> getEdge(dbService, item)
            is SchemaNode, is SchemaEdge, is String -> item
            else -> null
        }
        _itemToEdit.value = fetchedItem
        println("DEBUG: Set item to edit (original): $fetchedItem")
        return fetchedItem
    }

    /**
     * Saves the edited item by comparing the original (from _itemToEdit)
     * with the modified version (passed as an argument).
     */
    fun saveEditedItem(editedState: Any?, onFinished: () -> Unit) {
        viewModelScope.launch {
            val originalState = _itemToEdit.value
            println("DEBUG: Save button clicked.")
            println("DEBUG: Original state: $originalState")
            println("DEBUG: Edited state: $editedState")
            when (originalState) {
                is NodeTable -> {
                    val editedNode = editedState as? NodeTable
                    if (editedNode != null) {
                        saveNodeChanges(originalState, editedNode)
                        listNodes() // Refresh list
                    }
                }
                is EdgeTable -> {
                    val editedEdge = editedState as? EdgeTable
                    if (editedEdge != null) {
                        saveEdgeChanges(originalState, editedEdge)
                        listEdges() // Refresh list
                    }
                }
                is SchemaNode -> {
                    val editedSchema = editedState as? NodeSchemaEditState
                    if (editedSchema != null) {
                        println("DEBUG: Original and Edited states are valid. Calling saveNodeSchemaChanges...")
                        saveNodeSchemaChanges(originalState, editedSchema)
                        schemaViewModel.showSchema() // Refresh schema
                    } else {
                        println("DEBUG: Edited state was null or not a NodeSchemaEditState.")
                    }
                }
                is SchemaEdge -> {
                    val editedSchema = editedState as? EdgeSchemaEditState
                    if (editedSchema != null) {
                        saveEdgeSchemaChanges(originalState, editedSchema)
                        schemaViewModel.showSchema() // Refresh schema
                    }
                }
                else -> {
                    println("DEBUG: Original state was null or of unknown type.")
                }
            }
            clearSelectedItem() // Clear original state
            onFinished()        // Call the onFinished lambda
        }
    }

    private suspend fun saveNodeChanges(original: NodeTable, edited: NodeTable) {
        val pk = original.properties.first { it.isPrimaryKey }
        val pkDisplay = DisplayItemProperty(pk.key, pk.value)
        val changedProperties = edited.properties.filter { it.valueChanged && !it.isPrimaryKey }

        println("DEBUG: Found ${changedProperties.size} changed properties for node '${original.label}'.")

        if (changedProperties.isNotEmpty()) {
            println("DEBUG: Executing alterNodeProperties.")
            alterNodeProperties(dbService, original.label, pkDisplay, changedProperties)
        }
    }

    private suspend fun saveEdgeChanges(original: EdgeTable, edited: EdgeTable) {
        val changedProperties = edited.properties?.filter { it.valueChanged } ?: emptyList()
        println("DEBUG: Found ${changedProperties.size} changed properties for edge '${original.label}'.")
        if (changedProperties.isNotEmpty()) {
            println("DEBUG: Executing alterEdgeProperties.")
            val edgeDisplay = EdgeDisplayItem(original.label, original.src, original.dst)
            alterEdgeProperties(dbService, edgeDisplay, changedProperties)
        }
    }

    private suspend fun saveNodeSchemaChanges(original: SchemaNode, edited: NodeSchemaEditState) = coroutineScope {
        val originalLabel = original.label
        val currentLabel = edited.currentLabel
        var activeLabel = originalLabel

        // Handle table rename last, as it changes the reference
        if (originalLabel != currentLabel) {
            println("DEBUG: Renaming table: $originalLabel -> $currentLabel")
            alterNodeSchemaRenameTable(dbService, originalLabel, currentLabel)
            activeLabel = currentLabel // Use the new label for all subsequent operations
        }

        // Handle property changes first
        val operations = edited.properties.map { prop ->
            async {
                when {
                    prop.isDeleted -> {
                        println("DEBUG: Dropping property: ${prop.originalKey} from table $activeLabel")
                        alterNodeSchemaDropProperty(dbService, activeLabel, prop.originalKey)
                    }
                    prop.isNew -> {
                        println("DEBUG: Adding property: ${prop.key} ${prop.valueDataType} to table $activeLabel")
                        alterNodeSchemaAddProperty(dbService, activeLabel, prop.key, prop.valueDataType)
                    }
                    prop.originalKey != prop.key -> {
                        println("DEBUG: Renaming property: ${prop.originalKey} -> ${prop.key} in table $activeLabel")
                        alterNodeSchemaRenameProperty(dbService, activeLabel, prop.originalKey, prop.key)
                    }
                    // TODO: Add data type change support when Kuzu supports it
                }
            }
        }
        operations.awaitAll()
    }

    private suspend fun saveEdgeSchemaChanges(original: SchemaEdge, edited: EdgeSchemaEditState) = coroutineScope {
        val originalLabel = original.label
        val currentLabel = edited.currentLabel
        var activeLabel = originalLabel

        // Rename table first
        if (originalLabel != currentLabel) {
            println("DEBUG: Renaming edge table: $originalLabel -> $currentLabel")
            alterEdgeSchemaRenameTable(dbService, originalLabel, currentLabel)
            activeLabel = currentLabel
        }

        // Handle property changes after
        val operations = edited.properties.map { prop ->
            async {
                when {
                    prop.isDeleted -> {
                        println("DEBUG: Dropping edge property: ${prop.originalKey} from table $activeLabel")
                        alterEdgeSchemaDropProperty(dbService, activeLabel, prop.originalKey)
                    }
                    prop.isNew -> {
                        println("DEBUG: Adding edge property: ${prop.key} ${prop.valueDataType} to table $activeLabel")
                        alterEdgeSchemaAddProperty(dbService, activeLabel, prop.key, prop.valueDataType)
                    }
                    prop.originalKey != prop.key -> {
                        println("DEBUG: Renaming edge property: ${prop.originalKey} -> ${prop.key} in table $activeLabel")
                        alterEdgeSchemaRenameProperty(dbService, activeLabel, prop.originalKey, prop.key)
                    }
                }
            }
        }
        operations.awaitAll()
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
                    deleteNode(dbService, item)
                    _nodeList.update { list -> list.filterNot { it == item } }
                    // Also remove any edges connected to the deleted node
                    _edgeList.update { list -> list.filterNot { it.src == item || it.dst == item } }
                }
                is EdgeDisplayItem -> {
                    deleteEdge(dbService, item)
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
        dbService.close()
    }
}