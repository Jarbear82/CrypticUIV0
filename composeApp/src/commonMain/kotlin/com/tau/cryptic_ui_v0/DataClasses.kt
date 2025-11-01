package com.tau.cryptic_ui_v0

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Represents an on-disk notegraph database.
 * @param name The display name (the filename, e.g., "my_db.sqlite").
 * @param path The absolute path to the database file.
 */
data class NoteGraphItem(
    val name: String,
    val path: String
)

/**
 * Represents a Node in the UI lists and graph.
 * @param id The unique ID from the SQLite 'Node' table.
 * @param label The name of the schema (e.g., "Person", "Note").
 * @param displayProperty The user-friendly text to show (e.g., a person's name, a note's title).
 */
data class NodeDisplayItem(
    val id: Long,
    val label: String,
    val displayProperty: String
)

/**
 * Represents an Edge in the UI lists and graph.
 * @param id The unique ID from the SQLite 'Edge' table.
 * @param label The name of the schema (e.g., "KNOWS", "REFERENCES").
 * @param src The source Node.
 * @param dst The destination Node.
 */
data class EdgeDisplayItem(
    val id: Long,
    val label: String,
    val src: NodeDisplayItem,
    val dst: NodeDisplayItem
)

/**
 * Represents a user-defined property within a schema.
 * This is serialized to/from JSON.
 * @param name The name of the property (e.g., "Description", "Due Date").
 * @param type The data type for the UI (e.g., "Text", "Image", "Date").
 */
@Serializable
data class SchemaProperty(
    val name: String,
    val type: String,
    val isDisplayProperty: Boolean = false // New: Marks this as the one to show in lists
)

/**
 * Represents a connection pair for an edge schema.
 * @param src The name of the source node schema (e.t., "Person").
 * @param dst The name of the destination node schema (e.g., "Location").
 */
@Serializable // Also serializable for storing in SchemaDefinition properties
data class ConnectionPair(
    val src: String,
    val dst: String
)

/**
 * UI-facing model for a schema definition (either Node or Edge).
 * This is built from the 'SchemaDefinition' table.
 * @param id The unique ID from the 'SchemaDefinition' table.
 * @param type "NODE" or "EDGE".
 * @param name The name of the schema (e.g., "Person", "KNOWS").
 * @param properties The list of user-defined properties.
 * @param connections For EDGE schemas, the list of allowed connections.
 */
data class SchemaDefinitionItem(
    val id: Long,
    val type: String,
    val name: String,
    val properties: List<SchemaProperty>,
    val connections: List<ConnectionPair>? = null
)

// --- Data class for Database Metadata ---
data class DBMetaData(
    val name: String,
    val version: String,
    val storage: String
)

// --- Data class for Node Creation UI State ---
data class NodeCreationState(
    val schemas: List<SchemaDefinitionItem>, // All available NODE schemas
    val selectedSchema: SchemaDefinitionItem? = null,
    val properties: Map<String, String> = emptyMap() // UI state for text fields
)

// --- Data class for Edge Creation UI State ---
data class EdgeCreationState(
    val schemas: List<SchemaDefinitionItem>, // All available EDGE schemas
    val availableNodes: List<NodeDisplayItem>,
    val selectedSchema: SchemaDefinitionItem? = null,
    val selectedConnection: ConnectionPair? = null,
    val src: NodeDisplayItem? = null,
    val dst: NodeDisplayItem? = null,
    val properties: Map<String, String> = emptyMap()
)

// --- Data class for Node Schema Creation UI State ---
data class NodeSchemaCreationState(
    val tableName: String = "",
    val properties: List<SchemaProperty> = listOf(SchemaProperty("name", "Text", isDisplayProperty = true))
)

// --- Data class for Edge Schema Creation UI State ---
data class EdgeSchemaCreationState(
    val tableName: String = "",
    val connections: List<ConnectionPair> = emptyList(),
    val properties: List<SchemaProperty> = emptyList(),
    val allNodeSchemas: List<SchemaDefinitionItem> = emptyList() // All NODE schemas
)

// --- Data classes for Editing Instances ---

data class NodeEditState(
    val id: Long,
    val schema: SchemaDefinitionItem,
    val properties: Map<String, String> // Current values from DB, as strings for UI
)

data class EdgeEditState(
    val id: Long,
    val schema: SchemaDefinitionItem,
    val src: NodeDisplayItem,
    val dst: NodeDisplayItem,
    val properties: Map<String, String> // Current values from DB, as strings for UI
)

// --- Data classes for Editing Schemas ---

data class NodeSchemaEditState(
    val originalSchema: SchemaDefinitionItem,
    val currentName: String,
    val properties: List<SchemaProperty>
    // Note: Diffing logic will be in the ViewModel, comparing this to originalSchema
)

data class EdgeSchemaEditState(
    val originalSchema: SchemaDefinitionItem,
    val currentName: String,
    val connections: List<ConnectionPair>,
    val properties: List<SchemaProperty>
)

/**
 * Stores the generated hex color and its raw RGB components.
 */
data class ColorInfo(val hex: String, val rgb: IntArray, val composeColor: Color, val composeFontColor: Color)


/**
 * Represents the entire state of the "Edit" tab.
 */
sealed interface EditScreenState {
    data object None : EditScreenState
    data class CreateNode(val state: NodeCreationState) : EditScreenState
    data class CreateEdge(val state: EdgeCreationState) : EditScreenState
    data class CreateNodeSchema(val state: NodeSchemaCreationState) : EditScreenState
    data class CreateEdgeSchema(val state: EdgeSchemaCreationState) : EditScreenState
    data class EditNode(val state: NodeEditState) : EditScreenState
    data class EditEdge(val state: EdgeEditState) : EditScreenState
    data class EditNodeSchema(val state: NodeSchemaEditState) : EditScreenState
    data class EditEdgeSchema(val state: EdgeSchemaEditState) : EditScreenState
}

// --- Data classes for Graph View ---

/**
 * Represents the physical state of a node in the graph simulation.
 * @param id The unique ID from the database.
 * @param label The schema name (e.g., "Person").
 * @param displayProperty The text to show (e.g., "John Doe").
 * @param pos The current x/y position in the simulation space.
 * @param vel The current x/y velocity.
 * @param mass The mass of the node (influences physics).
 * @param radius The visual radius of the node.
 * @param colorInfo The color for drawing.
 * @param isFixed True if the node is being dragged by the user.
 */
data class GraphNode(
    val id: Long,
    val label: String,
    val displayProperty: String,
    var pos: androidx.compose.ui.geometry.Offset,
    var vel: androidx.compose.ui.geometry.Offset,
    val mass: Float,
    val radius: Float,
    val colorInfo: ColorInfo,
    var isFixed: Boolean = false // ADDED: Flag for dragging
)

/**
 * Represents the physical state of an edge in the graph simulation.
 * @param id The unique ID from the database.
 * @param sourceId The ID of the source node.
 * @param targetId The ID of the target node.
 * @param label The schema name (e.g., "KNOWS").
 * @param strength The "springiness" of the edge.
 * @param colorInfo The color for drawing.
 */
data class GraphEdge(
    val id: Long,
    val sourceId: Long,
    val targetId: Long,
    val label: String,
    val strength: Float,
    val colorInfo: ColorInfo
)

/**
 * Represents the pan and zoom state of the graph canvas.
 * @param pan The current x/y offset (pan) in world coordinates.
 * @param zoom The current zoom multiplier.
 */
data class TransformState(
    val pan: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    val zoom: Float = 1.0f
)
