package com.tau.nexus_note.viewmodels

import com.tau.nexus_note.* // Imports new data classes: NodeDisplayItem, EdgeDisplayItem, etc.
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MetadataViewModel(
    private val dbService: SqliteDbService,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel
) {
    // REMOVED: DBMetaData logic. This is now handled by MainViewModel
    // private val _dbMetaData = MutableStateFlow<DBMetaData?>(null)
    // val dbMetaData = _dbMetaData.asStateFlow()

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

    // ADDED: JSON serializer instance
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true // Ensure all fields are present
    }

    // REMOVED: init block that fetched Kuzu metadata

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

    // ADDED: private suspend function for node-fetching logic
    private suspend fun fetchNodes() {
        try {
            // UPDATED: Robust schema loading
            // Ensure schema is loaded to map IDs to names
            var schemaData = schemaViewModel.schema.value
            if (schemaData == null) {
                println("Schema not yet loaded. Calling and awaiting showSchema() first.")
                schemaViewModel.showSchema() // Make sure schema is loaded
                schemaData = schemaViewModel.schema.value // Refetch the value
            }

            // Now schemaData might be non-null (or null if DB is empty, which is fine)
            val schemaMap = schemaData?.nodeSchemas?.associateBy { it.id } ?: emptyMap()

            if (schemaData == null) {
                println("Schema could not be loaded. Aborting node fetch.")
                _nodeList.value = emptyList() // Clear list if schema is unavailable
                return // Exit the function
            }
            // --- END UPDATED ---

            val dbNodes = dbService.database.appDatabaseQueries.selectAllNodes().executeAsList()
            println("fetchNodes: Found ${dbNodes.size} nodes.") // Added logging
            _nodeList.value = dbNodes.mapNotNull { dbNode ->
                val nodeSchema = schemaMap[dbNode.schema_id]
                if (nodeSchema == null) {
                    println("Warning: Found node with unknown schema ID ${dbNode.schema_id}")
                    null
                } else {
                    NodeDisplayItem(
                        id = dbNode.id,
                        label = nodeSchema.name,
                        displayProperty = dbNode.display_label
                    )
                }
            }
        } catch (e: Exception) {
            println("Error listing nodes: ${e.message}")
            _nodeList.value = emptyList()
        }
    }

    // ADDED: private suspend function for edge-fetching logic
    private suspend fun fetchEdges() {
        try {
            // UPDATED: Robust schema and node loading
            var schemaData = schemaViewModel.schema.value
            if (schemaData == null) {
                println("Edge fetch: Schema not yet loaded. Calling and awaiting showSchema() first.")
                schemaViewModel.showSchema()
                schemaData = schemaViewModel.schema.value
            }

            if (_nodeList.value.isEmpty()) {
                println("Edge fetch: Nodes not loaded. Calling and awaiting fetchNodes() first.")
                fetchNodes() // Await node fetch
            }
            // --- END UPDATED ---

            val schemaMap = schemaData?.edgeSchemas?.associateBy { it.id } ?: emptyMap()
            val nodeMap = _nodeList.value.associateBy { it.id } // Uses the now-populated node list

            if (schemaData == null) {
                println("Schema could not be loaded. Aborting edge fetch.")
                _edgeList.value = emptyList() // Clear list
                return // Exit
            }

            val dbEdges = dbService.database.appDatabaseQueries.selectAllEdges().executeAsList()
            println("fetchEdges: Found ${dbEdges.size} edges.") // Added logging
            _edgeList.value = dbEdges.mapNotNull { dbEdge ->
                val schema = schemaMap[dbEdge.schema_id]
                val srcNode = nodeMap[dbEdge.from_node_id]
                val dstNode = nodeMap[dbEdge.to_node_id]

                if (schema == null || srcNode == null || dstNode == null) {
                    println("Warning: Skipping edge ${dbEdge.id} due to missing schema or node link.")
                    null
                } else {
                    EdgeDisplayItem(
                        id = dbEdge.id,
                        label = schema.name,
                        src = srcNode,
                        dst = dstNode
                    )
                }
            }
        } catch (e: Exception) {
            println("Error listing edges: ${e.message}")
            _edgeList.value = emptyList()
        }
    }

    // UPDATED: listNodes now uses fetchNodes
    fun listNodes() {
        viewModelScope.launch {
            fetchNodes()
        }
    }

    // UPDATED: listEdges now uses fetchEdges
    fun listEdges() {
        viewModelScope.launch {
            // Edges depend on nodes, so fetch nodes first if the list is empty
            // This check is now redundant thanks to the check inside fetchEdges, but is harmless
            if (_nodeList.value.isEmpty()) {
                fetchNodes()
            }
            fetchEdges()
        }
    }


    // UPDATED: listAll now sequentially awaits fetchNodes and fetchEdges
    fun listAll() {
        viewModelScope.launch {
            try {
                println("listAll: Fetching nodes...") // Added logging
                fetchNodes()
                println("listAll: Fetching edges...") // Added logging
                fetchEdges()
                println("listAll: Finished.") // Added logging
            } catch (e: Exception) {
                println("Error in listAll: ${e.message}")
            }
        }
    }

    /**
     * Fetches the full item to edit and stores it in _itemToEdit.
     * This stored item is the "original" version for comparison.
     * Returns the fetched item so the caller can pass it to EditCreateViewModel.
     */
    // UPDATED: Fetches from SQLite and deserializes JSON
    suspend fun setItemToEdit(item: Any): Any? {
        val fetchedItem = when (item) {
            is NodeDisplayItem -> {
                val dbNode = dbService.database.appDatabaseQueries.selectNodeById(item.id).executeAsOneOrNull() ?: return null
                // UPDATED: Robust schema check
                var schemaData = schemaViewModel.schema.value
                if (schemaData == null) {
                    schemaViewModel.showSchema()
                    schemaData = schemaViewModel.schema.value
                }
                val schema = schemaData?.nodeSchemas?.firstOrNull { it.id == dbNode.schema_id } ?: return null
                val properties = try {
                    json.decodeFromString<Map<String, String>>(dbNode.properties_json)
                } catch (e: Exception) {
                    println("Error parsing node properties: ${e.message}")
                    emptyMap()
                }
                NodeEditState(id = dbNode.id, schema = schema, properties = properties)
            }
            is EdgeDisplayItem -> {
                val dbEdge = dbService.database.appDatabaseQueries.selectEdgeById(item.id).executeAsOneOrNull() ?: return null
                // UPDATED: Robust schema check
                var schemaData = schemaViewModel.schema.value
                if (schemaData == null) {
                    schemaViewModel.showSchema()
                    schemaData = schemaViewModel.schema.value
                }
                val schema = schemaData?.edgeSchemas?.firstOrNull { it.id == dbEdge.schema_id } ?: return null
                val properties = try {
                    json.decodeFromString<Map<String, String>>(dbEdge.properties_json)
                } catch (e: Exception) {
                    println("Error parsing edge properties: ${e.message}")
                    emptyMap()
                }
                EdgeEditState(id = dbEdge.id, schema = schema, src = item.src, dst = item.dst, properties = properties)
            }
            is SchemaDefinitionItem -> item // Pass schema definitions directly
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
    // UPDATED: Serializes JSON and calls new SQLDelight update queries
    fun saveEditedItem(editedState: Any?, onFinished: () -> Unit) {
        viewModelScope.launch {
            val originalState = _itemToEdit.value
            println("DEBUG: Save button clicked.")
            println("DEBUG: Original state: $originalState")
            println("DEBUG: Edited state: $editedState")
            try {
                when (editedState) {
                    is NodeEditState -> {
                        val propertiesJson = json.encodeToString(editedState.properties)
                        // Find the display property's value from the map
                        val displayKey = editedState.schema.properties.firstOrNull { it.isDisplayProperty }?.name
                        val displayLabel = editedState.properties[displayKey] ?: "Node ${editedState.id}"

                        dbService.database.appDatabaseQueries.updateNodeProperties(
                            id = editedState.id,
                            display_label = displayLabel,
                            properties_json = propertiesJson
                        )
                        listNodes() // Refresh list
                    }
                    is EdgeEditState -> {
                        val propertiesJson = json.encodeToString(editedState.properties)
                        dbService.database.appDatabaseQueries.updateEdgeProperties(
                            id = editedState.id,
                            properties_json = propertiesJson
                        )
                        listEdges() // Refresh list
                    }
                    is NodeSchemaEditState -> {
                        val originalSchema = originalState as? SchemaDefinitionItem ?: throw IllegalStateException("Original schema not found")
                        val propertiesJson = json.encodeToString(editedState.properties)
                        dbService.database.appDatabaseQueries.updateSchema(
                            id = originalSchema.id,
                            name = editedState.currentName,
                            properties_json = propertiesJson,
                            connections_json = null // Node schemas don't have connections
                        )
                        schemaViewModel.showSchema() // Refresh schema
                    }
                    is EdgeSchemaEditState -> {
                        val originalSchema = originalState as? SchemaDefinitionItem ?: throw IllegalStateException("Original schema not found")
                        val propertiesJson = json.encodeToString(editedState.properties)
                        val connectionsJson = json.encodeToString(editedState.connections)
                        dbService.database.appDatabaseQueries.updateSchema(
                            id = originalSchema.id,
                            name = editedState.currentName,
                            properties_json = propertiesJson,
                            connections_json = connectionsJson
                        )
                        schemaViewModel.showSchema() // Refresh schema
                    }
                    else -> {
                        println("DEBUG: Edited state was null or of unknown type.")
                    }
                }
            } catch (e: Exception) {
                println("Error saving item: ${e.message}")
                // TODO: Show user-facing error
            }
            clearSelectedItem() // Clear original state
            onFinished()        // Call the onFinished lambda
        }
    }

    // REMOVED: Old Kuzu-based save helpers (saveNodeChanges, saveEdgeChanges, etc.)

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

    // UPDATED: Uses new SQLDelight delete queries
    fun deleteDisplayItem(item: Any) {
        viewModelScope.launch {
            try {
                when (item) {
                    is NodeDisplayItem -> {
                        dbService.database.appDatabaseQueries.deleteNodeById(item.id)
                        listAll() // Easiest way to refresh nodes and affected edges
                    }
                    is EdgeDisplayItem -> {
                        dbService.database.appDatabaseQueries.deleteEdgeById(item.id)
                        listEdges() // Just refresh edges
                    }
                }
            } catch (e: Exception) {
                println("Error deleting item: ${e.message}")
                // TODO: Show user-facing error
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

    // REMOVED: onCleared() is no longer handled here.
}
