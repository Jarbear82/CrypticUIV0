package com.tau.nexus_note.codex.crud

import com.tau.nexus_note.SqliteDbService
import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.EditScreenState
import com.tau.nexus_note.datamodels.NodeCreationState
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.datamodels.EdgeCreationState
import com.tau.nexus_note.datamodels.EdgeEditState
import com.tau.nexus_note.datamodels.EdgeSchemaCreationState
import com.tau.nexus_note.datamodels.EdgeSchemaEditState
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.NodeEditState
import com.tau.nexus_note.datamodels.NodeSchemaCreationState
import com.tau.nexus_note.datamodels.NodeSchemaEditState
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.codex.metadata.MetadataViewModel
import com.tau.nexus_note.codex.schema.SchemaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.collections.emptyList
import kotlin.collections.get

class EditCreateViewModel(
    private val dbService: SqliteDbService,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel,
    private val metadataViewModel: MetadataViewModel,
) {

    private val _editScreenState = MutableStateFlow<EditScreenState>(EditScreenState.None)
    val editScreenState = _editScreenState.asStateFlow()

    // ADDED: JSON serializer instance
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true // Ensure all fields are present
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
        // REMOVED: Unnecessary viewModelScope.launch wrapper to fix race condition
        // UPDATED: Fetches new SchemaData and filters for node schemas
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList<SchemaDefinitionItem>()
        metadataViewModel.clearSelectedItem()
        _editScreenState.value = EditScreenState.CreateNode(
            NodeCreationState(schemas = nodeSchemas)
        )
    }

    // UPDATED: Signature uses new SchemaDefinitionItem
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

    // UPDATED: Rewritten to serialize properties and call SQLDelight insert query
    fun createNodeFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = (_editScreenState.value as? EditScreenState.CreateNode)?.state ?: return@launch

            if (state.selectedSchema != null) {
                try {
                    // Serialize properties to JSON
                    val propertiesJson = json.encodeToString(state.properties)

                    // Find the display property's value
                    val displayKey = state.selectedSchema.properties.firstOrNull { it.isDisplayProperty }?.name
                    val displayLabel = state.properties[displayKey] ?: "Node" // Fallback

                    // Call SQLDelight insert query
                    dbService.database.appDatabaseQueries.insertNode(
                        schema_id = state.selectedSchema.id,
                        display_label = displayLabel,
                        properties_json = propertiesJson
                    )
                    cancelAllEditing()
                    metadataViewModel.listNodes()
                    onFinished()
                } catch (e: Exception) {
                    println("Error creating node: ${e.message}")
                    // TODO: Show user-facing error
                }
            }
        }
    }

    // --- Edge Creation ---
    fun initiateEdgeCreation() {
        // REMOVED: Unnecessary viewModelScope.launch wrapper to fix race condition
        // UPDATED: Fetches new SchemaData and filters for edge schemas
        val edgeSchemas = schemaViewModel.schema.value?.edgeSchemas ?: emptyList<SchemaDefinitionItem>()
        if (metadataViewModel.nodeList.value.isEmpty()) {
            metadataViewModel.listNodes()
        }
        metadataViewModel.clearSelectedItem()
        _editScreenState.value = EditScreenState.CreateEdge(
            EdgeCreationState(
                schemas = edgeSchemas,
                availableNodes = metadataViewModel.nodeList.value
            )
        )
    }

    // UPDATED: Signature uses new SchemaDefinitionItem
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
                    src = null, // Reset src/dst when connection type changes
                    dst = null
                )
            )
        }
    }

    fun updateEdgeCreationSrc(node: NodeDisplayItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            current.copy(state = current.state.copy(src = node))
        }
    }

    fun updateEdgeCreationDst(node: NodeDisplayItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            current.copy(state = current.state.copy(dst = node))
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

    // UPDATED: Rewritten to serialize properties and call SQLDelight insert query
    fun createEdgeFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = (_editScreenState.value as? EditScreenState.CreateEdge)?.state ?: return@launch

            if (state.selectedSchema != null && state.src != null && state.dst != null) {
                try {
                    // Serialize properties to JSON
                    val propertiesJson = json.encodeToString(state.properties)

                    // Call SQLDelight insert query
                    dbService.database.appDatabaseQueries.insertEdge(
                        schema_id = state.selectedSchema.id,
                        from_node_id = state.src.id,
                        to_node_id = state.dst.id,
                        properties_json = propertiesJson
                    )
                    cancelAllEditing() // Exit creation mode
                    metadataViewModel.listEdges() // Refresh the edge list
                    onFinished()
                } catch (e: Exception) {
                    println("Error creating edge: ${e.message}")
                    // TODO: Show user-facing error
                }
            }
        }
    }

    // --- Node Schema Creation ---
    fun initiateNodeSchemaCreation() {
        // FIX: Launch a coroutine *only* for the suspend call
        viewModelScope.launch {
            metadataViewModel.setItemToEdit("CreateNodeSchema") // Keep this for cancel logic
        }
        // UPDATED: Uses new NodeSchemaCreationState with default SchemaProperty
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

    // UPDATED: Signature uses new SchemaProperty
    fun onNodeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // UPDATED: Adds new SchemaProperty
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

    // UPDATED: Rewritten to serialize properties and call SQLDelight insert query
    fun createNodeSchemaFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = (_editScreenState.value as? EditScreenState.CreateNodeSchema)?.state ?: return@launch
            try {
                // Serialize properties
                val propertiesJson = json.encodeToString(state.properties)

                // Call SQLDelight insert query
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "NODE",
                    name = state.tableName,
                    properties_json = propertiesJson,
                    connections_json = null // Nodes don't have connections
                )
                onFinished()
                cancelAllEditing()
                schemaViewModel.showSchema()
            } catch (e: Exception) {
                println("Error creating node schema: ${e.message}")
                // TODO: Show user-facing error
            }
        }
    }

    // --- Edge Schema Creation ---
    fun initiateEdgeSchemaCreation() {
        // UPDATED: Fetches new SchemaData and filters for node schemas
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList<SchemaDefinitionItem>()
        // FIX: Launch a coroutine *only* for the suspend call
        viewModelScope.launch {
            metadataViewModel.setItemToEdit("CreateEdgeSchema") // Keep this for cancel logic
        }
        _editScreenState.value = EditScreenState.CreateEdgeSchema(
            EdgeSchemaCreationState(allNodeSchemas = nodeSchemas)
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

    // UPDATED: Signature uses new SchemaProperty
    fun onEdgeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // UPDATED: Adds new SchemaProperty
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

    // UPDATED: Rewritten to serialize properties AND connections and call SQLDelight insert query
    fun createEdgeSchemaFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = (_editScreenState.value as? EditScreenState.CreateEdgeSchema)?.state ?: return@launch
            try {
                // Serialize both properties and connections
                val propertiesJson = json.encodeToString(state.properties)
                val connectionsJson = json.encodeToString(state.connections)

                // Call SQLDelight insert query
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
                // TODO: Show user-facing error
            }
        }
    }

    // --- Node Editing ---
    // UPDATED: Signature uses new NodeEditState
    fun initiateNodeEdit(node: NodeEditState) {
        _editScreenState.value = EditScreenState.EditNode(node)
        println("DEBUG: Initiated node edit for: ${node.schema.name}")
    }

    // UPDATED: Logic remains the same, but operates on NodeEditState
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

    // --- Edge Editing ---
    // UPDATED: Signature uses new EdgeEditState
    fun initiateEdgeEdit(edge: EdgeEditState) {
        _editScreenState.value = EditScreenState.EditEdge(edge)
        println("DEBUG: Initiated edge edit for: ${edge.schema.name}")
    }

    // UPDATED: Logic remains the same, but operates on EdgeEditState
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
    // UPDATED: Signature uses new SchemaDefinitionItem
    fun initiateNodeSchemaEdit(schema: SchemaDefinitionItem) {
        _editScreenState.value = EditScreenState.EditNodeSchema(
            NodeSchemaEditState(
                originalSchema = schema,
                currentName = schema.name,
                properties = schema.properties // Start with the original properties
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

    // UPDATED: Signature uses new SchemaProperty
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
    // UPDATED: Signature uses new SchemaDefinitionItem
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

    // UPDATED: Signature uses new SchemaProperty
    fun updateEdgeSchemaEditProperty(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // UPDATED: Add/Remove Connections for Edge Schema Editing
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

