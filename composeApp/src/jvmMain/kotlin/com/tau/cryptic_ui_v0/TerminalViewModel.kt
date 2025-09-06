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

    val query = mutableStateOf("")

    init {
        viewModelScope.launch {
            dbService.initialize()
            _dbMetaData.value = dbService.getDBMetaData()
        }
    }

    fun executeQuery() {
        viewModelScope.launch {
            _queryResult.value = dbService.executeQuery(query.value)
        }
    }

    fun showSchema() {
        viewModelScope.launch {
            _schema.value = dbService.getSchema()
        }
    }

    fun listNodes() {
        query.value = "MATCH (n) RETURN n;"
        executeQuery()
    }

    fun listEdges() {
        query.value = "MATCH ()-[r]->() RETURN r;"
        executeQuery()
    }

    fun listAll() {
        query.value = "MATCH ()-[r]->(), (n) RETURN n, r;"
        executeQuery()
    }

    fun onCleared() {
        dbService.close()
    }
}