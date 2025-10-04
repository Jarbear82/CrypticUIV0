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

    private val _relationshipList = MutableStateFlow<List<RelDisplayItem>>(emptyList())
    val relationshipList = _relationshipList.asStateFlow()

    private val _selectedItem = MutableStateFlow<Any?>(null)
    val selectedItem = _selectedItem.asStateFlow()

    private val _nodeCreationState = MutableStateFlow<NodeCreationState?>(null)
    val nodeCreationState = _nodeCreationState.asStateFlow()

    private val _relCreationState = MutableStateFlow<RelCreationState?>(null)
    val relCreationState = _relCreationState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initialize()
            _dbMetaData.value = repository.getDBMetaData()
        }
    }

    private fun String.withBackticks(): String {
        return if (reservedWords.contains(this.uppercase())) "`$this`" else this
    }

    private fun formatPkValue(value: Any?): String {
        return if (value is String) "'$value'" else value.toString()
    }

    fun addNodes(nodes: Set<NodeDisplayItem>) {
        if (nodes.isNotEmpty()) {
            println("Updating Nodes")
            _nodeList.update { (it + nodes).distinct() }
        }
    }

    fun addRels(rels: Set<RelDisplayItem>) {
        if (rels.isNotEmpty()) {
            println("Updating Rels")
            _relationshipList.update { (it + rels).distinct() }
        }
    }

    fun listNodes() {
        viewModelScope.launch {
            val nodes = mutableListOf<NodeDisplayItem>()
            schemaViewModel.schema.value?.nodeTables?.forEach { table ->
                val pk = repository.getPrimaryKey(table.label) ?: return@forEach
                val q = "MATCH (n:${table.label.withBackticks()}) RETURN n.${pk.withBackticks()}"
                val result = repository.executeQuery(q)
                if (result is ExecutionResult.Success) {
                    result.results.firstOrNull()?.rows?.forEach { row ->
                        nodes.add(
                            NodeDisplayItem(
                                label = table.label,
                                primarykeyProperty = DisplayItemProperty(key = pk, value = row[0])
                            )
                        )
                    }
                }
            }
            _nodeList.value = nodes
        }
    }

    fun listEdges() {
        viewModelScope.launch {
            val rels = mutableListOf<RelDisplayItem>()
            val nodesFromRels = mutableSetOf<NodeDisplayItem>()
            schemaViewModel.schema.value?.relTables?.forEach { table ->
                val srcPkName = repository.getPrimaryKey(table.srcLabel) ?: return@forEach
                val dstPkName = repository.getPrimaryKey(table.dstLabel) ?: return@forEach
                val q = "MATCH (src:${table.srcLabel.withBackticks()})-[r:${table.label.withBackticks()}]->(dst:${table.dstLabel.withBackticks()}) RETURN src.${srcPkName.withBackticks()}, dst.${dstPkName.withBackticks()}"
                val result = repository.executeQuery(q)
                if (result is ExecutionResult.Success) {
                    result.results.firstOrNull()?.rows?.forEach { row ->
                        val srcPkValue = row[0]
                        val dstPkValue = row[1]

                        val srcNode = NodeDisplayItem(
                            label = table.srcLabel,
                            primarykeyProperty = DisplayItemProperty(
                                key = srcPkName,
                                value = srcPkValue
                            )
                        )
                        val dstNode = NodeDisplayItem(
                            label = table.dstLabel,
                            primarykeyProperty = DisplayItemProperty(
                                key = dstPkName,
                                value = dstPkValue
                            )
                        )

                        nodesFromRels.add(srcNode)
                        nodesFromRels.add(dstNode)
                        rels.add(RelDisplayItem(label = table.label, src = srcNode, dst = dstNode))
                    }
                }
            }
            _relationshipList.value = rels
            _nodeList.update { (it + nodesFromRels).distinct() }
        }
    }

    fun listAll() {
        viewModelScope.launch {
            listNodes()
            listEdges()
        }
    }

    fun initiateNodeCreation() {
        viewModelScope.launch {
            val schema = schemaViewModel.schema.value ?: return@launch
            _selectedItem.value = null // Clear any selected item
            _nodeCreationState.value = NodeCreationState(schemas = schema.nodeTables)
        }
    }
    fun initiateRelCreation() {
        viewModelScope.launch {
            val schema = schemaViewModel.schema.value ?: return@launch
            if (_nodeList.value.isEmpty()) {
                listNodes()
            }
            _selectedItem.value = null // Clear any selected item
            _relCreationState.value = RelCreationState(
                schemas = schema.relTables,
                availableNodes = _nodeList.value
            )
        }
    }
    fun initiateNodeSchemaCreation() {
        _selectedItem.value = "CreateNodeSchema"
    }

    fun initiateRelSchemaCreation() {
        _selectedItem.value = "CreateRelSchema"
    }

    fun cancelNodeCreation() {
        _nodeCreationState.value = null
    }
    fun cancelRelCreation() {
        _relCreationState.value = null
    }

    fun cancelNodeSchemaCreation() {
        _selectedItem.value = null
    }

    fun cancelRelSchemaCreation() {
        _selectedItem.value = null
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

    fun createNodeFromState() {
        viewModelScope.launch {
            _nodeCreationState.value?.let { state ->
                if (state.selectedSchema != null) {
                    val label = state.selectedSchema.label
                    // This is a simplification, we might need type conversion based on schema
                    val properties = state.properties.mapValues { entry ->
                        entry.value.toLongOrNull() ?: entry.value
                    }
                    createNode(label, properties)
                    _nodeCreationState.value = null // Exit creation mode
                    listNodes() // Refresh the node list
                }
            }
        }
    }

    private suspend fun createNode(label: String, properties: Map<String, Any?>) {
        val propertiesString = properties.entries.joinToString(", ") {
            "${it.key.withBackticks()}: ${formatPkValue(it.value)}"
        }
        val q = "CREATE (n:${label.withBackticks()} {${propertiesString}})"
        repository.executeQuery(q)
    }

    fun selectItem(item: Any) {
        viewModelScope.launch {
            _nodeCreationState.value = null // Exit node creation mode if active
            _relCreationState.value = null // Exit rel creation mode if active
            _selectedItem.value = when (item) {
                is NodeDisplayItem -> getNode(item)
                is RelDisplayItem -> getRel(item)
                is SchemaNode, is SchemaRel -> item
                else -> null
            }
        }
    }
    fun updateRelCreationSchema(schemaRel: SchemaRel) {
        _relCreationState.update {
            it?.copy(
                selectedSchema = schemaRel,
                src = null,
                dst = null,
                properties = emptyMap()
            )
        }
    }

    fun updateRelCreationSrc(node: NodeDisplayItem) {
        _relCreationState.update { it?.copy(src = node) }
    }

    fun updateRelCreationDst(node: NodeDisplayItem) {
        _relCreationState.update { it?.copy(dst = node) }
    }

    fun updateRelCreationProperty(key: String, value: String) {
        _relCreationState.update { currentState ->
            currentState?.copy(
                properties = currentState.properties.toMutableMap().apply {
                    this[key] = value
                }
            )
        }
    }
    fun createRelFromState() {
        viewModelScope.launch {
            _relCreationState.value?.let { state ->
                if (state.selectedSchema != null && state.src != null && state.dst != null) {
                    val label = state.selectedSchema.label
                    val src = state.src
                    val dst = state.dst
                    val properties = state.properties.mapValues { entry ->
                        entry.value.toLongOrNull() ?: entry.value
                    }
                    createRel(label, src, dst, properties)
                    _relCreationState.value = null // Exit creation mode
                    listEdges() // Refresh the edge list
                }
            }
        }
    }
    private suspend fun createRel(label: String, src: NodeDisplayItem, dst: NodeDisplayItem, properties: Map<String, Any?>) {
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
    }

    private suspend fun getNode(item: NodeDisplayItem): NodeTable? {
        val pkKey = item.primarykeyProperty.key
        val pkValue = item.primarykeyProperty.value
        val formattedPkValue = formatPkValue(pkValue)

        val q = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue RETURN n"
        val result = repository.executeQuery(q)

        if (result is ExecutionResult.Success) {
            val nodeValue = result.results.firstOrNull()?.rows?.firstOrNull()?.getOrNull(0) as? NodeValue
            if (nodeValue != null) {
                val properties = nodeValue.properties.map { (key, value) ->
                    TableProperty(
                        key = key,
                        value = value,
                        isPrimaryKey = (key == pkKey),
                        valueChanged = false
                    )
                }
                return NodeTable(
                    label = item.label,
                    properties = properties,
                    labelChanged = false,
                    propertiesChanged = false
                )
            }
        }
        return null
    }

    private suspend fun getRel(item: RelDisplayItem): RelTable? {
        val srcPk = item.src.primarykeyProperty
        val dstPk = item.dst.primarykeyProperty
        val formattedSrcPkValue = formatPkValue(srcPk.value)
        val formattedDstPkValue = formatPkValue(dstPk.value)

        val q = "MATCH (a:${item.src.label.withBackticks()})-[r:${item.label.withBackticks()}]->(b:${item.dst.label.withBackticks()}) " +
                "WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue " +
                "RETURN r LIMIT 1"
        val result = repository.executeQuery(q)

        if (result is ExecutionResult.Success) {
            val relValue = result.results.firstOrNull()?.rows?.firstOrNull()?.getOrNull(0) as? RelValue
            if (relValue != null) {
                val properties = relValue.properties.mapNotNull { (key, value) ->
                    if (key.startsWith("_")) return@mapNotNull null
                    TableProperty(
                        key = key,
                        value = value,
                        isPrimaryKey = false,
                        valueChanged = false
                    )
                }
                return RelTable(
                    label = item.label,
                    src = item.src,
                    dst = item.dst,
                    properties = properties,
                    labelChanged = false,
                    srcChanged = false,
                    dstChanged = false,
                    propertiesChanged = false
                )
            }
        }
        return null
    }


    fun deleteDisplayItem(item: Any) {
        viewModelScope.launch {
            when (item) {
                is NodeDisplayItem -> deleteNode(item)
                is RelDisplayItem -> deleteRel(item)
            }
        }
    }

    private suspend fun deleteNode(item: NodeDisplayItem) {
        val pkKey = item.primarykeyProperty.key
        val pkValue = item.primarykeyProperty.value
        val formattedPkValue = formatPkValue(pkValue)
        val q = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue DETACH DELETE n"
        val result = repository.executeQuery(q)
        if (result is ExecutionResult.Success) {
            _nodeList.update { list -> list.filterNot { it == item } }
            // Also remove any relationships connected to the deleted node
            _relationshipList.update { list -> list.filterNot { it.src == item || it.dst == item } }
        }
    }

    private suspend fun deleteRel(item: RelDisplayItem) {
        val srcPk = item.src.primarykeyProperty
        val dstPk = item.dst.primarykeyProperty
        val formattedSrcPkValue = formatPkValue(srcPk.value)
        val formattedDstPkValue = formatPkValue(dstPk.value)

        // This query deletes ALL relationships of this type between the two specific nodes.
        // This is a limitation due to not storing a unique internal ID on the RelDisplayItem.
        val q = "MATCH (a:${item.src.label.withBackticks()})-[r:${item.label.withBackticks()}]->(b:${item.dst.label.withBackticks()}) " +
                "WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue " +
                "DELETE r"
        val result = repository.executeQuery(q)
        if (result is ExecutionResult.Success) {
            _relationshipList.update { list -> list.filterNot { it == item } }
        }
    }


    fun clearNodeList() {
        _nodeList.value = emptyList()
    }

    fun clearEdgeList() {
        _relationshipList.value = emptyList()
    }

    fun clearSelectedItem() {
        _selectedItem.value = null
    }

    fun onCleared() {
        repository.close()
    }
}