package com.tau.cryptic_ui_v0

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val _relationshipList = MutableStateFlow<List<DisplayItem>>(emptyList())
    val relationshipList: StateFlow<List<DisplayItem>> = _relationshipList

    private val _selectedItem = MutableStateFlow<DisplayItem?>(null)
    val selectedItem: StateFlow<DisplayItem?> = _selectedItem

    val query = mutableStateOf("")

    init {
        viewModelScope.launch {
            dbService.initialize()
            _dbMetaData.value = dbService.getDBMetaData()
            showSchema()
        }
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
                val newRels = mutableMapOf<String, DisplayItem>()

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
                                    val pkName = dbService.getPrimaryKey(label) ?: "_id"
                                    val pkValue = properties[pkName]?.toString() ?: id
                                    newRels[id] = DisplayItem(id = id, label = label, primaryKey = pkValue, properties = properties)
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

    fun showSchema() {
        viewModelScope.launch {
            _schema.value = dbService.getSchema()
        }
    }

    fun listNodes() {
        viewModelScope.launch {
            val nodes = mutableListOf<DisplayItem>()
            _schema.value?.nodeTables?.forEach { table ->
                val pk = dbService.getPrimaryKey(table.name) ?: "_id"
                val q = "MATCH (n:${table.name}) RETURN n._id, n.$pk"
                val result = dbService.executeQuery(q)
                if (result is ExecutionResult.Success) {
                    result.results.firstOrNull()?.rows?.forEach { row ->
                        nodes.add(DisplayItem(id = row[0].toString(), label = table.name, primaryKey = row[1].toString()))
                    }
                }
            }
            _nodeList.value = nodes
        }
    }

    fun listEdges() {
        viewModelScope.launch {
            val rels = mutableListOf<DisplayItem>()
            _schema.value?.relTables?.forEach { table ->
                val pk = dbService.getPrimaryKey(table.name) ?: "_id"
                val q = "MATCH ()-[r:${table.name}]->() RETURN r._id, r.$pk"
                val result = dbService.executeQuery(q)
                if (result is ExecutionResult.Success) {
                    result.results.firstOrNull()?.rows?.forEach { row ->
                        rels.add(DisplayItem(id = row[0].toString(), label = table.name, primaryKey = row[1].toString()))
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

    fun selectItem(item: DisplayItem) {
        viewModelScope.launch {
            val pk = dbService.getPrimaryKey(item.label) ?: "_id"
            val q = "MATCH (n:${item.label}) WHERE n.$pk = '${item.primaryKey}' RETURN n"
            val result = dbService.executeQuery(q)
            if (result is ExecutionResult.Success) {
                result.results.firstOrNull()?.rows?.firstOrNull()?.let { row ->
                    val properties = row[0] as? Map<String, Any?>
                    _selectedItem.value = item.copy(properties = properties ?: emptyMap())
                }
            }
        }
    }

    fun deleteItem(item: DisplayItem) {
        viewModelScope.launch {
            // 1. Get Schema for label
            val isNode = _schema.value?.nodeTables?.any { it.name == item.label } == true
            if (isNode) {
                deleteNode(item)
            } else {
                deleteRel(item)
            }
        }
    }

    private suspend fun deleteNode(item: DisplayItem) {
        // 2. Generate Query
        val pk = dbService.getPrimaryKey(item.label) ?: return
        val q = "MATCH (n:${item.label}) WHERE n.$pk = '${item.primaryKey}' DETACH DELETE n"
        val result = dbService.executeQuery(q)
        if (result is ExecutionResult.Success) {
            // 3. Update Node list
            _nodeList.update { list -> list.filterNot { it.id == item.id } }
        }
    }

    private suspend fun deleteRel(item: DisplayItem) {
        // 2. Generate Query
        val pk = dbService.getPrimaryKey(item.label) ?: return
        val q = "MATCH ()-[r:${item.label}]->() WHERE r.$pk = '${item.primaryKey}' DELETE r"
        val result = dbService.executeQuery(q)
        if (result is ExecutionResult.Success) {
            // 3. Update Rel list
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