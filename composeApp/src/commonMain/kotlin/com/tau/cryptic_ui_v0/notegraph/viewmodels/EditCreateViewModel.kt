package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.tau.cryptic_ui_v0.db.AppDatabase

class EditCreateViewModel(
    private val dbService: SqliteDbService,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel,
    private val metadataViewModel: MetadataViewModel,
) {

    private val _editScreenState = MutableStateFlow<EditScreenState>(EditScreenState.None)
    val editScreenState = _editScreenState.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * Returns the currently active *edited* item state (not creation state).
     * Used by MetadataViewModel to get the edited data for comparison.
     */
    fun getCurrentEditState(): Any? {
        return when (val s = _editScreenState.value) {
            is EditScreenState.EditNode -> s.state
            is EditScreenState.EditEdge -> s.state
            is EditScreenState.EditNodeSchema -> s.state
            is EditScreenState.EditEdgeSchema -> s.state
            else -> null // It's a creation state or None
        }
    }

    /**
     * Clears all creation and editing states.
     * Called on "Cancel" or when switching tabs.
     */
    fun cancelAllEditing() {
        _editScreenState.value = EditScreenState.None
    }

    // --- Node Creation ---
    fun initiateNodeCreation() {
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        metadataViewModel.clearSelectedItem()
        _editScreenState.value = EditScreenState.CreateNode(
            NodeCreationState(schemas = nodeSchemas)
        )
    }

    fun updateNodeCreationSchema(schemaNode: SchemaDefinitionItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            current.copy(
                state = current.state.copy(
                    selectedSchema = schemaNode,
                    properties = emptyMap() // Reset properties when schema changes
                )
            )
        }
    }

    fun updateNodeCreationProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            val newProperties = current.state.properties.toMutableMap().apply {
                this[key] = value
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun createNodeFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = (_editScreenState.value as? EditScreenState.CreateNode)?.state ?: return@launch

            if (state.selectedSchema != null) {
                try {
                    val propertiesJson = json.encodeToString(state.properties)
                    val displayKey = state.selectedSchema.properties.firstOrNull { it.isDisplayProperty }?.name
                    val displayLabel = state.properties[displayKey] ?: "Node"

                    dbService.database.appDatabaseQueries.insertNode(
                        schema_id = state.selectedSchema.id,
                        display_label = displayLabel,
                        properties_json = propertiesJson,
                        cluster_id = null // New nodes are unclustered by default
                    )
                    cancelAllEditing()
                    metadataViewModel.listAll() // Use listAll to refresh edges too
                    onFinished()
                } catch (e: Exception) {
                    println("Error creating node: ${e.message}")
                }
            }
        }
    }

    // --- Edge Creation ---
    fun initiateEdgeCreation() {
        val edgeSchemas = schemaViewModel.schema.value?.edgeSchemas ?: emptyList()
        if (metadataViewModel.nodeList.value.isEmpty()) {
            metadataViewModel.listNodes()
        }
        if (metadataViewModel.clusterList.value.isEmpty()) { // ADDED
            metadataViewModel.listClusters()
        }
        metadataViewModel.clearSelectedItem()
        _editScreenState.value = EditScreenState.CreateEdge(
            EdgeCreationState(
                schemas = edgeSchemas,
                // --- MODIFIED ---
                availableEntities = metadataViewModel.nodeList.value + metadataViewModel.clusterList.value
                // --- END MODIFICATION ---
            )
        )
    }

    fun updateEdgeCreationSchema(schemaEdge: SchemaDefinitionItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            current.copy(
                state = current.state.copy(
                    selectedSchema = schemaEdge,
                    selectedConnection = null,
                    src = null,
                    dst = null,
                    properties = emptyMap()
                )
            )
        }
    }

    fun updateEdgeCreationConnection(connection: ConnectionPair) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            current.copy(
                state = current.state.copy(
                    selectedConnection = connection,
                    src = null,
                    dst = null
                )
            )
        }
    }

    fun updateEdgeCreationSrc(entity: GraphEntityDisplayItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            current.copy(state = current.state.copy(src = entity))
        }
    }

    fun updateEdgeCreationDst(entity: GraphEntityDisplayItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            current.copy(state = current.state.copy(dst = entity))
        }
    }

    fun updateEdgeCreationProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newProperties = current.state.properties.toMutableMap().apply {
                this[key] = value
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun createEdgeFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = (_editScreenState.value as? EditScreenState.CreateEdge)?.state ?: return@launch

            if (state.selectedSchema != null && state.src != null && state.dst != null) {
                try {
                    val propertiesJson = json.encodeToString(state.properties)

                    // Determine if src/dst are Node or Cluster
                    val fromNodeId = (state.src as? NodeDisplayItem)?.id
                    val fromClusterId = (state.src as? ClusterDisplayItem)?.id
                    val toNodeId = (state.dst as? NodeDisplayItem)?.id
                    val toClusterId = (state.dst as? ClusterDisplayItem)?.id

                    dbService.database.appDatabaseQueries.insertEdge(
                        schema_id = state.selectedSchema.id,
                        from_node_id = fromNodeId,
                        from_cluster_id = fromClusterId,
                        to_node_id = toNodeId,
                        to_cluster_id = toClusterId,
                        properties_json = propertiesJson
                    )
                    cancelAllEditing()
                    metadataViewModel.listAll() // Use listAll
                    onFinished()
                } catch (e: Exception) {
                    println("Error creating edge: ${e.message}")
                }
            }
        }
    }

    // --- Node Schema Creation ---
    fun initiateNodeSchemaCreation() {
        viewModelScope.launch {
            metadataViewModel.setItemToEdit("CreateNodeSchema")
        }
        _editScreenState.value = EditScreenState.CreateNodeSchema(
            NodeSchemaCreationState(
                properties = listOf(SchemaProperty("name", "Text", isDisplayProperty = true))
            )
        )
    }

    fun onNodeSchemaTableNameChange(name: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(tableName = name))
        }
    }

    fun onNodeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun onAddNodeSchemaProperty() {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(properties = current.state.properties + SchemaProperty("newProperty", "Text")))
        }
    }

    fun onRemoveNodeSchemaProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                removeAt(index)
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun createNodeSchemaFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = (_editScreenState.value as? EditScreenState.CreateNodeSchema)?.state ?: return@launch
            try {
                val propertiesJson = json.encodeToString(state.properties)

                dbService.database.appDatabaseQueries.insertSchema(
                    type = "NODE",
                    name = state.tableName,
                    properties_json = propertiesJson,
                    connections_json = null
                )
                onFinished()
                cancelAllEditing()
                schemaViewModel.showSchema()
            } catch (e: Exception) {
                println("Error creating node schema: ${e.message}")
            }
        }
    }

    // --- Edge Schema Creation ---
    fun initiateEdgeSchemaCreation() {
        // UPDATED: Now also fetches cluster schemas
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        val allConnectableNames = nodeSchemas.map { it.name } + "Cluster"

        viewModelScope.launch {
            metadataViewModel.setItemToEdit("CreateEdgeSchema")
        }
        _editScreenState.value = EditScreenState.CreateEdgeSchema(
            EdgeSchemaCreationState(
                allNodeSchemas = nodeSchemas, // Pass node schemas
                allConnectableNames = allConnectableNames // Pass all names
            )
        )
    }

    fun onEdgeSchemaTableNameChange(name: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            current.copy(state = current.state.copy(tableName = name))
        }
    }

    fun onAddEdgeSchemaConnection(src: String, dst: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newConnection = ConnectionPair(src, dst)
            if (!current.state.connections.contains(newConnection)) {
                current.copy(state = current.state.copy(connections = current.state.connections + newConnection))
            } else {
                current
            }
        }
    }

    fun onRemoveEdgeSchemaConnection(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newConnections = current.state.connections.toMutableList().apply {
                removeAt(index)
            }
            current.copy(state = current.state.copy(connections = newConnections))
        }
    }

    fun onEdgeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun onAddEdgeSchemaProperty() {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            current.copy(state = current.state.copy(properties = current.state.properties + SchemaProperty("newProperty", "Text")))
        }
    }

    fun onRemoveEdgeSchemaProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                removeAt(index)
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun createEdgeSchemaFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = (_editScreenState.value as? EditScreenState.CreateEdgeSchema)?.state ?: return@launch
            try {
                val propertiesJson = json.encodeToString(state.properties)
                val connectionsJson = json.encodeToString(state.connections)

                dbService.database.appDatabaseQueries.insertSchema(
                    type = "EDGE",
                    name = state.tableName,
                    properties_json = propertiesJson,
                    connections_json = connectionsJson
                )
                onFinished()
                cancelAllEditing()
                schemaViewModel.showSchema()
            } catch (e: Exception) {
                println("Error creating edge schema: ${e.message}")
            }
        }
    }

    // --- Node Editing ---
    fun initiateNodeEdit(node: NodeEditState) {
        _editScreenState.value = EditScreenState.EditNode(node)
        println("DEBUG: Initiated node edit for: ${node.schema.name}")
    }

    fun updateNodeEditProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNode) return@update current

            val newProperties = current.state.properties.toMutableMap().apply {
                this[key] = value
            }

            println("DEBUG: Updated property '$key' to '$value'.")
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateNodeEditCluster(cluster: ClusterDisplayItem?) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNode) return@update current
            current.copy(state = current.state.copy(clusterId = cluster?.id))
        }
    }

    // --- Edge Editing ---
    fun initiateEdgeEdit(edge: EdgeEditState) {
        _editScreenState.value = EditScreenState.EditEdge(edge)
        println("DEBUG: Initiated edge edit for: ${edge.schema.name}")
    }

    fun updateEdgeEditProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdge) return@update current

            val newProperties = current.state.properties.toMutableMap().apply {
                this[key] = value
            }

            println("DEBUG: Updated edge property '$key' to '$value'.")
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Node Schema Editing ---
    fun initiateNodeSchemaEdit(schema: SchemaDefinitionItem) {
        _editScreenState.value = EditScreenState.EditNodeSchema(
            NodeSchemaEditState(
                originalSchema = schema,
                currentName = schema.name,
                properties = schema.properties
            )
        )
    }

    fun updateNodeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            current.copy(state = current.state.copy(currentName = label))
        }
    }

    fun updateNodeSchemaEditAddProperty() {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProperties = current.state.properties + SchemaProperty(
                name = "newProperty",
                type = "Text",
                isDisplayProperty = false
            )
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateNodeSchemaEditRemoveProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                removeAt(index)
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateNodeSchemaEditProperty(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Edge Schema Editing ---
    fun initiateEdgeSchemaEdit(schema: SchemaDefinitionItem) {
        _editScreenState.value = EditScreenState.EditEdgeSchema(
            EdgeSchemaEditState(
                originalSchema = schema,
                currentName = schema.name,
                connections = schema.connections ?: emptyList(),
                properties = schema.properties
            )
        )
    }

    fun updateEdgeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            current.copy(state = current.state.copy(currentName = label))
        }
    }

    fun updateEdgeSchemaEditAddProperty() {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties + SchemaProperty(
                name = "newProperty",
                type = "Text",
                isDisplayProperty = false
            )
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateEdgeSchemaEditRemoveProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                removeAt(index)
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateEdgeSchemaEditProperty(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateEdgeSchemaEditAddConnection(src: String, dst: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newConnection = ConnectionPair(src, dst)
            if (!current.state.connections.contains(newConnection)) {
                current.copy(state = current.state.copy(connections = current.state.connections + newConnection))
            } else {
                current
            }
        }
    }

    fun updateEdgeSchemaEditRemoveConnection(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newConnections = current.state.connections.toMutableList().apply {
                removeAt(index)
            }
            current.copy(state = current.state.copy(connections = newConnections))
        }
    }
}

