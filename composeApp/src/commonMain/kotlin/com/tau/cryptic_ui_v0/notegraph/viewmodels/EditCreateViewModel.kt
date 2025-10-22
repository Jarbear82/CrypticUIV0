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
    // --- Creation States ---
    private val _nodeCreationState = MutableStateFlow<NodeCreationState?>(null)
    val nodeCreationState = _nodeCreationState.asStateFlow()

    private val _edgeCreationState = MutableStateFlow<EdgeCreationState?>(null)
    val edgeCreationState = _edgeCreationState.asStateFlow()

    private val _nodeSchemaCreationState = MutableStateFlow(NodeSchemaCreationState())
    val nodeSchemaCreationState = _nodeSchemaCreationState.asStateFlow()

    private val _edgeSchemaCreationState = MutableStateFlow(EdgeSchemaCreationState())
    val edgeSchemaCreationState = _edgeSchemaCreationState.asStateFlow()

    // --- Editing States ---
    private val _nodeEditState = MutableStateFlow<NodeTable?>(null)
    val nodeEditState = _nodeEditState.asStateFlow()

    private val _edgeEditState = MutableStateFlow<EdgeTable?>(null)
    val edgeEditState = _edgeEditState.asStateFlow()

    private val _nodeSchemaEditState = MutableStateFlow<NodeSchemaEditState?>(null)
    val nodeSchemaEditState = _nodeSchemaEditState.asStateFlow()

    private val _edgeSchemaEditState = MutableStateFlow<EdgeSchemaEditState?>(null)
    val edgeSchemaEditState = _edgeSchemaEditState.asStateFlow()

    /**
     * Returns the currently active edit state object.
     * Used by MetadataViewModel to get the edited data for comparison.
     */
    fun getCurrentEditState(): Any? {
        return nodeEditState.value
            ?: edgeEditState.value
            ?: nodeSchemaEditState.value
            ?: edgeSchemaEditState.value
    }

    /**
     * Clears all creation and editing states.
     * Called on "Cancel" or when switching tabs.
     */
    fun cancelAllEditing() {
        _nodeCreationState.value = null
        _edgeCreationState.value = null
        // Reset creation states to default
        _nodeSchemaCreationState.value = NodeSchemaCreationState()
        _edgeSchemaCreationState.value = EdgeSchemaCreationState(allNodeSchemas = schemaViewModel.schema.value?.nodeTables ?: emptyList())
        // Clear editing states
        _nodeEditState.value = null
        _edgeEditState.value = null
        _nodeSchemaEditState.value = null
        _edgeSchemaEditState.value = null
    }

    // --- Node Creation ---
    fun initiateNodeCreation() {
        viewModelScope.launch {
            cancelAllEditing() // Clear other states
            val schema = schemaViewModel.schema.value ?: return@launch
            metadataViewModel.clearSelectedItem()
            _nodeCreationState.value = NodeCreationState(schemas = schema.nodeTables)
        }
    }

    fun updateNodeCreationSchema(schemaNode: SchemaNode) {
        _nodeCreationState.update {
            it?.copy(
                selectedSchema = schemaNode,
                properties = emptyMap() // Reset properties when schema changes
            )
        }
    }

    fun updateNodeCreationProperty(key: String, value: String) {
        _nodeCreationState.update { currentState ->
            currentState?.copy(
                properties = currentState.properties.toMutableMap().apply {
                    this[key] = value
                }
            )
        }
    }

    fun createNodeFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            _nodeCreationState.value?.let { state ->
                if (state.selectedSchema != null) {
                    val properties = state.properties.map {
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
    }

    // --- Edge Creation ---
    fun initiateEdgeCreation() {
        viewModelScope.launch {
            cancelAllEditing()
            val schema = schemaViewModel.schema.value ?: return@launch
            if (metadataViewModel.nodeList.value.isEmpty()) {
                metadataViewModel.listNodes()
            }
            metadataViewModel.clearSelectedItem()
            _edgeCreationState.value = EdgeCreationState(
                schemas = schema.edgeTables,
                availableNodes = metadataViewModel.nodeList.value
            )
        }
    }

    fun updateEdgeCreationSchema(schemaEdge: SchemaEdge) {
        _edgeCreationState.update {
            it?.copy(
                selectedSchema = schemaEdge,
                src = null,
                dst = null,
                properties = emptyMap()
            )
        }
    }

    fun updateEdgeCreationSrc(node: NodeDisplayItem) {
        _edgeCreationState.update { it?.copy(src = node) }
    }

    fun updateEdgeCreationDst(node: NodeDisplayItem) {
        _edgeCreationState.update { it?.copy(dst = node) }
    }

    fun updateEdgeCreationProperty(key: String, value: String) {
        _edgeCreationState.update { currentState ->
            currentState?.copy(
                properties = currentState.properties.toMutableMap().apply {
                    this[key] = value
                }
            )
        }
    }

    fun createEdgeFromState(onFinished: () -> Unit) {
        viewModelScope.launch {
            _edgeCreationState.value?.let { state ->
                if (state.selectedSchema != null && state.src != null && state.dst != null) {
                    val properties = state.properties.map {
                        TableProperty(it.key, it.value.toLongOrNull() ?: it.value, false, false)
                    }
                    val edgeTable = EdgeTable(state.selectedSchema.label, state.src!!, state.dst!!, properties, false, false, false, false)
                    createEdge(dbService, edgeTable)
                    cancelAllEditing() // Exit creation mode
                    metadataViewModel.listEdges() // Refresh the edge list
                    onFinished()
                }
            }
        }
    }

    // --- Node Schema Creation ---
    fun initiateNodeSchemaCreation() {
        viewModelScope.launch {
            cancelAllEditing()
            metadataViewModel.setItemToEdit("CreateNodeSchema")
        }
    }

    fun onNodeSchemaTableNameChange(name: String) {
        _nodeSchemaCreationState.update { it.copy(tableName = name) }
    }

    fun onNodeSchemaPropertyChange(index: Int, property: Property) {
        _nodeSchemaCreationState.update {
            val newProperties = it.properties.toMutableList()
            newProperties[index] = property
            it.copy(properties = newProperties)
        }
    }

    fun onAddNodeSchemaProperty() {
        _nodeSchemaCreationState.update {
            it.copy(properties = it.properties + Property())
        }
    }

    fun onRemoveNodeSchemaProperty(index: Int) {
        _nodeSchemaCreationState.update {
            val newProperties = it.properties.toMutableList()
            newProperties.removeAt(index)
            it.copy(properties = newProperties)
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
            cancelAllEditing()
            val nodeSchemas = schemaViewModel.schema.value?.nodeTables ?: emptyList()
            onEdgeSchemaCreationInitiated(nodeSchemas)
            metadataViewModel.setItemToEdit("CreateEdgeSchema")
        }
    }

    private fun onEdgeSchemaCreationInitiated(nodeSchemas: List<SchemaNode>) {
        _edgeSchemaCreationState.update { it.copy(allNodeSchemas = nodeSchemas) }
    }

    fun onEdgeSchemaTableNameChange(name: String) {
        _edgeSchemaCreationState.update { it.copy(tableName = name) }
    }

    fun onEdgeSchemaSrcTableChange(table: String) {
        _edgeSchemaCreationState.update { it.copy(srcTable = table) }
    }

    fun onEdgeSchemaDstTableChange(table: String) {
        _edgeSchemaCreationState.update { it.copy(dstTable = table) }
    }

    fun onEdgeSchemaPropertyChange(index: Int, property: Property) {
        _edgeSchemaCreationState.update {
            val newProperties = it.properties.toMutableList()
            newProperties[index] = property
            it.copy(properties = newProperties)
        }
    }

    fun onAddEdgeSchemaProperty() {
        _edgeSchemaCreationState.update {
            it.copy(properties = it.properties + Property(isPrimaryKey = false))
        }
    }

    fun onRemoveEdgeSchemaProperty(index: Int) {
        _edgeSchemaCreationState.update {
            val newProperties = it.properties.toMutableList()
            newProperties.removeAt(index)
            it.copy(properties = newProperties)
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
        cancelAllEditing()
        // FIX: Create a deep copy to prevent mutating the original state
        _nodeEditState.value = node.deepCopy()
        println("DEBUG: Initiated node edit for: ${node.label}")
    }

    fun updateNodeEditProperty(index: Int, value: String) {
        _nodeEditState.update { currentState ->
            // Use our custom deepCopy here as well
            currentState?.deepCopy()?.apply {
                val prop = this.properties[index]
                val newValue = value.toLongOrNull() ?: value
                // Mutate the property in our copied list
                this.properties = this.properties.toMutableList().also {
                    it[index] = prop.copy(value = newValue, valueChanged = true)
                }
                println("DEBUG: Updated property '${prop.key}' to '$newValue'. valueChanged is now true.")
            }
        }
    }

    // --- Edge Editing ---
    fun initiateEdgeEdit(edge: EdgeTable) {
        cancelAllEditing()
        // FIX: Create a deep copy
        _edgeEditState.value = edge.deepCopy()
        println("DEBUG: Initiated edge edit for: ${edge.label}")
    }

    fun updateEdgeEditProperty(index: Int, value: String) {
        _edgeEditState.update { currentState ->
            // Use our custom deepCopy here as well
            currentState?.deepCopy()?.apply {
                val prop = this.properties?.get(index)
                if (prop != null) {
                    val newValue = value.toLongOrNull() ?: value
                    this.properties = this.properties?.toMutableList()?.also {
                        it[index] = prop.copy(value = newValue, valueChanged = true)
                    }
                    println("DEBUG: Updated edge property '${prop.key}' to '$newValue'. valueChanged is now true.")
                }
            }
        }
    }

    // --- Node Schema Editing ---
    fun initiateNodeSchemaEdit(schema: SchemaNode) {
        cancelAllEditing()
        val editableProperties = schema.properties.map {
            EditableSchemaProperty(
                key = it.key,
                valueDataType = it.valueDataType,
                isPrimaryKey = it.isPrimaryKey,
                originalKey = it.key
            )
        }.toMutableList()
        _nodeSchemaEditState.value = NodeSchemaEditState(
            originalSchema = schema,
            currentLabel = schema.label,
            properties = editableProperties
        )
    }

    fun updateNodeSchemaEditLabel(label: String) {
        _nodeSchemaEditState.update { it?.copy(currentLabel = label) }
    }

    fun updateNodeSchemaEditAddProperty() {
        _nodeSchemaEditState.update {
            it?.copy(
                properties = it.properties.toMutableList().apply {
                    add(EditableSchemaProperty(
                        key = "newProperty",
                        valueDataType = "STRING",
                        isPrimaryKey = false,
                        originalKey = "newProperty",
                        isNew = true
                    ))
                }
            )
        }
    }

    fun updateNodeSchemaEditRemoveProperty(index: Int) {
        _nodeSchemaEditState.update {
            it?.copy(
                properties = it.properties.toMutableList().apply {
                    this[index] = this[index].copy(isDeleted = true)
                }
            )
        }
    }

    fun updateNodeSchemaEditProperty(index: Int, property: EditableSchemaProperty) {
        _nodeSchemaEditState.update {
            it?.copy(
                properties = it.properties.toMutableList().apply {
                    this[index] = property
                }
            )
        }
    }

    // --- Edge Schema Editing ---
    fun initiateEdgeSchemaEdit(schema: SchemaEdge) {
        cancelAllEditing()
        val editableProperties = schema.properties.map {
            EditableSchemaProperty(
                key = it.key,
                valueDataType = it.valueDataType,
                isPrimaryKey = it.isPrimaryKey,
                originalKey = it.key
            )
        }.toMutableList()
        _edgeSchemaEditState.value = EdgeSchemaEditState(
            originalSchema = schema,
            currentLabel = schema.label,
            properties = editableProperties
        )
    }

    fun updateEdgeSchemaEditLabel(label: String) {
        _edgeSchemaEditState.update { it?.copy(currentLabel = label) }
    }

    fun updateEdgeSchemaEditAddProperty() {
        _edgeSchemaEditState.update {
            it?.copy(
                properties = it.properties.toMutableList().apply {
                    add(EditableSchemaProperty(
                        key = "newProperty",
                        valueDataType = "STRING",
                        isPrimaryKey = false,
                        originalKey = "newProperty",
                        isNew = true
                    ))
                }
            )
        }
    }

    fun updateEdgeSchemaEditRemoveProperty(index: Int) {
        _edgeSchemaEditState.update {
            it?.copy(
                properties = it.properties.toMutableList().apply {
                    this[index] = this[index].copy(isDeleted = true)
                }
            )
        }
    }

    fun updateEdgeSchemaEditProperty(index: Int, property: EditableSchemaProperty) {
        _edgeSchemaEditState.update {
            it?.copy(
                properties = it.properties.toMutableList().apply {
                    this[index] = property
                }
            )
        }
    }
}