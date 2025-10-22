package com.tau.cryptic_ui_v0

import androidx.compose.ui.graphics.Color

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

data class NodeTable(
    val label: String,
    val properties: List<TableProperty>,
    val labelChanged: Boolean,
    val propertiesChanged: Boolean
)

data class EdgeTable(
    val label: String,
    val src: NodeDisplayItem,
    val dst: NodeDisplayItem,
    val properties: List<TableProperty>?,
    val labelChanged: Boolean,
    val srcChanged: Boolean,
    val dstChanged: Boolean,
    val propertiesChanged: Boolean
)

data class TableProperty(
    val key: String,
    val value: Any?,
    val isPrimaryKey: Boolean,
    val valueChanged: Boolean
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