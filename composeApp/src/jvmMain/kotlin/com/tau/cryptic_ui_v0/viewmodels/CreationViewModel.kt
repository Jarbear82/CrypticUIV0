package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreationViewModel(
    private val repository: KuzuRepository,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel,
    private val metadataViewModel: MetadataViewModel,
) {
    private val _nodeCreationState = MutableStateFlow<NodeCreationState?>(null)
    val nodeCreationState = _nodeCreationState.asStateFlow()

    private val _edgeCreationState = MutableStateFlow<EdgeCreationState?>(null)
    val edgeCreationState = _edgeCreationState.asStateFlow()

    private val _nodeSchemaCreationState = MutableStateFlow(NodeSchemaCreationState())
    val nodeSchemaCreationState = _nodeSchemaCreationState.asStateFlow()

    private val _edgeSchemaCreationState = MutableStateFlow(EdgeSchemaCreationState())
    val edgeSchemaCreationState = _edgeSchemaCreationState.asStateFlow()

    private fun String.withBackticks(): String {
        return if (reservedWords.contains(this.uppercase())) "`$this`" else this
    }

    private fun formatPkValue(value: Any?): String {
        return if (value is String) "'$value'" else value.toString()
    }

    // --- Node Creation ---
    fun initiateNodeCreation() {
        viewModelScope.launch {
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
                    val label = state.selectedSchema.label
                    val properties = state.properties.mapValues { entry ->
                        entry.value.toLongOrNull() ?: entry.value
                    }
                    val propertiesString = properties.entries.joinToString(", ") {
                        "${it.key.withBackticks()}: ${formatPkValue(it.value)}"
                    }
                    val q = "CREATE (n:${label.withBackticks()} {${propertiesString}})"
                    repository.executeQuery(q)
                    _nodeCreationState.value = null // Exit creation mode
                    metadataViewModel.listNodes() // Refresh the node list
                    onFinished()
                }
            }
        }
    }

    fun cancelNodeCreation() {
        _nodeCreationState.value = null
    }

    // --- Edge Creation ---
    fun initiateEdgeCreation() {
        viewModelScope.launch {
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
                    val label = state.selectedSchema.label
                    val src = state.src
                    val dst = state.dst
                    val properties = state.properties.mapValues { entry ->
                        entry.value.toLongOrNull() ?: entry.value
                    }
                    val srcPk = src.primarykeyProperty
                    val dstPk = dst.primarykeyProperty
                    val formattedSrcPkValue = formatPkValue(srcPk.value)
                    val formattedDstPkValue = formatPkValue(dstPk.value)

                    val propertiesString = if (properties.isNotEmpty()) {
                        "{${properties.entries.joinToString(", ") { "${it.key.withBackticks()}: ${formatPkValue(it.value)}" }}}"
                    } else {
                        ""
                    }

                    val q = """
                        MATCH (a:${src.label.withBackticks()}), (b:${dst.label.withBackticks()})
                        WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue
                        CREATE (a)-[r:${label.withBackticks()} $propertiesString]->(b)
                    """.trimIndent()
                    repository.executeQuery(q)

                    _edgeCreationState.value = null // Exit creation mode
                    metadataViewModel.listEdges() // Refresh the edge list
                    onFinished()
                }
            }
        }
    }

    fun cancelEdgeCreation() {
        _edgeCreationState.value = null
    }

    // --- Node Schema Creation ---
    fun initiateNodeSchemaCreation() {
        metadataViewModel.selectItem("CreateNodeSchema")
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
            val pk = state.properties.first { it.isPrimaryKey }
            val properties = state.properties.joinToString(", ") {
                "${it.name.withBackticks()} ${it.type}"
            }
            val q = "CREATE NODE TABLE ${state.tableName.withBackticks()} ($properties, PRIMARY KEY (${pk.name.withBackticks()}))"
            repository.executeQuery(q)
            onFinished()
            schemaViewModel.showSchema()
        }
    }

    fun cancelNodeSchemaCreation() {
        metadataViewModel.clearSelectedItem()
    }


    // --- Edge Schema Creation ---
    fun initiateEdgeSchemaCreation() {
        val nodeSchemas = schemaViewModel.schema.value?.nodeTables ?: emptyList()
        onEdgeSchemaCreationInitiated(nodeSchemas)
        metadataViewModel.selectItem("CreateEdgeSchema")
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
            val properties = if (state.properties.isNotEmpty()) {
                ", " + state.properties.joinToString(", ") {
                    "${it.name.withBackticks()} ${it.type}"
                }
            } else {
                ""
            }
            val q = "CREATE REL TABLE ${state.tableName.withBackticks()} (FROM ${state.srcTable!!.withBackticks()} TO ${state.dstTable!!.withBackticks()}$properties)"
            repository.executeQuery(q)
            onFinished()
            schemaViewModel.showSchema()
        }
    }

    fun cancelEdgeSchemaCreationFromState() {
        metadataViewModel.clearSelectedItem()
    }
}