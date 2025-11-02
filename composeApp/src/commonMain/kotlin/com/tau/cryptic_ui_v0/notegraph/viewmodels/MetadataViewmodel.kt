package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.* // Imports new data classes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.tau.cryptic_ui_v0.db.AppDatabase

class MetadataViewModel(
    private val dbService: SqliteDbService,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel // Still needed to get schema names
) {
    private val _nodeList = MutableStateFlow<List<NodeDisplayItem>>(emptyList())
    val nodeList = _nodeList.asStateFlow()

    // ADDED
    private val _clusterList = MutableStateFlow<List<ClusterDisplayItem>>(emptyList())
    val clusterList = _clusterList.asStateFlow()

    private val _edgeList = MutableStateFlow<List<EdgeDisplayItem>>(emptyList())
    val edgeList = _edgeList.asStateFlow()

    // This stores the ORIGINAL item being edited
    private val _itemToEdit = MutableStateFlow<Any?>(null)
    val itemToEdit = _itemToEdit.asStateFlow()

    private val _primarySelectedItem = MutableStateFlow<Any?>(null)
    val primarySelectedItem = _primarySelectedItem.asStateFlow()

    private val _secondarySelectedItem = MutableStateFlow<Any?>(null)
    val secondarySelectedItem = _secondarySelectedItem.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private suspend fun fetchNodes() {
        try {
            var schemaData = schemaViewModel.schema.value
            if (schemaData == null) {
                println("Schema not yet loaded. Calling and awaiting showSchema() first.")
                schemaViewModel.showSchema()
                schemaData = schemaViewModel.schema.value
            }

            val schemaMap = schemaData?.nodeSchemas?.associateBy { it.id } ?: emptyMap()

            if (schemaData == null) {
                println("Schema could not be loaded. Aborting node fetch.")
                _nodeList.value = emptyList()
                return
            }

            val dbNodes = dbService.database.appDatabaseQueries.selectAllNodes().executeAsList()
            println("fetchNodes: Found ${dbNodes.size} nodes.")
            _nodeList.value = dbNodes.mapNotNull { dbNode ->
                val nodeSchema = schemaMap[dbNode.schema_id]
                if (nodeSchema == null) {
                    println("Warning: Found node with unknown schema ID ${dbNode.schema_id}")
                    null
                } else {
                    NodeDisplayItem(
                        id = dbNode.id,
                        label = nodeSchema.name,
                        displayProperty = dbNode.display_label,
                        // --- MODIFIED: Read clusterId from db ---
                        clusterId = dbNode.cluster_id
                        // --- END MODIFICATION ---
                    )
                }
            }
        } catch (e: Exception) {
            println("Error listing nodes: ${e.message}")
            _nodeList.value = emptyList()
        }
    }

    // ADDED
    private suspend fun fetchClusters() {
        try {

            val dbClusters = dbService.database.appDatabaseQueries.selectAllClusters().executeAsList()
            println("fetchClusters: Found ${dbClusters.size} clusters.")
            _clusterList.value = dbClusters.map { dbCluster ->
                ClusterDisplayItem(
                    id = dbCluster.id,
                    // 'label' is a constant "Cluster" defined in the data class
                    displayProperty = dbCluster.display_label
                )
            }
        } catch (e: Exception) {
            println("Error listing clusters: ${e.message}")
            _clusterList.value = emptyList()
        }
    }

    private suspend fun fetchEdges() {
        try {
            var schemaData = schemaViewModel.schema.value
            if (schemaData == null) {
                println("Edge fetch: Schema not loaded. Calling and awaiting showSchema() first.")
                schemaViewModel.showSchema()
                schemaData = schemaViewModel.schema.value
            }

            if (_nodeList.value.isEmpty()) {
                println("Edge fetch: Nodes not loaded. Calling and awaiting fetchNodes() first.")
                fetchNodes()
            }
            // ADDED: Fetch clusters if list is empty
            if (_clusterList.value.isEmpty()) {
                println("Edge fetch: Clusters not loaded. Calling and awaiting fetchClusters() first.")
                fetchClusters()
            }

            val schemaMap = schemaData?.edgeSchemas?.associateBy { it.id } ?: emptyMap()
            // Create a combined map of all graph entities
            val entityMap: Map<String, GraphEntityDisplayItem> =
                _nodeList.value.associateBy { "node_${it.id}" } +
                        _clusterList.value.associateBy { "cluster_${it.id}" }

            if (schemaData == null) {
                println("Schema could not be loaded. Aborting edge fetch.")
                _edgeList.value = emptyList()
                return
            }

            val dbEdges = dbService.database.appDatabaseQueries.selectAllEdges().executeAsList()
            println("fetchEdges: Found ${dbEdges.size} edges.")
            _edgeList.value = dbEdges.mapNotNull { dbEdge ->
                val schema = schemaMap[dbEdge.schema_id]

                // Determine src and dst entities
                val srcEntity = when {
                    dbEdge.from_node_id != null -> entityMap["node_${dbEdge.from_node_id}"]
                    dbEdge.from_cluster_id != null -> entityMap["cluster_${dbEdge.from_cluster_id}"]
                    else -> null
                }
                val dstEntity = when {
                    dbEdge.to_node_id != null -> entityMap["node_${dbEdge.to_node_id}"]
                    dbEdge.to_cluster_id != null -> entityMap["cluster_${dbEdge.to_cluster_id}"]
                    else -> null
                }

                if (schema == null || srcEntity == null || dstEntity == null) {
                    println("Warning: Skipping edge ${dbEdge.id} due to missing schema or entity link.")
                    null
                } else {
                    EdgeDisplayItem(
                        id = dbEdge.id,
                        label = schema.name,
                        src = srcEntity,
                        dst = dstEntity
                    )
                }
            }
        } catch (e: Exception) {
            println("Error listing edges: ${e.message}")
            _edgeList.value = emptyList()
        }
    }

    fun listNodes() {
        viewModelScope.launch {
            fetchNodes()
        }
    }

    // ADDED
    fun listClusters() {
        viewModelScope.launch {
            fetchClusters()
        }
    }

    fun listEdges() {
        viewModelScope.launch {
            if (_nodeList.value.isEmpty()) {
                fetchNodes()
            }
            if (_clusterList.value.isEmpty()) { // ADDED
                fetchClusters()
            }
            fetchEdges()
        }
    }


    // UPDATED: listAll now calls fetchClusters
    fun listAll() {
        viewModelScope.launch {
            try {
                println("listAll: Fetching nodes...")
                fetchNodes()
                println("listAll: Fetching clusters...") // ADDED
                fetchClusters() // ADDED
                println("listAll: Fetching edges...")
                fetchEdges()
                println("listAll: Finished.")
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
    // UPDATED: Handles ClusterDisplayItem and ClusterEditState
    suspend fun setItemToEdit(item: Any): Any? {
        val fetchedItem = when (item) {
            is NodeDisplayItem -> {
                val dbNode = dbService.database.appDatabaseQueries.selectNodeById(item.id).executeAsOneOrNull() ?: return null
                var schemaData = schemaViewModel.schema.value
                if (schemaData == null) {
                    schemaViewModel.showSchema()
                    schemaData = schemaViewModel.schema.value
                }
                // --- MODIFICATION: Fetch clusters for dropdown ---
                if (_clusterList.value.isEmpty()) {
                    fetchClusters()
                }
                val allClusters = _clusterList.value
                // --- END MODIFICATION ---

                val schema = schemaData?.nodeSchemas?.firstOrNull { it.id == dbNode.schema_id } ?: return null
                val properties = try {
                    json.decodeFromString<Map<String, String>>(dbNode.properties_json)
                } catch (e: Exception) {
                    println("Error parsing node properties: ${e.message}")
                    emptyMap()
                }
                NodeEditState(
                    id = dbNode.id,
                    schema = schema,
                    properties = properties,
                    clusterId = dbNode.cluster_id, // Pass clusterId
                    availableClusters = allClusters // Pass all clusters
                )
            }
            is EdgeDisplayItem -> {
                val dbEdge = dbService.database.appDatabaseQueries.selectEdgeById(item.id).executeAsOneOrNull() ?: return null
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
                EdgeEditState(
                    id = dbEdge.id,
                    schema = schema,
                    src = item.src,
                    dst = item.dst,
                    properties = properties
                )
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
    // UPDATED: Handles ClusterEditState
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
                        val displayKey = editedState.schema.properties.firstOrNull { it.isDisplayProperty }?.name
                        val displayLabel = editedState.properties[displayKey] ?: "Node ${editedState.id}"

                        dbService.database.appDatabaseQueries.updateNodeProperties(
                            id = editedState.id,
                            display_label = displayLabel,
                            properties_json = propertiesJson,
                            cluster_id = editedState.clusterId // Pass the new cluster ID
                        )
                        // listAll() is needed to refresh node.clusterId in all viewmodels
                        listAll()
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
                            connections_json = null
                        )
                        schemaViewModel.showSchema()
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
                        schemaViewModel.showSchema()
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

    fun selectItem(item: Any) {
        val currentPrimary = _primarySelectedItem.value
        val currentSecondary = _secondarySelectedItem.value

        when (item) {
            // UPDATED: Merged NodeDisplayItem and ClusterDisplayItem logic
            is GraphEntityDisplayItem -> {
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

    // UPDATED: Handles ClusterDisplayItem
    fun deleteDisplayItem(item: Any) {
        viewModelScope.launch {
            try {
                when (item) {
                    is NodeDisplayItem -> {
                        dbService.database.appDatabaseQueries.deleteNodeById(item.id)
                        listAll() // Easiest way to refresh nodes and affected edges
                    }
                    is ClusterDisplayItem -> { // ADDED
                        dbService.database.appDatabaseQueries.deleteClusterById(item.id)
                        listAll() // Easiest way to refresh clusters and affected edges
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

    // ADDED
    fun clearClusterList() {
        _clusterList.value = emptyList()
    }

    fun clearEdgeList() {
        _edgeList.value = emptyList()
    }

    fun clearSelectedItem() {
        _itemToEdit.value = null
        _primarySelectedItem.value = null
        _secondarySelectedItem.value = null
    }
}
