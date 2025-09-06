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

    private val _queryResult = MutableStateFlow<String>("")
    val queryResult: StateFlow<String> = _queryResult

    val query = mutableStateOf("")

    init {
        viewModelScope.launch {
            dbService.initialize()
        }
    }

    fun executeQuery() {
        viewModelScope.launch {
            val result = dbService.executeQuery(query.value)
            _queryResult.value = formatExecutionResult(result)
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
        query.value = "MATCH (n)-[r]->(m) RETURN n, r, m;"
        executeQuery()
    }


    private fun formatExecutionResult(result: ExecutionResult): String {
        return when (result) {
            is ExecutionResult.Success -> {
                if (result.results.isEmpty()) {
                    "Query executed successfully, but returned no data."
                } else {
                    result.results.joinToString("\n\n") { formattedResult ->
                        formatResultAsTable(formattedResult)
                    }
                }
            }
            is ExecutionResult.Error -> "Error: ${result.message}"
        }
    }

     fun onCleared() {
        dbService.close()
    }
}