package com.tau.cryptic_ui_v0

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
            if (result is ExecutionResult.Success && result.isSchemaChanged) {
                showSchema()
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
            val q = "MATCH (n) WHERE n._id = '${item.id}' RETURN n"
            val result = dbService.executeQuery(q)
            if (result is ExecutionResult.Success) {
                result.results.firstOrNull()?.rows?.firstOrNull()?.let { row ->
                    val properties = row[0] as? Map<String, Any?>
                    _selectedItem.value = item.copy(properties = properties ?: emptyMap())
                }
            }
        }
    }

    fun clearSelectedItem() {
        _selectedItem.value = null
    }

    fun onCleared() {
        dbService.close()
    }
}