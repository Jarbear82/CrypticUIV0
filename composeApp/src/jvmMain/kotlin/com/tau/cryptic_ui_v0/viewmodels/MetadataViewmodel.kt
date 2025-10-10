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

    private val _edgeList = MutableStateFlow<List<EdgeDisplayItem>>(emptyList())
    val edgeList = _edgeList.asStateFlow()

    private val _itemToEdit = MutableStateFlow<Any?>(null)
    val itemToEdit = _itemToEdit.asStateFlow()

    private val _primarySelectedItem = MutableStateFlow<Any?>(null)
    val primarySelectedItem = _primarySelectedItem.asStateFlow()

    private val _secondarySelectedItem = MutableStateFlow<Any?>(null)
    val secondarySelectedItem = _secondarySelectedItem.asStateFlow()


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

    fun addEdges(edges: Set<EdgeDisplayItem>) {
        if (edges.isNotEmpty()) {
            println("Updating Edges")
            _edgeList.update { (it + edges).distinct() }
        }
    }

    fun listNodes() {
        viewModelScope.launch {
            val nodes = mutableListOf<NodeDisplayItem>()
            schemaViewModel.schema.value?.nodeTables?.forEach { table ->
                val pk = repository.getPrimaryKey(table.label) ?: return@forEach
                val query = "MATCH (n:${table.label.withBackticks()}) RETURN n.${pk.withBackticks()}"
                val result = repository.executeQuery(query)
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
            val edges = mutableListOf<EdgeDisplayItem>()
            val nodesFromEdges = mutableSetOf<NodeDisplayItem>()
            schemaViewModel.schema.value?.edgeTables?.forEach { table ->
                val srcPkName = repository.getPrimaryKey(table.srcLabel) ?: return@forEach
                val dstPkName = repository.getPrimaryKey(table.dstLabel) ?: return@forEach
                val query = "MATCH (src:${table.srcLabel.withBackticks()})-[r:${table.label.withBackticks()}]->(dst:${table.dstLabel.withBackticks()}) RETURN src.${srcPkName.withBackticks()}, dst.${dstPkName.withBackticks()}"
                val result = repository.executeQuery(query)
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

                        nodesFromEdges.add(srcNode)
                        nodesFromEdges.add(dstNode)
                        edges.add(EdgeDisplayItem(label = table.label, src = srcNode, dst = dstNode))
                    }
                }
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

    fun setItemToEdit(item: Any) {
        viewModelScope.launch {
            _itemToEdit.value = when (item) {
                is NodeDisplayItem -> getNode(item)
                is EdgeDisplayItem -> getEdge(item)
                is SchemaNode, is SchemaEdge, is String -> item
                else -> null
            }
        }
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


    private suspend fun getNode(item: NodeDisplayItem): NodeTable? {
        val pkKey = item.primarykeyProperty.key
        val pkValue = item.primarykeyProperty.value
        val formattedPkValue = formatPkValue(pkValue)

        val query = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue RETURN n"
        val result = repository.executeQuery(query)

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

    private suspend fun getEdge(item: EdgeDisplayItem): EdgeTable? {
        val srcPk = item.src.primarykeyProperty
        val dstPk = item.dst.primarykeyProperty
        val formattedSrcPkValue = formatPkValue(srcPk.value)
        val formattedDstPkValue = formatPkValue(dstPk.value)

        val query = "MATCH (a:${item.src.label.withBackticks()})-[r:${item.label.withBackticks()}]->(b:${item.dst.label.withBackticks()}) " +
                "WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue " +
                "RETURN r LIMIT 1"
        val result = repository.executeQuery(query)

        if (result is ExecutionResult.Success) {
            val edgeValue = result.results.firstOrNull()?.rows?.firstOrNull()?.getOrNull(0) as? EdgeValue
            if (edgeValue != null) {
                val properties = edgeValue.properties.mapNotNull { (key, value) ->
                    if (key.startsWith("_")) return@mapNotNull null
                    TableProperty(
                        key = key,
                        value = value,
                        isPrimaryKey = false,
                        valueChanged = false
                    )
                }
                return EdgeTable(
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
                is EdgeDisplayItem -> deleteEdge(item)
            }
        }
    }

    private suspend fun deleteNode(item: NodeDisplayItem) {
        val pkKey = item.primarykeyProperty.key
        val pkValue = item.primarykeyProperty.value
        val formattedPkValue = formatPkValue(pkValue)
        val query = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue DETACH DELETE n"
        val result = repository.executeQuery(query)
        if (result is ExecutionResult.Success) {
            _nodeList.update { list -> list.filterNot { it == item } }
            // Also remove any edges connected to the deleted node
            _edgeList.update { list -> list.filterNot { it.src == item || it.dst == item } }
        }
    }

    private suspend fun deleteEdge(item: EdgeDisplayItem) {
        val srcPk = item.src.primarykeyProperty
        val dstPk = item.dst.primarykeyProperty
        val formattedSrcPkValue = formatPkValue(srcPk.value)
        val formattedDstPkValue = formatPkValue(dstPk.value)

        // This query deletes ALL edges of this type between the two specific nodes.
        // This is a limitation due to not storing a unique internal ID on the EdgeDisplayItem.
        val query = "MATCH (a:${item.src.label.withBackticks()})-[r:${item.label.withBackticks()}]->(b:${item.dst.label.withBackticks()}) " +
                "WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue " +
                "DELETE r"
        val result = repository.executeQuery(query)
        if (result is ExecutionResult.Success) {
            _edgeList.update { list -> list.filterNot { it == item } }
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
        repository.close()
    }
}