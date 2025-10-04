package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SchemaViewModel(private val repository: KuzuRepository, private val viewModelScope: CoroutineScope) {
    private val _schema = MutableStateFlow<Schema?>(null)
    val schema = _schema.asStateFlow()

    private val _nodeSchemaCreationState = MutableStateFlow(NodeSchemaCreationState())
    val nodeSchemaCreationState = _nodeSchemaCreationState.asStateFlow()

    private val _relSchemaCreationState = MutableStateFlow(RelSchemaCreationState())
    val relSchemaCreationState = _relSchemaCreationState.asStateFlow()


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
        val relSchemaList = mutableListOf<SchemaRel>()

        // Call show tables
        val execResult = repository.executeQuery("CALL SHOW_TABLES() RETURN *;")
        if (execResult !is ExecutionResult.Success) {
            println("Failed to fetch schema")
            // Handle error case
            return Schema(nodeSchemaList, relSchemaList)
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
                            // We can assume `isPrimaryKey` is always false for relationship properties.
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
                            // Create Rel Schema and add to Rel Schema List
                            relSchemaList.add(SchemaRel(tableName, srcLabel, dstLabel, properties))
                        }
                    }
                }
                "RECURSIVE_REL" -> {
                    // Recursive relationships are internally represented as STRUCT{LIST [NODE], LIST [REL]},
                    // but for schema representation, they can be treated as RELs for now.
                    // Call Show Connection
                    val showConnectionResult = repository.executeQuery("CALL SHOW_CONNECTION(\"$tableName\") RETURN *;")
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
        return Schema(nodeTables = nodeSchemaList, relTables = relSchemaList)
    }

    fun onNodeSchemaTableNameChange(name: String) {
        _nodeSchemaCreationState.update { it.copy(tableName = name) }
    }

    fun onNodeSchemaPropertyChange(index: Int, property: Property) {
        _nodeSchemaCreationState.update {
            val newProperties = it.properties.toMutableList()
            newProperties[index] = property
            it.copy(properties = newProperties)
        }
    }

    fun onAddNodeSchemaProperty() {
        _nodeSchemaCreationState.update {
            it.copy(properties = it.properties + Property())
        }
    }

    fun onRemoveNodeSchemaProperty(index: Int) {
        _nodeSchemaCreationState.update {
            val newProperties = it.properties.toMutableList()
            newProperties.removeAt(index)
            it.copy(properties = newProperties)
        }
    }

    fun onRelSchemaTableNameChange(name: String) {
        _relSchemaCreationState.update { it.copy(tableName = name) }
    }

    fun onRelSchemaSrcTableChange(table: String) {
        _relSchemaCreationState.update { it.copy(srcTable = table) }
    }

    fun onRelSchemaDstTableChange(table: String) {
        _relSchemaCreationState.update { it.copy(dstTable = table) }
    }

    fun onRelSchemaPropertyChange(index: Int, property: Property) {
        _relSchemaCreationState.update {
            val newProperties = it.properties.toMutableList()
            newProperties[index] = property
            it.copy(properties = newProperties)
        }
    }

    fun onAddRelSchemaProperty() {
        _relSchemaCreationState.update {
            it.copy(properties = it.properties + Property(isPrimaryKey = false))
        }
    }

    fun onRemoveRelSchemaProperty(index: Int) {
        _relSchemaCreationState.update {
            val newProperties = it.properties.toMutableList()
            newProperties.removeAt(index)
            it.copy(properties = newProperties)
        }
    }

    fun createNodeSchemaFromState(state: NodeSchemaCreationState, onFinished: () -> Unit) {
        viewModelScope.launch {
            val pk = state.properties.first { it.isPrimaryKey }
            val properties = state.properties.joinToString(", ") {
                "${it.name.withBackticks()} ${it.type}"
            }
            val q = "CREATE NODE TABLE ${state.tableName.withBackticks()} ($properties, PRIMARY KEY (${pk.name.withBackticks()}))"
            repository.executeQuery(q)
            onFinished()
            showSchema()
        }
    }

    fun createRelSchemaFromState(state: RelSchemaCreationState, onFinished: () -> Unit) {
        viewModelScope.launch {
            val properties = if (state.properties.isNotEmpty()) {
                ", " + state.properties.joinToString(", ") {
                    "${it.name.withBackticks()} ${it.type}"
                }
            } else {
                ""
            }
            val q = "CREATE REL TABLE ${state.tableName.withBackticks()} (FROM ${state.srcTable!!.withBackticks()} TO ${state.dstTable!!.withBackticks()}$properties)"
            repository.executeQuery(q)
            onFinished()
            showSchema()
        }
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

    fun deleteSchemaRel(item: SchemaRel) {
        viewModelScope.launch {
            val q = "DROP TABLE ${item.label.withBackticks()}"
            val result = repository.executeQuery(q)
            if (result is ExecutionResult.Success) {
                showSchema()
            }
        }
    }
}