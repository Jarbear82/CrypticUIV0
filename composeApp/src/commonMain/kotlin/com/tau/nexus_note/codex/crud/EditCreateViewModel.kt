package com.tau.nexus_note.codex.crud

import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.EditScreenState
import com.tau.nexus_note.datamodels.NodeCreationState
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.datamodels.EdgeCreationState
import com.tau.nexus_note.datamodels.EdgeEditState
import com.tau.nexus_note.datamodels.EdgeSchemaCreationState
import com.tau.nexus_note.datamodels.EdgeSchemaEditState
import com.tau.nexus_note.datamodels.NodeEditState
import com.tau.nexus_note.datamodels.NodeSchemaCreationState
import com.tau.nexus_note.datamodels.NodeSchemaEditState
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.codex.metadata.MetadataViewModel
import com.tau.nexus_note.codex.schema.SchemaViewModel
import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.codex.schema.SchemaData
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.NodeDisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.collections.emptyList
import kotlin.collections.get

class EditCreateViewModel(
    private val repository: CodexRepository,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel,
    private val metadataViewModel: MetadataViewModel,
) {

    private val _editScreenState = MutableStateFlow<EditScreenState>(EditScreenState.None)
    val editScreenState = _editScreenState.asStateFlow()

    // This will be used to tell the CodexView to navigate *after* a save is complete.
    private val _navigationEvent = MutableSharedFlow<Unit>(replay = 0)
    val navigationEventFlow = _navigationEvent.asSharedFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true // Ensure all fields are present
    }

    // --- Validation Helper ---

    /**
     * Checks if a schema name is unique.
     * @param name The name to check.
     * @param editingId The ID of the schema being edited, or null if creating a new one.
     * @return An error message string if not unique, or null if unique.
     */
    private fun isSchemaNameUnique(name: String, editingId: Long? = null): String? {
        if (name.isBlank()) return "Name cannot be blank."

        val allSchemas = (schemaViewModel.schema.value?.nodeSchemas ?: emptyList()) +
                (schemaViewModel.schema.value?.edgeSchemas ?: emptyList())

        val conflictingSchema = allSchemas.find { it.name.equals(name, ignoreCase = false) }

        return when {
            conflictingSchema == null -> null // No conflict
            editingId != null && conflictingSchema.id == editingId -> null // Conflict is with itself
            else -> "Name is already used by another schema."
        }
    }

    /**
     * Validates a single property within a list.
     * @param index The index of the property being validated.
     * @param property The property itself.
     * @param allProperties The complete list of properties for this schema.
     * @return An error message string if invalid, or null if valid.
     */
    private fun validateProperty(
        index: Int,
        property: SchemaProperty,
        allProperties: List<SchemaProperty>
    ): String? {
        if (property.name.isBlank()) return "Name cannot be blank."

        val conflict = allProperties.withIndex().find { (i, p) ->
            i != index && p.name.equals(property.name, ignoreCase = false)
        }
        return if (conflict != null) "Name is already used in this schema." else null
    }


    /**
     * Saves the currently active state (create or edit) to the repository.
     * After saving, it emits a navigation event and clears the edit state.
     */
    fun saveCurrentState() {
        val stateToSave = _editScreenState.value
        if (stateToSave is EditScreenState.None) return

        // --- PRE-SAVE VALIDATION ---
        // Check for any existing errors in the state before attempting to save
        val hasError = when (stateToSave) {
            is EditScreenState.CreateNodeSchema -> stateToSave.state.tableNameError != null || stateToSave.state.propertyErrors.any { it.value != null }
            is EditScreenState.EditNodeSchema -> stateToSave.state.currentNameError != null || stateToSave.state.propertyErrors.any { it.value != null }
            is EditScreenState.CreateEdgeSchema -> stateToSave.state.tableNameError != null || stateToSave.state.propertyErrors.any { it.value != null }
            is EditScreenState.EditEdgeSchema -> stateToSave.state.currentNameError != null || stateToSave.state.propertyErrors.any { it.value != null }
            else -> false // No validation for instance creation/editing (yet)
        }

        if (hasError) {
            println("Save aborted due to validation errors.")
            return // Don't save
        }
        // --- END VALIDATION ---

        viewModelScope.launch {
            // Perform the database operation based on the current state
            when (stateToSave) {
                is EditScreenState.CreateNode -> repository.createNode(stateToSave.state)
                is EditScreenState.CreateEdge -> repository.createEdge(stateToSave.state)
                is EditScreenState.CreateNodeSchema -> repository.createNodeSchema(stateToSave.state)
                is EditScreenState.CreateEdgeSchema -> repository.createEdgeSchema(stateToSave.state)
                is EditScreenState.EditNode -> repository.updateNode(stateToSave.state)
                is EditScreenState.EditEdge -> repository.updateEdge(stateToSave.state)
                is EditScreenState.EditNodeSchema -> repository.updateNodeSchema(stateToSave.state)
                is EditScreenState.EditEdgeSchema -> repository.updateEdgeSchema(stateToSave.state)
                is EditScreenState.None -> {} // Should not happen due to check above
            }

            // After the suspend function completes, clear the state and signal navigation
            cancelAllEditing() // Resets editScreenState to None
            metadataViewModel.clearSelectedItem()
            _navigationEvent.emit(Unit) // Tell CodexView to handle navigation
        }
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
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList<SchemaDefinitionItem>()
        metadataViewModel.clearSelectedItem()
        _editScreenState.value = EditScreenState.CreateNode(
            NodeCreationState(schemas = nodeSchemas)
        )
    }

    /**
     * Initiates node creation with a pre-selected schema.
     */
    fun initiateNodeCreation(schema: SchemaDefinitionItem) {
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        metadataViewModel.clearSelectedItem()
        _editScreenState.value = EditScreenState.CreateNode(
            NodeCreationState(
                schemas = nodeSchemas,
                selectedSchema = schema
            )
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

    // --- Edge Creation ---
    fun initiateEdgeCreation() {
        // Fetches new SchemaData and filters for edge schemas
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

    /**
     * Initiates edge creation with a pre-selected schema and connection pair.
     */
    fun initiateEdgeCreation(schema: SchemaDefinitionItem, connection: ConnectionPair) {
        val edgeSchemas = schemaViewModel.schema.value?.edgeSchemas ?: emptyList()
        if (metadataViewModel.nodeList.value.isEmpty()) {
            metadataViewModel.listNodes()
        }
        metadataViewModel.clearSelectedItem()
        _editScreenState.value = EditScreenState.CreateEdge(
            EdgeCreationState(
                schemas = edgeSchemas,
                availableNodes = metadataViewModel.nodeList.value,
                selectedSchema = schema,
                selectedConnection = connection,
                src = null,
                dst = null,
                properties = emptyMap()
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
                    // Reset src/dst when connection type changes
                    src = null,
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

    // --- Node Schema Creation ---
    fun initiateNodeSchemaCreation() {
        viewModelScope.launch {
            metadataViewModel.setItemToEdit("CreateNodeSchema") // Keep this for cancel logic
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
            val error = isSchemaNameUnique(name)
            current.copy(state = current.state.copy(tableName = name, tableNameError = error))
        }
    }

    fun onNodeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)

            current.copy(state = current.state.copy(
                properties = newProperties,
                propertyErrors = newErrors
            ))
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

    // --- Edge Schema Creation ---
    fun initiateEdgeSchemaCreation() {
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList<SchemaDefinitionItem>()
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
            val error = isSchemaNameUnique(name)
            current.copy(state = current.state.copy(tableName = name, tableNameError = error))
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
            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)

            current.copy(state = current.state.copy(
                properties = newProperties,
                propertyErrors = newErrors
            ))
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

    // --- Node Editing ---
    fun initiateNodeEdit(item: NodeDisplayItem) {
        viewModelScope.launch {
            val editState = repository.getNodeEditState(item.id)
            if (editState != null) {
                _editScreenState.value = EditScreenState.EditNode(editState)
                metadataViewModel.setItemToEdit(item) // For navigation
            } else {
                println("Error: Could not fetch node details for ${item.id}")
            }
        }
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

    // --- Edge Editing ---
    fun initiateEdgeEdit(item: EdgeDisplayItem) {
        viewModelScope.launch {
            val editState = repository.getEdgeEditState(item)
            if (editState != null) {
                _editScreenState.value = EditScreenState.EditEdge(editState)
                metadataViewModel.setItemToEdit(item) // For navigation
            } else {
                println("Error: Could not fetch edge details for ${item.id}")
            }
        }
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
                properties = schema.properties // Start with the original properties
            )
        )
    }

    fun updateNodeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val error = isSchemaNameUnique(label, current.state.originalSchema.id)
            current.copy(state = current.state.copy(currentName = label, currentNameError = error))
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
            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)

            current.copy(state = current.state.copy(
                properties = newProperties,
                propertyErrors = newErrors
            ))
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
            val error = isSchemaNameUnique(label, current.state.originalSchema.id)
            current.copy(state = current.state.copy(currentName = label, currentNameError = error))
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
            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)

            current.copy(state = current.state.copy(
                properties = newProperties,
                propertyErrors = newErrors
            ))
        }
    }

    // Add/Remove Connections for Edge Schema Editing
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