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

// List of reserved workds in kuzu:
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

    private val _nodeList = MutableStateFlow<List<DisplayItem>>(emptyList())
    val nodeList: StateFlow<List<DisplayItem>> = _nodeList

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

    fun executeQuery() {
        viewModelScope.launch {
            val result = dbService.executeQuery(query.value)
            _queryResult.value = result
            if (result is ExecutionResult.Success) {
                if (result.isSchemaChanged) {
                    showSchema()
                }

                val newNodes = mutableMapOf<String, DisplayItem>()
                val newRels = mutableMapOf<String, RelDisplayItem>()

                suspend fun processValue(value: Any?) {
                    when (value) {
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            val properties = value as Map<String, Any?>
                            if (properties.containsKey("_nodes") && properties.containsKey("_rels")) { // RECURSIVE_REL
                                (properties["_nodes"] as? List<*>)?.let { nodes ->
                                    for (node in nodes) {
                                        processValue(node)
                                    }
                                }
                                (properties["_rels"] as? List<*>)?.let { rels ->
                                    for (rel in rels) {
                                        processValue(rel)
                                    }
                                }
                            } else if (properties.containsKey("_src") && properties.containsKey("_dst")) { // REL
                                val id = properties["_id"]?.toString()
                                val label = properties["_label"]?.toString()
                                if (id != null && label != null) {
                                    val srcId = properties["_src"].toString()
                                    val dstId = properties["_dst"].toString()
                                    val relSchema = _schema.value?.relTables?.find { it.name == label }
                                    if (relSchema != null) {
                                        newRels[id] = RelDisplayItem(
                                            id = id,
                                            label = label,
                                            src = srcId,
                                            dst = dstId,
                                            srcLabel = relSchema.src,
                                            dstLabel = relSchema.dst,
                                            properties = properties
                                        )
                                    }
                                }
                            } else if (properties.containsKey("_label")) { // NODE
                                val id = properties["_id"]?.toString()
                                val label = properties["_label"]?.toString()
                                if (id != null && label != null) {
                                    val pkName = dbService.getPrimaryKey(label) ?: "_id"
                                    val pkValue = properties[pkName]?.toString() ?: id
                                    newNodes[id] = DisplayItem(id = id, label = label, primaryKey = pkValue, properties = properties)
                                }
                            }
                        }
                        is List<*> -> {
                            for (item in value) {
                                processValue(item)
                            }
                        }
                    }
                }

                for (formattedResult in result.results) {
                    for (row in formattedResult.rows) {
                        for (cell in row) {
                            processValue(cell)
                        }
                    }
                }

                if (newNodes.isNotEmpty()) {
                    val allNodes = _nodeList.value + newNodes.values
                    _nodeList.value = allNodes.distinctBy { it.id }
                }

                if (newRels.isNotEmpty()) {
                    val allRels = _relationshipList.value + newRels.values
                    _relationshipList.value = allRels.distinctBy { it.id }
                }
            }
        }
    }

    suspend fun showSchema() {
        _schema.value = dbService.getSchema()
    }

    fun listNodes() {
        viewModelScope.launch {
            val nodes = mutableListOf<DisplayItem>()
            _schema.value?.nodeTables?.forEach { table ->
                val pk = dbService.getPrimaryKey(table.name) ?: return@forEach
                // Corrected the query to use the id() function for nodes
                val q = "MATCH (n:${table.name.withBackticks()}) RETURN id(n), n.${pk.withBackticks()}"
                val result = dbService.executeQuery(q)
                if (result is ExecutionResult.Success) {
                    result.results.firstOrNull()?.rows?.forEach { row ->
                        // Assuming row[0] is the internal ID and row[1] is the primary key value
                        nodes.add(DisplayItem(id = row[0].toString(), label = table.name, primaryKey = row[1].toString()))
                    }
                }
            }
            _nodeList.value = nodes
        }
    }

    fun listEdges() {
        viewModelScope.launch {
            val rels = mutableListOf<RelDisplayItem>()
            _schema.value?.relTables?.forEach { table ->
                val q = "MATCH (src)-[r:${table.name.withBackticks()}]->(dst) RETURN src, r, dst, id(r)"
                val result = dbService.executeQuery(q)
                if (result is ExecutionResult.Success) {
                    result.results.firstOrNull()?.rows?.forEach { row ->
                        val srcNode = row[0] as Map<String, Any?>
                        val rel = row[1] as Map<String, Any?>
                        val dstNode = row[2] as Map<String, Any?>
                        rels.add(
                            RelDisplayItem(
                                id = row[3].toString(),
                                label = rel["_label"].toString(),
                                src = srcNode["_id"].toString(),
                                dst = dstNode["_id"].toString(),
                                srcLabel = srcNode["_label"].toString(),
                                dstLabel = dstNode["_label"].toString(),
                                properties = rel
                            )
                        )
                    }
                }
            }
            _relationshipList.value = rels
        }
    }

    fun listAll() {
        listNodes()
        listEdges()
    }

    fun clearQueryResult() {
        _queryResult.value = null
    }

    fun selectItem(item: Any) {
        viewModelScope.launch {
            when (item) {
                is DisplayItem -> {
                    val pk = dbService.getPrimaryKey(item.label) ?: return@launch
                    val q = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pk.withBackticks()} = '${item.primaryKey}' RETURN n"
                    val result = dbService.executeQuery(q)
                    if (result is ExecutionResult.Success) {
                        result.results.firstOrNull()?.rows?.firstOrNull()?.let { row ->
                            val properties = row[0] as? Map<String, Any?>
                            _selectedItem.value = item.copy(properties = properties ?: emptyMap())
                        }
                    }
                }
                is RelDisplayItem -> {
                    val q = "MATCH ()-[r:${item.label.withBackticks()}]->() WHERE r._id = '${item.id}' RETURN r"
                    val result = dbService.executeQuery(q)
                    if (result is ExecutionResult.Success) {
                        result.results.firstOrNull()?.rows?.firstOrNull()?.let { row ->
                            val properties = row[0] as? Map<String, Any?>
                            _selectedItem.value = item.copy(properties = properties ?: emptyMap())
                        }
                    }
                }
            }
        }
    }

    fun deleteItem(item: Any) {
        viewModelScope.launch {
            when (item) {
                is DisplayItem -> deleteNode(item)
                is RelDisplayItem -> deleteRel(item)
            }
        }
    }

    private suspend fun deleteNode(item: DisplayItem) {
        val pk = dbService.getPrimaryKey(item.label) ?: return
        val q = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pk.withBackticks()} = '${item.primaryKey}' DETACH DELETE n"
        val result = dbService.executeQuery(q)
        if (result is ExecutionResult.Success) {
            _nodeList.update { list -> list.filterNot { it.id == item.id } }
        }
    }

    private suspend fun deleteRel(item: RelDisplayItem) {
        val q = "MATCH ()-[r:${item.label.withBackticks()}]->() WHERE r._id = '${item.id}' DELETE r"
        val result = dbService.executeQuery(q)
        if (result is ExecutionResult.Success) {
            _relationshipList.update { list -> list.filterNot { it.id == item.id } }
        }
    }

    fun clearSelectedItem() {
        _selectedItem.value = null
    }

    fun onCleared() {
        dbService.close()
    }
}