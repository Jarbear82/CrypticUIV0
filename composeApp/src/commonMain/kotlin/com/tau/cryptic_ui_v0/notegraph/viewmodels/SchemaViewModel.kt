package com.tau.cryptic_ui_v0.viewmodels

import com.tau.cryptic_ui_v0.ConnectionPair
import com.tau.cryptic_ui_v0.SchemaDefinitionItem
import com.tau.cryptic_ui_v0.SchemaProperty
import com.tau.cryptic_ui_v0.SqliteDbService
import com.tau.cryptic_ui_v0.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class SchemaData(
    val nodeSchemas: List<SchemaDefinitionItem>,
    val edgeSchemas: List<SchemaDefinitionItem>
)

class SchemaViewModel(
    private val dbService: SqliteDbService,
    private val viewModelScope: CoroutineScope
) {
    private val _schema = MutableStateFlow<SchemaData?>(null)
    val schema = _schema.asStateFlow()

    // --- ADDED: State for managing the delete confirmation dialog ---
    private val _schemaToDelete = MutableStateFlow<SchemaDefinitionItem?>(null)
    val schemaToDelete = _schemaToDelete.asStateFlow()

    private val _schemaDependencyCount = MutableStateFlow(0L)
    val schemaDependencyCount = _schemaDependencyCount.asStateFlow()

    private var metadataViewModel: MetadataViewModel? = null

    // JSON parser configuration
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true // Helps handle nulls gracefully
    }

    init {
        viewModelScope.launch {
            showSchema()
        }
    }

    fun setDependencies(metadataViewModel: MetadataViewModel) {
        this.metadataViewModel = metadataViewModel
    }

    suspend fun showSchema() {
        println("\n\nShowing Schema...")
        try {
            val dbSchemas = dbService.database.appDatabaseQueries.selectAllSchemas().executeAsList()

            val nodeSchemas = mutableListOf<SchemaDefinitionItem>()
            val edgeSchemas = mutableListOf<SchemaDefinitionItem>()

            dbSchemas.forEach { dbSchema ->
                // Deserialize properties
                val properties = try {
                    json.decodeFromString<List<SchemaProperty>>(dbSchema.properties_json)
                } catch (e: Exception) {
                    println("Failed to parse properties for schema ${dbSchema.name}: ${e.message}")
                    emptyList<SchemaProperty>()
                }

                if (dbSchema.type == "NODE") {
                    nodeSchemas.add(
                        SchemaDefinitionItem(
                            id = dbSchema.id,
                            type = dbSchema.type,
                            name = dbSchema.name,
                            properties = properties,
                            connections = null
                        )
                    )
                } else if (dbSchema.type == "EDGE") {
                    // Deserialize connections for edges
                    val connections = try {
                        dbSchema.connections_json?.let {
                            json.decodeFromString<List<ConnectionPair>>(it)
                        } ?: emptyList()
                    } catch (e: Exception) {
                        println("Failed to parse connections for schema ${dbSchema.name}: ${e.message}")
                        emptyList<ConnectionPair>()
                    }

                    edgeSchemas.add(
                        SchemaDefinitionItem(
                            id = dbSchema.id,
                            type = dbSchema.type,
                            name = dbSchema.name,
                            properties = properties,
                            connections = connections
                        )
                    )
                }
            }
            _schema.value = SchemaData(nodeSchemas, edgeSchemas)
        } catch (e: Exception) {
            println("Error fetching schema: ${e.message}")
            _schema.value = SchemaData(emptyList(), emptyList()) // Set to empty on error
        }
    }

    /**
     * Checks for dependencies and shows a confirmation dialog if necessary.
     */
    fun requestDeleteSchema(item: SchemaDefinitionItem) {
        viewModelScope.launch {
            try {
                // Use new queries to check for dependencies
                val nodeCount = dbService.database.appDatabaseQueries.countNodesForSchema(item.id).executeAsOne()
                val edgeCount = dbService.database.appDatabaseQueries.countEdgesForSchema(item.id).executeAsOne()
                val totalCount = nodeCount + edgeCount

                if (totalCount == 0L) {
                    // No dependencies, delete immediately
                    // UPDATED: Need to set the item to delete even if count is 0
                    _schemaToDelete.value = item
                    confirmDeleteSchema()
                } else {
                    // Dependencies found, show dialog
                    _schemaDependencyCount.value = totalCount
                    _schemaToDelete.value = item
                }
            } catch (e: Exception) {
                println("Error checking schema dependencies: ${e.message}")
                // TODO: Show user-facing error
            }
        }
    }

    /**
     * Called by the dialog's "Confirm" button.
     * Deletes the schema, relying on "ON DELETE CASCADE" to clean up nodes/edges.
     */
    fun confirmDeleteSchema() {
        viewModelScope.launch {
            val item = _schemaToDelete.value ?: return@launch // Get item from state
            try {
                // This single query deletes the schema.
                // The DB's "ON DELETE CASCADE" will delete all associated nodes/edges.
                dbService.database.appDatabaseQueries.deleteSchemaById(item.id)

                // Refresh all data
                showSchema()
                metadataViewModel?.listAll()

            } catch (e: Exception) {
                println("Error deleting schema ${item.name}: ${e.message}")
                // TODO: Show error to user
            } finally {
                clearDeleteSchemaRequest()
            }
        }
    }

    /**
     * Called by the dialog's "Cancel" button.
     */
    fun clearDeleteSchemaRequest() {
        _schemaToDelete.value = null
        _schemaDependencyCount.value = 0
    }
}

