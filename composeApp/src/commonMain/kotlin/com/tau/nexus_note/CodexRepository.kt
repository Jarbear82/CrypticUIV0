package com.tau.nexus_note

import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.EdgeCreationState
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.EdgeEditState
import com.tau.nexus_note.datamodels.EdgeSchemaCreationState
import com.tau.nexus_note.datamodels.EdgeSchemaEditState
import com.tau.nexus_note.datamodels.NodeCreationState
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.NodeEditState
import com.tau.nexus_note.datamodels.NodeSchemaCreationState
import com.tau.nexus_note.datamodels.NodeSchemaEditState
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.codex.schema.SchemaData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Centralizes all database logic and state for an open Codex.
 * This class owns the database connection and the primary data flows.
 * ViewModels will observe these flows and call methods on this repository
 * to perform actions.
 */
class CodexRepository(
    private val dbService: SqliteDbService,
    private val repositoryScope: CoroutineScope
) {

    // --- Central State Flows ---

    private val _schema = MutableStateFlow<SchemaData?>(null)
    val schema = _schema.asStateFlow()

    private val _nodeList = MutableStateFlow<List<NodeDisplayItem>>(emptyList())
    val nodeList = _nodeList.asStateFlow()

    private val _edgeList = MutableStateFlow<List<EdgeDisplayItem>>(emptyList())
    val edgeList = _edgeList.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // --- Public API ---

    fun refreshAll() {
        repositoryScope.launch {
            refreshSchema()
            refreshNodes()
            refreshEdges()
        }
    }

    fun refreshSchema() {
        repositoryScope.launch {
            try {
                val dbSchemas = dbService.database.appDatabaseQueries.selectAllSchemas().executeAsList()
                val nodeSchemas = mutableListOf<SchemaDefinitionItem>()
                val edgeSchemas = mutableListOf<SchemaDefinitionItem>()

                dbSchemas.forEach { dbSchema ->
                    val properties = try {
                        json.decodeFromString<List<SchemaProperty>>(dbSchema.properties_json)
                    } catch (e: Exception) { emptyList() }

                    if (dbSchema.type == "NODE") {
                        nodeSchemas.add(
                            SchemaDefinitionItem(dbSchema.id, dbSchema.type, dbSchema.name, properties, null)
                        )
                    } else if (dbSchema.type == "EDGE") {
                        val connections = try {
                            dbSchema.connections_json?.let { json.decodeFromString<List<ConnectionPair>>(it) } ?: emptyList()
                        } catch (e: Exception) { emptyList() }
                        edgeSchemas.add(
                            SchemaDefinitionItem(dbSchema.id, dbSchema.type, dbSchema.name, properties, connections)
                        )
                    }
                }
                _schema.value = SchemaData(nodeSchemas, edgeSchemas)
            } catch (e: Exception) {
                println("Error refreshing schema: ${e.message}")
                _schema.value = SchemaData(emptyList(), emptyList())
            }
        }
    }

    fun refreshNodes() {
        repositoryScope.launch {
            val schemaMap = _schema.value?.nodeSchemas?.associateBy { it.id } ?: run {
                if (_schema.value == null) refreshSchema() // Ensure schema is loaded
                _schema.value?.nodeSchemas?.associateBy { it.id } ?: emptyMap()
            }

            try {
                val dbNodes = dbService.database.appDatabaseQueries.selectAllNodes().executeAsList()
                _nodeList.value = dbNodes.mapNotNull { dbNode ->
                    val nodeSchema = schemaMap[dbNode.schema_id]
                    if (nodeSchema == null) {
                        println("Warning: Found node with unknown schema ID ${dbNode.schema_id}")
                        null
                    } else {
                        NodeDisplayItem(dbNode.id, nodeSchema.name, dbNode.display_label)
                    }
                }
            } catch (e: Exception) {
                println("Error refreshing nodes: ${e.message}")
                _nodeList.value = emptyList()
            }
        }
    }

    fun refreshEdges() {
        repositoryScope.launch {
            val schemaMap = _schema.value?.edgeSchemas?.associateBy { it.id } ?: run {
                if (_schema.value == null) refreshSchema()
                _schema.value?.edgeSchemas?.associateBy { it.id } ?: emptyMap()
            }

            val nodeMap = _nodeList.value.associateBy { it.id } ?: run {
                if(_nodeList.value.isEmpty()) refreshNodes()
                _nodeList.value.associateBy { it.id } ?: emptyMap()
            }

            try {
                val dbEdges = dbService.database.appDatabaseQueries.selectAllEdges().executeAsList()
                _edgeList.value = dbEdges.mapNotNull { dbEdge ->
                    val schema = schemaMap[dbEdge.schema_id]
                    val srcNode = nodeMap[dbEdge.from_node_id]
                    val dstNode = nodeMap[dbEdge.to_node_id]

                    if (schema == null || srcNode == null || dstNode == null) {
                        println("Warning: Skipping edge ${dbEdge.id} due to missing schema or node link.")
                        null
                    } else {
                        EdgeDisplayItem(dbEdge.id, schema.name, srcNode, dstNode)
                    }
                }
            } catch (e: Exception) {
                println("Error refreshing edges: ${e.message}")
                _edgeList.value = emptyList()
            }
        }
    }

    // --- Schema CRUD ---

    suspend fun getSchemaDependencyCount(schemaId: Long): Long {
        return try {
            val nodeCount = dbService.database.appDatabaseQueries.countNodesForSchema(schemaId).executeAsOne()
            val edgeCount = dbService.database.appDatabaseQueries.countEdgesForSchema(schemaId).executeAsOne()
            nodeCount + edgeCount
        } catch (e: Exception) {
            println("Error checking schema dependencies: ${e.message}")
            -1L // Indicate error
        }
    }

    fun deleteSchema(schemaId: Long) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.deleteSchemaById(schemaId)
                refreshAll() // Deleting schema cascades, refresh everything
            } catch (e: Exception) {
                println("Error deleting schema: ${e.message}")
            }
        }
    }

    fun createNodeSchema(state: NodeSchemaCreationState) {
        repositoryScope.launch {
            try {
                val propertiesJson = json.encodeToString(state.properties)
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "NODE",
                    name = state.tableName,
                    properties_json = propertiesJson,
                    connections_json = null
                )
                refreshSchema()
            } catch (e: Exception) {
                println("Error creating node schema: ${e.message}")
            }
        }
    }

    fun createEdgeSchema(state: EdgeSchemaCreationState) {
        repositoryScope.launch {
            try {
                val propertiesJson = json.encodeToString(state.properties)
                val connectionsJson = json.encodeToString(state.connections)
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "EDGE",
                    name = state.tableName,
                    properties_json = propertiesJson,
                    connections_json = connectionsJson
                )
                refreshSchema()
            } catch (e: Exception) {
                println("Error creating edge schema: ${e.message}")
            }
        }
    }

    fun updateNodeSchema(state: NodeSchemaEditState) {
        repositoryScope.launch {
            try {
                val propertiesJson = json.encodeToString(state.properties)
                dbService.database.appDatabaseQueries.updateSchema(
                    id = state.originalSchema.id,
                    name = state.currentName,
                    properties_json = propertiesJson,
                    connections_json = null
                )
                refreshSchema()
                refreshNodes()
            } catch (e: Exception) {
                println("Error updating node schema: ${e.message}")
            }
        }
    }

    fun updateEdgeSchema(state: EdgeSchemaEditState) {
        repositoryScope.launch {
            try {
                val propertiesJson = json.encodeToString(state.properties)
                val connectionsJson = json.encodeToString(state.connections)
                dbService.database.appDatabaseQueries.updateSchema(
                    id = state.originalSchema.id,
                    name = state.currentName,
                    properties_json = propertiesJson,
                    connections_json = connectionsJson
                )
                refreshSchema()
                refreshEdges()
            } catch (e: Exception) {
                println("Error updating edge schema: ${e.message}")
            }
        }
    }

    // --- Node CRUD ---

    fun createNode(state: NodeCreationState) {
        repositoryScope.launch {
            if (state.selectedSchema == null) return@launch
            try {
                val propertiesJson = json.encodeToString(state.properties)
                val displayKey = state.selectedSchema.properties.firstOrNull { it.isDisplayProperty }?.name
                val displayLabel = state.properties[displayKey] ?: "Node"

                dbService.database.appDatabaseQueries.insertNode(
                    schema_id = state.selectedSchema.id,
                    display_label = displayLabel,
                    properties_json = propertiesJson
                )
                refreshNodes() // Only refresh nodes
            } catch (e: Exception) {
                println("Error creating node: ${e.message}")
            }
        }
    }

    suspend fun getNodeEditState(itemId: Long): NodeEditState? {
        val dbNode = dbService.database.appDatabaseQueries.selectNodeById(itemId).executeAsOneOrNull() ?: return null
        val schema = _schema.value?.nodeSchemas?.firstOrNull { it.id == dbNode.schema_id } ?: return null
        val properties = try {
            json.decodeFromString<Map<String, String>>(dbNode.properties_json)
        } catch (e: Exception) {
            println("Error parsing node properties: ${e.message}")
            emptyMap()
        }
        return NodeEditState(id = dbNode.id, schema = schema, properties = properties)
    }

    fun updateNode(state: NodeEditState) {
        repositoryScope.launch {
            try {
                val propertiesJson = json.encodeToString(state.properties)
                val displayKey = state.schema.properties.firstOrNull { it.isDisplayProperty }?.name
                val displayLabel = state.properties[displayKey] ?: "Node ${state.id}"

                dbService.database.appDatabaseQueries.updateNodeProperties(
                    id = state.id,
                    display_label = displayLabel,
                    properties_json = propertiesJson
                )
                refreshNodes()
            } catch (e: Exception) {
                println("Error updating node: ${e.message}")
            }
        }
    }

    /**
     * Deletes a node and its cascading edges.
     * This implementation is optimized to perform an in-memory update
     * for the node list and only re-query the edge list.
     */
    fun deleteNode(itemId: Long) {
        repositoryScope.launch {
            try {
                // 1. Delete node from DB (this will cascade delete edges)
                dbService.database.appDatabaseQueries.deleteNodeById(itemId)

                // 2. Update node from list in-memory
                _nodeList.update { it.filterNot { node -> node.id == itemId } }

                // 3. Refresh only edges, which were affected by the cascade
                refreshEdges()
            } catch (e: Exception) {
                println("Error deleting node: ${e.message}")
            }
        }
    }

    // --- Edge CRUD ---

    fun createEdge(state: EdgeCreationState) {
        repositoryScope.launch {
            if (state.selectedSchema == null || state.src == null || state.dst == null) return@launch
            try {
                val propertiesJson = json.encodeToString(state.properties)
                dbService.database.appDatabaseQueries.insertEdge(
                    schema_id = state.selectedSchema.id,
                    from_node_id = state.src.id,
                    to_node_id = state.dst.id,
                    properties_json = propertiesJson
                )
                refreshEdges()
            } catch (e: Exception) {
                println("Error creating edge: ${e.message}")
            }
        }
    }

    suspend fun getEdgeEditState(item: EdgeDisplayItem): EdgeEditState? {
        val dbEdge = dbService.database.appDatabaseQueries.selectEdgeById(item.id).executeAsOneOrNull() ?: return null
        val schema = _schema.value?.edgeSchemas?.firstOrNull { it.id == dbEdge.schema_id } ?: return null
        val properties = try {
            json.decodeFromString<Map<String, String>>(dbEdge.properties_json)
        } catch (e: Exception) {
            println("Error parsing edge properties: ${e.message}")
            emptyMap()
        }
        return EdgeEditState(id = dbEdge.id, schema = schema, src = item.src, dst = item.dst, properties = properties)
    }

    fun updateEdge(state: EdgeEditState) {
        repositoryScope.launch {
            try {
                val propertiesJson = json.encodeToString(state.properties)
                dbService.database.appDatabaseQueries.updateEdgeProperties(
                    id = state.id,
                    properties_json = propertiesJson
                )
                refreshEdges()
            } catch (e: Exception) {
                println("Error updating edge: ${e.message}")
            }
        }
    }

    /**
     * Deletes an edge.
     * This implementation is optimized to perform an in-memory update
     * and avoid any subsequent database reads.
     */
    fun deleteEdge(itemId: Long) {
        repositoryScope.launch {
            try {
                // 1. Delete edge from DB
                dbService.database.appDatabaseQueries.deleteEdgeById(itemId)

                // 2. Update edge list in-memory.
                _edgeList.update { it.filterNot { edge -> edge.id == itemId } }

            } catch (e: Exception) {
                println("Error deleting edge: ${e.message}")
            }
        }
    }
}