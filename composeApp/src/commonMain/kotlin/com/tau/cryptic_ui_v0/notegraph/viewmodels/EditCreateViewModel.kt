package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditCreateViewModel(
    private val dbService: KuzuDBService,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel,
    private val metadataViewModel: MetadataViewModel,
) {

    private val _editScreenState = MutableStateFlow<EditScreenState>(EditScreenState.None)
    val editScreenState = _editScreenState.asStateFlow()

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
        viewModelScope.launch {
            val schema = schemaViewModel.schema.value ?: return@launch
            metadataViewModel.clearSelectedItem()
            _editScreenState.value = EditScreenState.CreateNode(
                NodeCreationState(schemas = schema.nodeTables)
            )
        }
    }

    fun updateNodeCreationSchema(schemaNode: SchemaNode) {
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
                val properties = state.properties.map {
                    // Use new immutable TableProperty
                    TableProperty(it.key, it.value.toLongOrNull() ?: it.value, false, false)
                }
                val nodeTable = NodeTable(state.selectedSchema.label, properties, false, false)
                createNode(dbService, nodeTable)
                cancelAllEditing() // Exit creation mode
                metadataViewModel.listNodes() // Refresh the node list
                onFinished()
            }
        }
    }

    // --- Edge Creation ---
    fun initiateEdgeCreation() {
        viewModelScope.launch {
            val schema = schemaViewModel.schema.value ?: return@launch
            if (metadataViewModel.nodeList.value.isEmpty()) {
                metadataViewModel.listNodes()
            }
            metadataViewModel.clearSelectedItem()
            _editScreenState.value = EditScreenState.CreateEdge(
                EdgeCreationState(
                    schemas = schema.edgeTables,
                    availableNodes = metadataViewModel.nodeList.value
                )
            )
        }
    }

    fun updateEdgeCreationSchema(schemaEdge: SchemaEdge) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            current.copy(
                state = current.state.copy(
                    selectedSchema = schemaEdge,
                    src = null,
                    dst = null,
                    properties = emptyMap()
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

    fun createEdgeFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = (_editScreenState.value as? EditScreenState.CreateEdge)?.state ?: return@launch

            if (state.selectedSchema != null && state.src != null && state.dst != null) {
                val properties = state.properties.map {
                    TableProperty(it.key, it.value.toLongOrNull() ?: it.value, false, false)
                }
                val edgeTable = EdgeTable(state.selectedSchema.label, state.src, state.dst, properties, false, false, false, false)
                createEdge(dbService, edgeTable)
                cancelAllEditing() // Exit creation mode
                metadataViewModel.listEdges() // Refresh the edge list
                onFinished()
            }
        }
    }

    // --- Node Schema Creation ---
    fun initiateNodeSchemaCreation() {
        viewModelScope.launch {
            metadataViewModel.setItemToEdit("CreateNodeSchema") // Keep this for cancel logic
            _editScreenState.value = EditScreenState.CreateNodeSchema(NodeSchemaCreationState())
        }
    }

    fun onNodeSchemaTableNameChange(name: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(tableName = name))
        }
    }

    fun onNodeSchemaPropertyChange(index: Int, property: Property) {
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
            current.copy(state = current.state.copy(properties = current.state.properties + Property()))
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

    fun createNodeSchemaFromState(state: NodeSchemaCreationState, onFinished: () -> Unit) {
        viewModelScope.launch {
            createNodeSchema(dbService, state)
            onFinished()
            cancelAllEditing()
            schemaViewModel.showSchema()
        }
    }

    // --- Edge Schema Creation ---
    fun initiateEdgeSchemaCreation() {
        viewModelScope.launch {
            val nodeSchemas = schemaViewModel.schema.value?.nodeTables ?: emptyList()
            metadataViewModel.setItemToEdit("CreateEdgeSchema") // Keep this for cancel logic
            _editScreenState.value = EditScreenState.CreateEdgeSchema(
                EdgeSchemaCreationState(allNodeSchemas = nodeSchemas)
            )
        }
    }

    fun onEdgeSchemaTableNameChange(name: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            current.copy(state = current.state.copy(tableName = name))
        }
    }

    fun onEdgeSchemaSrcTableChange(table: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            current.copy(state = current.state.copy(srcTable = table))
        }
    }

    fun onEdgeSchemaDstTableChange(table: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            current.copy(state = current.state.copy(dstTable = table))
        }
    }

    fun onEdgeSchemaPropertyChange(index: Int, property: Property) {
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
            current.copy(state = current.state.copy(properties = current.state.properties + Property(isPrimaryKey = false)))
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

    fun createEdgeSchemaFromState(state: EdgeSchemaCreationState, onFinished: () -> Unit) {
        viewModelScope.launch {
            createEdgeSchema(dbService, state)
            onFinished()
            cancelAllEditing()
            schemaViewModel.showSchema()
        }
    }

    // --- Node Editing ---
    fun initiateNodeEdit(node: NodeTable) {
        _editScreenState.value = EditScreenState.EditNode(node)
        println("DEBUG: Initiated node edit for: ${node.label}")
    }

    fun updateNodeEditProperty(index: Int, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNode) return@update current

            val prop = current.state.properties[index]
            val newValue = value.toLongOrNull() ?: value

            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = prop.copy(value = newValue, valueChanged = true)
            }

            println("DEBUG: Updated property '${prop.key}' to '$newValue'. valueChanged is now true.")
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Edge Editing ---
    fun initiateEdgeEdit(edge: EdgeTable) {
        _editScreenState.value = EditScreenState.EditEdge(edge)
        println("DEBUG: Initiated edge edit for: ${edge.label}")
    }

    fun updateEdgeEditProperty(index: Int, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdge) return@update current

            val properties = current.state.properties ?: return@update current
            val prop = properties[index]
            val newValue = value.toLongOrNull() ?: value

            val newProperties = properties.toMutableList().apply {
                this[index] = prop.copy(value = newValue, valueChanged = true)
            }

            println("DEBUG: Updated edge property '${prop.key}' to '$newValue'. valueChanged is now true.")
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Node Schema Editing ---
    fun initiateNodeSchemaEdit(schema: SchemaNode) {
        val editableProperties = schema.properties.map {
            EditableSchemaProperty(
                key = it.key,
                valueDataType = it.valueDataType,
                isPrimaryKey = it.isPrimaryKey,
                originalKey = it.key
            )
        }
        _editScreenState.value = EditScreenState.EditNodeSchema(
            NodeSchemaEditState(
                originalSchema = schema,
                currentLabel = schema.label,
                properties = editableProperties
            )
        )
    }

    fun updateNodeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            current.copy(state = current.state.copy(currentLabel = label))
        }
    }

    fun updateNodeSchemaEditAddProperty() {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProperties = current.state.properties + EditableSchemaProperty(
                key = "newProperty",
                valueDataType = "STRING",
                isPrimaryKey = false,
                originalKey = "newProperty",
                isNew = true
            )
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateNodeSchemaEditRemoveProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = this[index].copy(isDeleted = true)
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateNodeSchemaEditProperty(index: Int, property: EditableSchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // --- Edge Schema Editing ---
    fun initiateEdgeSchemaEdit(schema: SchemaEdge) {
        val editableProperties = schema.properties.map {
            EditableSchemaProperty(
                key = it.key,
                valueDataType = it.valueDataType,
                isPrimaryKey = it.isPrimaryKey,
                originalKey = it.key
            )
        }
        _editScreenState.value = EditScreenState.EditEdgeSchema(
            EdgeSchemaEditState(
                originalSchema = schema,
                currentLabel = schema.label,
                properties = editableProperties
            )
        )
    }

    fun updateEdgeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            current.copy(state = current.state.copy(currentLabel = label))
        }
    }

    fun updateEdgeSchemaEditAddProperty() {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties + EditableSchemaProperty(
                key = "newProperty",
                valueDataType = "STRING",
                isPrimaryKey = false,
                originalKey = "newProperty",
                isNew = true
            )
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateEdgeSchemaEditRemoveProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = this[index].copy(isDeleted = true)
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    fun updateEdgeSchemaEditProperty(index: Int, property: EditableSchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList().apply {
                this[index] = property
            }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }
}