package com.tau.cryptic_ui_v0

import androidx.compose.ui.graphics.Color

/**
 * Represents an on-disk notegraph database.
 * @param name The display name (the filename, e.g., "my_db.kuzu").
 * @param path The absolute path to the database file.
 */
data class NoteGraphItem(val name: String, val path: String)
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

data class RecursiveEdgeValue(
    val nodes: List<NodeValue>,
    val edges: List<EdgeValue>
)

// --- Data classes for Editing Instances ---
data class NodeTable(
    val label: String,
    var properties: List<TableProperty>, // This list will be mutated during edit
    val labelChanged: Boolean, // Not used yet
    val propertiesChanged: Boolean // Not used yet
) {
    /**
     * Creates a deep copy of the NodeTable, ensuring the properties list
     * is a new list containing copies of the TableProperty objects.
     */
    fun deepCopy(): NodeTable {
        return NodeTable(
            label = this.label,
            properties = this.properties.map { it.copy() },
            labelChanged = this.labelChanged,
            propertiesChanged = this.propertiesChanged
        )
    }
}


data class EdgeTable(
    val label: String,
    val src: NodeDisplayItem,
    val dst: NodeDisplayItem,
    var properties: List<TableProperty>?, // This list will be mutated
    val labelChanged: Boolean,
    val srcChanged: Boolean,
    val dstChanged: Boolean,
    val propertiesChanged: Boolean
) {
    /**
     * Creates a deep copy of the EdgeTable, ensuring the properties list
     * is a new list containing copies of the TableProperty objects.
     */
    fun deepCopy(): EdgeTable {
        return EdgeTable(
            label = this.label,
            src = this.src,
            dst = this.dst,
            properties = this.properties?.map { it.copy() },
            labelChanged = this.labelChanged,
            srcChanged = this.srcChanged,
            dstChanged = this.dstChanged,
            propertiesChanged = this.propertiesChanged
        )
    }
}

data class TableProperty(
    val key: String,
    var value: Any?, // This value will be mutated
    val isPrimaryKey: Boolean,
    var valueChanged: Boolean // Flag to track changes
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
    val srcLabel: String,
    val dstLabel: String,
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

// --- Data classes for Editing Schemas ---
data class NodeSchemaEditState(
    val originalSchema: SchemaNode,
    var currentLabel: String,
    val properties: MutableList<EditableSchemaProperty>
)

data class EdgeSchemaEditState(
    val originalSchema: SchemaEdge,
    var currentLabel: String,
    val properties: MutableList<EditableSchemaProperty>
    // Note: Altering src/dst of an edge schema is not supported by Kuzu.
)

data class EditableSchemaProperty(
    var key: String,
    var valueDataType: String,
    var isPrimaryKey: Boolean,
    val originalKey: String, // To know what to rename
    val isNew: Boolean = false,
    var isDeleted: Boolean = false
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
    val srcTable: String? = null,
    val dstTable: String? = null,
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