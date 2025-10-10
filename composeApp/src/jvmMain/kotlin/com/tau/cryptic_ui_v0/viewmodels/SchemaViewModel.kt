package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SchemaViewModel(private val repository: KuzuRepository, private val viewModelScope: CoroutineScope) {
    private val _schema = MutableStateFlow<Schema?>(null)
    val schema = _schema.asStateFlow()

    init {
        viewModelScope.launch {
            showSchema()
        }
    }
    suspend fun showSchema() {
        println("\n\nShowing Schema...")
        _schema.value = getSchema()
    }

    private fun String.withBackticks(): String {
        return if (reservedWords.contains(this.uppercase())) "`$this`" else this
    }

    private suspend fun getSchema() : Schema {
        val nodeSchemaList = mutableListOf<SchemaNode>()
        val edgeSchemaList = mutableListOf<SchemaEdge>()

        // Call show tables
        val execResult = repository.executeQuery("CALL SHOW_TABLES() RETURN *;")
        if (execResult !is ExecutionResult.Success) {
            println("Failed to fetch schema")
            // Handle error case
            return Schema(nodeSchemaList, edgeSchemaList)
        }


        val allTables = execResult.results.first().rows.map { it[1] as String to it[2] as String }

        // List Schema Tables
        for ((tableName, tableType) in allTables) {
            // For each table, create schema
            when (tableType) {
                "NODE" -> {
                    // FOR NODE
                    val tableInfoResult = repository.executeQuery("CALL TABLE_INFO(\"$tableName\") RETURN *;")
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
                    val tableInfoResult = repository.executeQuery("CALL TABLE_INFO(\"$tableName\") RETURN *;")
                    val properties = if (tableInfoResult is ExecutionResult.Success) {
                        // Add Properties (if any)
                        tableInfoResult.results.first().rows.map { row ->
                            val propName = row[1].toString()
                            val propType = row[2].toString()
                            // For REL tables, the 5th column is `storage_direction`, not a boolean, so we can't cast it.
                            // We can assume `isPrimaryKey` is always false for edge properties.
                            SchemaProperty(propName, propType, isPrimaryKey = false)
                        }
                    } else {
                        emptyList()
                    }

                    // Call Show Connection
                    val showConnectionResult = repository.executeQuery("CALL SHOW_CONNECTION(\"$tableName\") RETURN *;")
                    if (showConnectionResult is ExecutionResult.Success) {
                        // Add TO and FROM (src and dst)
                        val connectionRows = showConnectionResult.results.first().rows
                        for (row in connectionRows) {
                            val srcLabel = row[0] as String
                            val dstLabel = row[1] as String
                            // Create Edge Schema and add to Edge Schema List
                            edgeSchemaList.add(SchemaEdge(tableName, srcLabel, dstLabel, properties))
                        }
                    }
                }
                "RECURSIVE_REL" -> {
                    // Recursive edges are internally represented as STRUCT{LIST [NODE], LIST [REL]},
                    // but for schema representation, they can be treated as Edges for now.
                    // Call Show Connection
                    val showConnectionResult = repository.executeQuery("CALL SHOW_CONNECTION(\"$tableName\") RETURN *;")
                    if (showConnectionResult is ExecutionResult.Success) {
                        val connectionRows = showConnectionResult.results.first().rows
                        for (row in connectionRows) {
                            val srcLabel = row[0] as String
                            val dstLabel = row[1] as String
                            // Create Edge Schema and add to Edge Schema List
                            // Note: Recursive edges do not have properties according to the provided context.
                            // You can add logic to get properties if they become available in the future.
                            edgeSchemaList.add(SchemaEdge(tableName, srcLabel, dstLabel, emptyList()))
                        }
                    }
                }
            }
        }
        // Create Schema containing Node and Edge tables
        return Schema(nodeTables = nodeSchemaList, edgeTables = edgeSchemaList)
    }

    fun deleteSchemaNode(item: SchemaNode) {
        viewModelScope.launch {
            // TODO: Create an alert to ask if they want to delete a schema.
            //  Warn them if it deletes other schemas
            val q = "DROP TABLE ${item.label.withBackticks()}"
            val result = repository.executeQuery(q)
            if (result is ExecutionResult.Success) {
                showSchema()
            }
        }
    }

    fun deleteSchemaEdge(item: SchemaEdge) {
        viewModelScope.launch {
            val q = "DROP TABLE ${item.label.withBackticks()}"
            val result = repository.executeQuery(q)
            if (result is ExecutionResult.Success) {
                showSchema()
            }
        }
    }
}