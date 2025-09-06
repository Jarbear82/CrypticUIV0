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
        query.value = "MATCH ()-[r]->(), (n) RETURN n, r;"
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

    private fun formatResultAsTable(result: FormattedResult): String {
        val headers = result.headers
        val rows = result.rows
        if (headers.isEmpty() || rows.isEmpty()) {
            return "No results to display."
        }

        val columnWidths = mutableMapOf<Int, Int>()
        for (i in headers.indices) {
            columnWidths[i] = headers[i].length
        }

        for (row in rows) {
            for (i in row.indices) {
                val cellLength = row[i]?.toString()?.length ?: 4
                if (cellLength > (columnWidths[i] ?: 0)) {
                    columnWidths[i] = cellLength
                }
            }
        }

        val builder = StringBuilder()
        // Header
        for (i in headers.indices) {
            builder.append(headers[i].padEnd(columnWidths[i] ?: 0))
            builder.append(" | ")
        }
        builder.append("\n")
        // Separator
        for (i in headers.indices) {
            builder.append("-".repeat(columnWidths[i] ?: 0))
            builder.append(" | ")
        }
        builder.append("\n")

        // Rows
        for (row in rows) {
            for (i in row.indices) {
                builder.append((row.getOrNull(i)?.toString() ?: "null").padEnd(columnWidths[i] ?: 0))
                builder.append(" | ")
            }
            builder.append("\n")
        }

        return builder.toString()
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