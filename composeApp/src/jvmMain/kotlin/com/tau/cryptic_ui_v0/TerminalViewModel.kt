// CrypticUIV0/composeApp/src/jvmMain/kotlin/com/tau/cryptic_ui_v0/TerminalViewModel.kt
package com.tau.cryptic_ui_v0

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// List of reserved words in kuzu:
val reservedWords: List<String> = listOf(
    "COLUMN", "CREATE", "DBTYPE", "DEFAULT", "GROUP", "HEADERS", "INSTALL", "MACRO", "OPTIONAL",
    "PROFILE", "UNION", "UNWIND", "WITH", "LIMIT", "ONLY", "ORDER", "WHERE", "ALL", "CASE", "CAST",
    "ELSE", "END", "ENDS", "EXISTS", "GLOB", "SHORTEST", "THEN", "WHEN", "NULL", "FALSE", "TRUE",
    "ASC", "ASCENDING", "DESC", "DESCENDING", "ON", "AND","DISTINCT", "IN", "IS", "NOT", "OR",
    "STARTS", "XOR", "FROM", "PRIMARY", "TABLE", "TO"
)

class TerminalViewModel {
    private val dbService = KuzuDBService()
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val _schema = MutableStateFlow<Schema?>(null)
    val schema: StateFlow<Schema?> = _schema

    private val _queryResult = MutableStateFlow<ExecutionResult?>(null)
    val queryResult: StateFlow<ExecutionResult?> = _queryResult

    private val _dbMetaData = MutableStateFlow<DBMetaData?>(null)
    val dbMetaData: StateFlow<DBMetaData?> = _dbMetaData

    private val _nodeList = MutableStateFlow<List<NodeDisplayItem>>(emptyList())
    val nodeList: StateFlow<List<NodeDisplayItem>> = _nodeList

    private val _relationshipList = MutableStateFlow<List<RelDisplayItem>>(emptyList())
    val relationshipList: StateFlow<List<RelDisplayItem>> = _relationshipList

    private val _selectedItem = MutableStateFlow<Any?>(null)
    val selectedItem: StateFlow<Any?> = _selectedItem

    private val _query = mutableStateOf("")
    val query: State<String> = _query

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    init {
        viewModelScope.launch {
            dbService.initialize()
            _dbMetaData.value = dbService.getDBMetaData()
            showSchema()
        }
    }

    private fun String.withBackticks(): String {
        return if (reservedWords.contains(this.uppercase())) "`$this`" else this
    }

    private fun formatPkValue(value: Any?): String {
        return if (value is String) "'$value'" else value.toString()
    }

    fun executeQuery() {
        viewModelScope.launch {
            clearNodeList()
            clearEdgeList()
            val result = dbService.executeQuery(query.value)
            _queryResult.value = result
            if (result !is ExecutionResult.Success) return@launch

            if (result.isSchemaChanged) {
                showSchema()
            }

            // --- Pass 1: Gather all raw node and relationship maps from the result ---
            val rawNodeMaps = mutableListOf<Map<String, Any?>>()
            val rawRelMaps = mutableListOf<Map<String, Any?>>()

            fun findRawData(value: Any?) {
                when (value) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val properties = value as Map<String, Any?>
                        when {
                            properties.containsKey("_nodes") && properties.containsKey("_rels") -> { // Path/Recursive Rel
                                (properties["_nodes"] as? List<*>)?.forEach(::findRawData)
                                (properties["_rels"] as? List<*>)?.forEach(::findRawData)
                            }
                            properties.containsKey("_src") && properties.containsKey("_dst") -> rawRelMaps.add(properties) // Rel
                            properties.containsKey("_label") -> rawNodeMaps.add(properties) // Node
                        }
                    }
                    is List<*> -> value.forEach(::findRawData)
                }
            }

            result.results.forEach { formattedResult ->
                formattedResult.rows.forEach { row ->
                    row.forEach(::findRawData)
                }
            }

            // --- Pass 2: Process raw node maps into NodeDisplayItems ---
            // A temporary map from internal Kuzu ID to our new NodeDisplayItem is needed to link relationships.
            val tempNodeMap = mutableMapOf<String, NodeDisplayItem>()
            rawNodeMaps.forEach { properties ->
                val id = properties["_id"]?.toString()
                val label = properties["_label"]?.toString()
                if (id != null && label != null) {
                    val pkName = dbService.getPrimaryKey(label) ?: "_id"
                    val pkValue = properties[pkName]
                    tempNodeMap[id] = NodeDisplayItem(
                        label = label,
                        primarykeyProperty = DisplayItemProperty(key = pkName, value = pkValue)
                    )
                }
            }

            // --- Pass 3: Process raw relationship maps into RelDisplayItems ---
            val newRels = mutableListOf<RelDisplayItem>()
            rawRelMaps.forEach { properties ->
                val label = properties["_label"]?.toString()
                val srcId = properties["_src"]?.toString()
                val dstId = properties["_dst"]?.toString()

                val srcNode = tempNodeMap[srcId]
                val dstNode = tempNodeMap[dstId]

                if (label != null && srcNode != null && dstNode != null) {
                    newRels.add(RelDisplayItem(
                        label = label,
                        src = srcNode,
                        dst = dstNode
                    ))
                }
            }

            // --- Final Step: Add new items to the UI state, ensuring no duplicates ---
            if (tempNodeMap.isNotEmpty()) {
                _nodeList.update { (it + tempNodeMap.values).distinct() }
            }
            if (newRels.isNotEmpty()) {
                _relationshipList.update { (it + newRels).distinct() }
            }
        }
    }

    suspend fun showSchema() {
        _schema.value = dbService.getSchema()
    }

    fun listNodes() {
        viewModelScope.launch {
            val nodes = mutableListOf<NodeDisplayItem>()
            _schema.value?.nodeTables?.forEach { table ->
                val pk = dbService.getPrimaryKey(table.label) ?: return@forEach
                val q = "MATCH (n:${table.label.withBackticks()}) RETURN n.${pk.withBackticks()}"
                val result = dbService.executeQuery(q)
                if (result is ExecutionResult.Success) {
                    result.results.firstOrNull()?.rows?.forEach { row ->
                        nodes.add(NodeDisplayItem(
                            label = table.label,
                            primarykeyProperty = DisplayItemProperty(key = pk, value = row[0])
                        ))
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
            _schema.value?.relTables?.forEach { table ->
                val srcPkName = dbService.getPrimaryKey(table.srcLabel) ?: return@forEach
                val dstPkName = dbService.getPrimaryKey(table.dstLabel) ?: return@forEach
                val q = "MATCH (src:${table.srcLabel.withBackticks()})-[r:${table.label.withBackticks()}]->(dst:${table.dstLabel.withBackticks()}) RETURN src.${srcPkName.withBackticks()}, dst.${dstPkName.withBackticks()}"
                val result = dbService.executeQuery(q)
                if (result is ExecutionResult.Success) {
                    result.results.firstOrNull()?.rows?.forEach { row ->
                        val srcPkValue = row[0]
                        val dstPkValue = row[1]

                        val srcNode = NodeDisplayItem(
                            label = table.srcLabel,
                            primarykeyProperty = DisplayItemProperty(key = srcPkName, value = srcPkValue)
                        )
                        val dstNode = NodeDisplayItem(
                            label = table.dstLabel,
                            primarykeyProperty = DisplayItemProperty(key = dstPkName, value = dstPkValue)
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

    fun clearQueryResult() {
        _queryResult.value = null
    }

    fun selectItem(item: Any) {
        viewModelScope.launch {
            when (item) {
                is NodeTable -> {
                    val pkProperty = item.properties.find { it.isPrimaryKey } ?: return@launch
                    val pkKey = pkProperty.key
                    val pkValue = pkProperty.value
                    val formattedPkValue = formatPkValue(pkValue)

                    val q = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue RETURN n"
                    val result = dbService.executeQuery(q)

                    if (result is ExecutionResult.Success) {
                        result.results.firstOrNull()?.rows?.firstOrNull()?.getOrNull(0)?.let { nodeData ->
                            val propertiesMap = nodeData as? Map<String, Any?>
                            if (propertiesMap != null) {
                                val newProperties = propertiesMap.map { (key, value) ->
                                    TableProperty(
                                        key = key,
                                        value = value,
                                        isPrimaryKey = (key == pkKey),
                                        valueChanged = false
                                    )
                                }
                                _selectedItem.value = item.copy(properties = newProperties)
                            }
                        }
                    }
                }
                is RelTable -> {
                    val srcPk = item.src.primarykeyProperty
                    val dstPk = item.dst.primarykeyProperty
                    val formattedSrcPkValue = formatPkValue(srcPk.value)
                    val formattedDstPkValue = formatPkValue(dstPk.value)

                    val q = "MATCH (a:${item.src.label.withBackticks()})-[r:${item.label.withBackticks()}]->(b:${item.dst.label.withBackticks()}) " +
                            "WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue " +
                            "RETURN r LIMIT 1"
                    val result = dbService.executeQuery(q)

                    if (result is ExecutionResult.Success) {
                        result.results.firstOrNull()?.rows?.firstOrNull()?.getOrNull(0)?.let { relData ->
                            val propertiesMap = relData as? Map<String, Any?>
                            if (propertiesMap != null) {
                                val newProperties = propertiesMap.mapNotNull { (key, value) ->
                                    if (key.startsWith("_")) return@mapNotNull null
                                    TableProperty(key = key, value = value, isPrimaryKey = false, valueChanged = false)
                                }
                                _selectedItem.value = item.copy(properties = newProperties)
                            }
                        }
                    }
                }
            }
        }
    }

    fun deleteDisplayItem(item: Any) {
        viewModelScope.launch {
            when (item) {
                is NodeDisplayItem -> deleteNode(item)
                is RelDisplayItem -> deleteRel(item)
            }
        }
    }

    fun deleteTableItem(item: Any) {
        // TODO: Implement logic to delete selected item which will always be a node or table item
    }

    private suspend fun deleteNode(item: NodeDisplayItem) {
        val pkKey = item.primarykeyProperty.key
        val pkValue = item.primarykeyProperty.value
        val formattedPkValue = formatPkValue(pkValue)
        val q = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue DETACH DELETE n"
        val result = dbService.executeQuery(q)
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
        val result = dbService.executeQuery(q)
        if (result is ExecutionResult.Success) {
            _relationshipList.update { list -> list.filterNot { it == item } }
        }
    }

    fun clearNodeList() {
        _nodeList.value = emptyList<NodeDisplayItem>()
    }

    fun clearEdgeList() {
        _relationshipList.value = emptyList<RelDisplayItem>()
    }

    fun clearSelectedItem() {
        _selectedItem.value = null
    }

    fun onCleared() {
        dbService.close()
    }
}