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

// Helper data class to hold the schema state, replacing the old 'Schema' class
data class SchemaData(
    val nodeSchemas: List<SchemaDefinitionItem>,
    val edgeSchemas: List<SchemaDefinitionItem>
)

// UPDATED: Constructor takes SqliteDbService
class SchemaViewModel(private val dbService: SqliteDbService, private val viewModelScope: CoroutineScope) {
    // UPDATED: StateFlow holds the new SchemaData class
    private val _schema = MutableStateFlow<SchemaData?>(null)
    val schema = _schema.asStateFlow()

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

    // UPDATED: Rewritten to use SqliteDbService and deserialize JSON
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

    // UPDATED: Takes new SchemaDefinitionItem and uses new delete query
    fun deleteSchema(item: SchemaDefinitionItem) {
        viewModelScope.launch {
            try {
                // This single query deletes by the unique ID, regardless of type
                dbService.database.appDatabaseQueries.deleteSchemaById(item.id)
                showSchema() // Refresh the schema list
            } catch (e: Exception) {
                println("Error deleting schema ${item.name}: ${e.message}")
                // TODO: Show error to user
            }
        }
    }

    // REMOVED: Old Kuzu-specific functions
    /*
    fun deleteSchemaNode(item: SchemaNode) {
        viewModelScope.launch {
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
    */
}
