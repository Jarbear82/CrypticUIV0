package com.tau.cryptic_ui_v0

import androidx.compose.ui.graphics.Color

/**
 * Represents an on-disk notegraph database.
 * @param name The display name (the filename, e.g., "my_db.kuzu").
 * @param path The absolute path to the database file.
 */
data class NoteGraphItem(
    val name: String,
    val path: String
)
data class NodeDisplayItem(
    val label: String,
    val primarykeyProperty: DisplayItemProperty,
)

data class EdgeDisplayItem(
    val label: String,
    val src: NodeDisplayItem,
    val dst: NodeDisplayItem,
)

data class DisplayItemProperty(
    val key: String,
    val value: Any?
)

//  --- Data classes for Actual Nodes and Edges ---
data class NodeValue(
    val id: String,
    val label: String,
    val properties: Map<String, Any?>
)

data class EdgeValue(
    val id: String,
    val label: String,
    val src: String,
    val dst: String,
    val properties: Map<String, Any?>
)

data class ConnectionPair(
    val src: String,
    val dst: String
)

data class RecursiveEdgeValue(
    val nodes: List<NodeValue>,
    val edges: List<EdgeValue>
)

// --- Data classes for Editing Instances (NOW IMMUTABLE) ---
data class NodeTable(
    val label: String,
    val properties: List<TableProperty>, // Changed to val
    val labelChanged: Boolean,
    val propertiesChanged: Boolean
)
// No deepCopy() needed

data class EdgeTable(
    val label: String,
    val src: NodeDisplayItem,
    val dst: NodeDisplayItem,
    val properties: List<TableProperty>?, // Changed to val
    val labelChanged: Boolean,
    val srcChanged: Boolean,
    val dstChanged: Boolean,
    val propertiesChanged: Boolean
)
// No deepCopy() needed

data class TableProperty(
    val key: String,
    val value: Any?, // Changed to val
    val isPrimaryKey: Boolean,
    val valueChanged: Boolean // Changed to val
)

// --- Data classes for Schema Representation ---

data class SchemaNode(
    val label: String,
    val properties: List<SchemaProperty>,
    val labelChanged: Boolean = false,
    val propertiesChanged: Boolean = false
)
data class SchemaEdge(
    val label: String,
    val connections: List<ConnectionPair>,
    val properties: List<SchemaProperty>,
    val labelChanged: Boolean = false,
    val srcLabelChanged: Boolean = false,
    val dstLabelChanged: Boolean = false,
    val propertiesChanged: Boolean = false,
)

data class SchemaProperty(
    val key: String,
    val valueDataType: String,
    val isPrimaryKey: Boolean,
    val keyChanged: Boolean = false,
    val valueDataTypeChanged: Boolean = false,
    val isPkChanged: Boolean = false
)

data class Schema(
    val nodeTables: List<SchemaNode>,
    val edgeTables: List<SchemaEdge>
)

// --- Data classes for Editing Schemas (NOW IMMUTABLE) ---
data class NodeSchemaEditState(
    val originalSchema: SchemaNode,
    val currentLabel: String, // Changed to val
    val properties: List<EditableSchemaProperty> // Changed to val (was MutableList)
)

data class EdgeSchemaEditState(
    val originalSchema: SchemaEdge,
    val currentLabel: String, // Changed to val
    val properties: List<EditableSchemaProperty> // Changed to val (was MutableList)
    // Note: Altering src/dst of an edge schema is not supported by Kuzu.
)

data class EditableSchemaProperty(
    val key: String, // Changed to val
    val valueDataType: String, // Changed to val
    val isPrimaryKey: Boolean, // Changed to val
    val originalKey: String, // To know what to rename
    val isNew: Boolean = false,
    val isDeleted: Boolean = false // Changed to val
)


// --- Data classes for Query Results ---
data class FormattedResult(
    val headers: List<String>,
    val rows: List<List<Any?>>,
    val dataTypes: Map<String, Any?>,
    val summary: String,
    val rowCount: Long
) {
    override fun toString(): String {
        val rowListStr = rows.joinToString(separator = ",\n        ", prefix = "[\n        ", postfix = "\n    ]") { it.toString() }

        return "Formatted Result(\n" +
                "    headers = $headers\n" +
                "    rows = $rowListStr\n" +
                "    dataTypes = $dataTypes\n" +
                "    summary = $summary\n" +
                "    rowCount = $rowCount\n" +
                ")"
    }
}

sealed class ExecutionResult {
    data class Success(val results: List<FormattedResult>, val isSchemaChanged: Boolean) : ExecutionResult()
    data class Error(val message: String) : ExecutionResult()
}

// --- Data class for Database Metadata ---
data class DBMetaData(
    val name: String,
    val version: String,
    val storage: String
)

// --- Data class for Node Creation ---
data class NodeCreationState(
    val schemas: List<SchemaNode>,
    val selectedSchema: SchemaNode? = null,
    val properties: Map<String, String> = emptyMap()
)

// --- Data class for Edge Creation ---
data class EdgeCreationState(
    val schemas: List<SchemaEdge>,
    val availableNodes: List<NodeDisplayItem>,
    val selectedSchema: SchemaEdge? = null,
    val selectedConnection: ConnectionPair? = null,
    val src: NodeDisplayItem? = null,
    val dst: NodeDisplayItem? = null,
    val properties: Map<String, String> = emptyMap()
)

// --- Data class for Node Schema Creation ---
data class NodeSchemaCreationState(
    val tableName: String = "",
    val properties: List<Property> = listOf(Property(isPrimaryKey = true))
)

// --- Data class for Edge Schema Creation ---
data class EdgeSchemaCreationState(
    val tableName: String = "",
    val connections: List<ConnectionPair> = emptyList(),
    val properties: List<Property> = emptyList(),
    val allNodeSchemas: List<SchemaNode> = emptyList()
)


data class Property(
    val name: String = "",
    val type: String = "STRING",
    val isPrimaryKey: Boolean = false
)

/**
 * Stores the generated hex color and its raw RGB components.
 */
data class ColorInfo(val hex: String, val rgb: IntArray, val composeColor: Color, val composeFontColor: Color)

// --- NEW SEALED INTERFACE ---
/**
 * Represents the entire state of the "Edit" tab.
 */
sealed interface EditScreenState {
    data object None : EditScreenState // Default state, nothing is happening
    data class CreateNode(val state: NodeCreationState) : EditScreenState
    data class CreateEdge(val state: EdgeCreationState) : EditScreenState
    data class CreateNodeSchema(val state: NodeSchemaCreationState) : EditScreenState
    data class CreateEdgeSchema(val state: EdgeSchemaCreationState) : EditScreenState
    data class EditNode(val state: NodeTable) : EditScreenState
    data class EditEdge(val state: EdgeTable) : EditScreenState
    data class EditNodeSchema(val state: NodeSchemaEditState) : EditScreenState
    data class EditEdgeSchema(val state: EdgeSchemaEditState) : EditScreenState
}