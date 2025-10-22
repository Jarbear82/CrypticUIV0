package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SchemaViewModel(private val dbService: KuzuDBService, private val viewModelScope: CoroutineScope) {
    private val _schema = MutableStateFlow<Schema?>(null)
    val schema = _schema.asStateFlow()

    init {
        viewModelScope.launch {
            showSchema()
        }
    }
    suspend fun showSchema() {
        println("\n\nShowing Schema...")
        _schema.value = getSchema(dbService)
    }

    fun deleteSchemaNode(item: SchemaNode) {
        viewModelScope.launch {
            // TODO: Create an alert to ask if they want to delete a schema.
            //  Warn them if it deletes other schemas
            deleteSchemaNode(dbService, item)
            showSchema()
        }
    }

    fun deleteSchemaEdge(item: SchemaEdge) {
        viewModelScope.launch {
            deleteSchemaEdge(dbService, item)
            showSchema()
        }
    }
}