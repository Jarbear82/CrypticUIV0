package com.tau.nexus_note.codex.schema

import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SchemaData(
    val nodeSchemas: List<SchemaDefinitionItem>,
    val edgeSchemas: List<SchemaDefinitionItem>
)

class SchemaViewModel(
    private val repository: CodexRepository,
    private val viewModelScope: CoroutineScope
) {
    // --- State is now observed from the repository ---
    val schema = repository.schema

    // --- State for managing the delete confirmation dialog (This is UI state) ---
    private val _schemaToDelete = MutableStateFlow<SchemaDefinitionItem?>(null)
    val schemaToDelete = _schemaToDelete.asStateFlow()

    private val _schemaDependencyCount = MutableStateFlow(0L)
    val schemaDependencyCount = _schemaDependencyCount.asStateFlow()

    fun showSchema() {
        // Pass-through to repository
        repository.refreshSchema()
    }

    fun requestDeleteSchema(item: SchemaDefinitionItem) {
        viewModelScope.launch {
            val totalCount = repository.getSchemaDependencyCount(item.id)

            if (totalCount == 0L) {
                // No dependencies, delete immediately
                repository.deleteSchema(item.id)
                clearDeleteSchemaRequest() // Clear state just in case
            } else if (totalCount > 0L) {
                // Dependencies found, show dialog
                _schemaDependencyCount.value = totalCount
                _schemaToDelete.value = item
            }
            // else (count == -1L) an error occurred, do nothing
        }
    }

    fun confirmDeleteSchema() {
        viewModelScope.launch {
            val item = _schemaToDelete.value ?: return@launch
            repository.deleteSchema(item.id)
            clearDeleteSchemaRequest()
        }
    }

    fun clearDeleteSchemaRequest() {
        _schemaToDelete.value = null
        _schemaDependencyCount.value = 0
    }
}