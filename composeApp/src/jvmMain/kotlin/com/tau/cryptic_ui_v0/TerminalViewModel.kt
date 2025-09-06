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

    private val _schema = MutableStateFlow<String?>(null)
    val schema: StateFlow<String?> = _schema

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
            val schemaResult = dbService.getSchema()
            _schema.value = if (schemaResult != null) {
                formatSchema(schemaResult)
            } else {
                "Failed to retrieve schema."
            }
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

    private fun formatSchema(schema: Schema): String {
        val builder = StringBuilder()
        builder.append("Node Tables:\n")
        for (table in schema.nodeTables) {
            builder.append("  - ${table.name}\n")
            for (prop in table.properties) {
                builder.append("    - ${prop.first}: ${prop.second}\n")
            }
        }
        builder.append("\nRelationship Tables:\n")
        for (table in schema.relTables) {
            builder.append("  - ${table.name} (${table.src} -> ${table.dst})\n")
            for (prop in table.properties) {
                builder.append("    - ${prop.first}: ${prop.second}\n")
            }
        }
        return builder.toString()
    }

    fun onCleared() {
        dbService.close()
    }
}