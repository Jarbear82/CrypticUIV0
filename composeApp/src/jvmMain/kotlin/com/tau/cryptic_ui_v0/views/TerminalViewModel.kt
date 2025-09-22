package com.tau.cryptic_ui_v0.views

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.tau.cryptic_ui_v0.DBMetaData
import com.tau.cryptic_ui_v0.DisplayItemProperty
import com.tau.cryptic_ui_v0.ExecutionResult
import com.tau.cryptic_ui_v0.KuzuDBService
import com.tau.cryptic_ui_v0.NodeCreationState
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.NodeSchemaCreationState
import com.tau.cryptic_ui_v0.NodeTable
import com.tau.cryptic_ui_v0.NodeValue
import com.tau.cryptic_ui_v0.RecursiveRelValue
import com.tau.cryptic_ui_v0.RelCreationState
import com.tau.cryptic_ui_v0.RelDisplayItem
import com.tau.cryptic_ui_v0.RelSchemaCreationState
import com.tau.cryptic_ui_v0.RelTable
import com.tau.cryptic_ui_v0.RelValue
import com.tau.cryptic_ui_v0.Schema
import com.tau.cryptic_ui_v0.SchemaNode
import com.tau.cryptic_ui_v0.SchemaProperty
import com.tau.cryptic_ui_v0.SchemaRel
import com.tau.cryptic_ui_v0.TableProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

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

    private val _nodeCreationState = MutableStateFlow<NodeCreationState?>(null)
    val nodeCreationState: StateFlow<NodeCreationState?> = _nodeCreationState

    private val _relCreationState = MutableStateFlow<RelCreationState?>(null)
    val relCreationState: StateFlow<RelCreationState?> = _relCreationState

    private val _nodeSchemaCreationState = MutableStateFlow<NodeSchemaCreationState?>(null)
    val nodeSchemaCreationState: StateFlow<NodeSchemaCreationState?> = _nodeSchemaCreationState

    private val _relSchemaCreationState = MutableStateFlow<RelSchemaCreationState?>(null)
    val relSchemaCreationState: StateFlow<RelSchemaCreationState?> = _relSchemaCreationState


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
                println("Schema Changed. Updating Schema")
                showSchema()
            }

            val tempNodeMap: Map<String, NodeDisplayItem>

            // First Pass: Collect all unique NodeValues from the result
            println("First Pass: Find all nodes")
            val nodeValues = mutableSetOf<NodeValue>()
            fun findNodeValues(value: Any?) {
                when (value) {
                    is NodeValue -> nodeValues.add(value)
                    is RecursiveRelValue -> value.nodes.forEach(::findNodeValues)
                    is List<*> -> value.forEach(::findNodeValues)
                }
            }
            result.results.forEach { formattedResult ->
                formattedResult.rows.forEach { row ->
                    row.forEach(::findNodeValues)
                }
            }

            // Process all nodes concurrently and wait for them to finish
            println("Processing ${nodeValues.size} nodes...")
            val nodeItems = async {
                nodeValues.map { nodeValue ->
                    async {
                        val pkName = dbService.getPrimaryKey(nodeValue.label) ?: "_id"
                        val pkValue = nodeValue.properties[pkName]
                        val nodeItem = NodeDisplayItem(
                            label = nodeValue.label,
                            primarykeyProperty = DisplayItemProperty(key = pkName, value = pkValue)
                        )
                        // Return a pair of the node's ID and the created item
                        nodeValue.id to nodeItem
                    }
                }.awaitAll()
            }.await()
            // Create the map after all nodes have been processed
            tempNodeMap = nodeItems.toMap()
            val newNodes = tempNodeMap.values.toSet()

            // Second Pass: Now that tempNodeMap is populated, find all relationships
            println("Second Pass: Find all relationships")

            val newRels = mutableSetOf<RelDisplayItem>()
            fun findRelData(value: Any?) {
                when (value) {
                    is RelValue -> {
                        val srcNode = tempNodeMap[value.src]
                        val dstNode = tempNodeMap[value.dst]
                        if (srcNode != null && dstNode != null) {
                            newRels.add(
                                RelDisplayItem(
                                    label = value.label,
                                    src = srcNode,
                                    dst = dstNode
                                )
                            )
                        }
                    }
                    is RecursiveRelValue -> value.rels.forEach(::findRelData)
                    is List<*> -> value.forEach(::findRelData)
                }
            }

            // TODO: If no nodes were returned and rels were, retrieve their acompanying nodes

            result.results.forEach { formattedResult ->
                formattedResult.rows.forEach { row ->
                    row.forEach(::findRelData)
                }
            }

            // Final Step: Update the UI state
            println("--- Final Step: Add new items to the UI state ---")
            if (newNodes.isNotEmpty()) {
                println("Updating Nodes")
                _nodeList.update { (it + newNodes).distinct() }
            }
            if (newRels.isNotEmpty()) {
                println("Updating Rels")
                _relationshipList.update { (it + newRels).distinct() }
            }
        }
    }

    suspend fun showSchema() {
        println("\n\nShowing Schema...")

        // Call show tables
        val execResult = dbService.executeQuery("CALL SHOW_TABLES() RETURN *;")
        if (execResult !is ExecutionResult.Success) {
            return // Handle error case
        }

        val nodeSchemaList = mutableListOf<SchemaNode>()
        val relSchemaList = mutableListOf<SchemaRel>()
        val allTables = execResult.results.first().rows.map { it[1] as String to it[2] as String }

        // List Schema Tables
        for ((tableName, tableType) in allTables) {
            // For each table, create schema
            when (tableType) {
                "NODE" -> {
                    // FOR NODE
                    val tableInfoResult = dbService.executeQuery("CALL TABLE_INFO(\"$tableName\") RETURN *;")
                    if (tableInfoResult is ExecutionResult.Success) {
                        val properties = tableInfoResult.results.first().rows.map { row ->
                            // Add Each Property name and type
                            val propName = row[1].toString()
                            val propType = row[2].toString()
                            val isPrimaryKey = row[4] as Boolean
                            SchemaProperty(propName, propType, isPrimaryKey)
                        }
                        // Create Node Schema and add to Node Schema List
                        nodeSchemaList.add(SchemaNode(tableName, properties))
                    }
                }
                "REL" -> {
                    // FOR EDGE
                    val tableInfoResult = dbService.executeQuery("CALL TABLE_INFO(\"$tableName\") RETURN *;")
                    val properties = if (tableInfoResult is ExecutionResult.Success) {
                        // Add Properties (if any)
                        tableInfoResult.results.first().rows.map { row ->
                            val propName = row[1].toString()
                            val propType = row[2].toString()
                            // For REL tables, the 5th column is `storage_direction`, not a boolean, so we can't cast it.
                            // We can assume `isPrimaryKey` is always false for relationship properties.
                            SchemaProperty(propName, propType, isPrimaryKey = false)
                        }
                    } else {
                        emptyList()
                    }

                    // Call Show Connection
                    val showConnectionResult = dbService.executeQuery("CALL SHOW_CONNECTION(\"$tableName\") RETURN *;")
                    if (showConnectionResult is ExecutionResult.Success) {
                        // Add TO and FROM (src and dst)
                        val connectionRows = showConnectionResult.results.first().rows
                        for (row in connectionRows) {
                            val srcLabel = row[0] as String
                            val dstLabel = row[1] as String
                            // Create Rel Schema and add to Rel Schema List
                            relSchemaList.add(SchemaRel(tableName, srcLabel, dstLabel, properties))
                        }
                    }
                }
                "RECURSIVE_REL" -> {
                    // Recursive relationships are internally represented as STRUCT{LIST [NODE], LIST [REL]},
                    // but for schema representation, they can be treated as RELs for now.
                    // Call Show Connection
                    val showConnectionResult = dbService.executeQuery("CALL SHOW_CONNECTION(\"$tableName\") RETURN *;")
                    if (showConnectionResult is ExecutionResult.Success) {
                        val connectionRows = showConnectionResult.results.first().rows
                        for (row in connectionRows) {
                            val srcLabel = row[0] as String
                            val dstLabel = row[1] as String
                            // Create Rel Schema and add to Rel Schema List
                            // Note: Recursive relationships do not have properties according to the provided context.
                            // You can add logic to get properties if they become available in the future.
                            relSchemaList.add(SchemaRel(tableName, srcLabel, dstLabel, emptyList()))
                        }
                    }
                }
            }
        }

        // Create Schema containing Node and Rel tables
        _schema.value = Schema(nodeTables = nodeSchemaList, relTables = relSchemaList)
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

    fun clearQueryResult() {
        _queryResult.value = null
    }

    fun initiateNodeCreation() {
        viewModelScope.launch {
            val schema = _schema.value ?: dbService.getSchema().also { _schema.value = it }
            if (schema != null) {
                _selectedItem.value = null // Clear any selected item
                _nodeCreationState.value = NodeCreationState(schemas = schema.nodeTables)
            }
        }
    }
    fun initiateRelCreation() {
        viewModelScope.launch {
            val schema = _schema.value ?: dbService.getSchema().also { _schema.value = it }
            if (schema != null) {
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
    }
    fun initiateNodeSchemaCreation() {
        _selectedItem.value = null
        _nodeSchemaCreationState.value = NodeSchemaCreationState()
    }

    fun initiateRelSchemaCreation() {
        _selectedItem.value = null
        _relSchemaCreationState.value =
            RelSchemaCreationState(allNodeSchemas = _schema.value?.nodeTables ?: emptyList())
    }


    fun cancelNodeCreation() {
        _nodeCreationState.value = null
    }
    fun cancelRelCreation() {
        _relCreationState.value = null
    }

    fun cancelNodeSchemaCreation() {
        _nodeSchemaCreationState.value = null
    }

    fun cancelRelSchemaCreation() {
        _relSchemaCreationState.value = null
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
        dbService.executeQuery(q)
    }

    private suspend fun getNode(item: NodeDisplayItem): NodeTable? {
        val pkKey = item.primarykeyProperty.key
        val pkValue = item.primarykeyProperty.value
        val formattedPkValue = formatPkValue(pkValue)

        val q = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue RETURN n"
        val result = dbService.executeQuery(q)

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
        val result = dbService.executeQuery(q)

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

    fun selectItem(item: Any) {
        viewModelScope.launch {
            _nodeCreationState.value = null // Exit node creation mode if active
            _relCreationState.value = null // Exit rel creation mode if active
            _nodeSchemaCreationState.value = null
            _relSchemaCreationState.value = null
            _selectedItem.value = when (item) {
                is NodeDisplayItem -> getNode(item)
                is RelDisplayItem -> getRel(item)
                is SchemaNode -> item
                is SchemaRel -> item
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
        dbService.executeQuery(q)
    }

    fun createNodeSchemaFromState(state: NodeSchemaCreationState) {
        viewModelScope.launch {
            val pk = state.properties.first { it.isPrimaryKey }
            val properties = state.properties.joinToString(", ") {
                "${it.name.withBackticks()} ${it.type}"
            }
            val q = "CREATE NODE TABLE ${state.tableName.withBackticks()} ($properties, PRIMARY KEY (${pk.name.withBackticks()}))"
            dbService.executeQuery(q)
            _nodeSchemaCreationState.value = null
            showSchema()
        }
    }

    fun createRelSchemaFromState(state: RelSchemaCreationState) {
        viewModelScope.launch {
            val properties = if (state.properties.isNotEmpty()) {
                ", " + state.properties.joinToString(", ") {
                    "${it.name.withBackticks()} ${it.type}"
                }
            } else {
                ""
            }
            val q = "CREATE REL TABLE ${state.tableName.withBackticks()} (FROM ${state.srcTable!!.withBackticks()} TO ${state.dstTable!!.withBackticks()}$properties)"
            dbService.executeQuery(q)
            _relSchemaCreationState.value = null
            showSchema()
        }
    }

    fun deleteDisplayItem(item: Any) {
        viewModelScope.launch {
            when (item) {
                is NodeDisplayItem -> deleteNode(item)
                is RelDisplayItem -> deleteRel(item)
                is SchemaNode -> deleteSchemaNode(item)
                is SchemaRel -> deleteSchemaRel(item)
            }
        }
    }

    private suspend fun deleteSchemaNode(item: SchemaNode) {
        // TODO: Create an alert to ask if they want to delete a schema.
        //  Warn them if it deletes other schemas
        val q = "DROP TABLE ${item.label.withBackticks()}"
        val result = dbService.executeQuery(q)
        if (result is ExecutionResult.Success) {
            showSchema()
        }
    }

    private suspend fun deleteSchemaRel(item: SchemaRel) {
        val q = "DROP TABLE ${item.label.withBackticks()}"
        val result = dbService.executeQuery(q)
        if (result is ExecutionResult.Success) {
            showSchema()
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