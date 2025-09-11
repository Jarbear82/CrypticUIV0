package com.tau.cryptic_ui_v0

import com.kuzudb.DataType as KuzuDataType
import com.kuzudb.Value as KuzuValue

data class NodeDisplayItem(
    val label: String,
    val primarykeyProperty: DisplayItemProperty,
)

data class RelDisplayItem(
    val label: String,
    val src: NodeDisplayItem,
    val dst: NodeDisplayItem,
)

data class DisplayItemProperty(
    val key: String,
    val value: Any?
)

//  --- Data classes for Actual Nodes and Relationships ---
data class NodeValue(
    val id: String,
    val label: String,
    val properties: Map<String, Any?>
)

data class RelValue(
    val id: String,
    val label: String,
    val src: String,
    val dst: String,
    val properties: Map<String, Any?>
)

data class RecursiveRelValue(
    val nodes: List<NodeValue>,
    val rels: List<RelValue>
)

data class NodeTable(
    val label: String,
    val properties: List<TableProperty>,
    val labelChanged: Boolean,
    val propertiesChanged: Boolean
)

data class RelTable(
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
data class SchemaRel(
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
    val valueDataType: KuzuValue,
    val isPrimaryKey: Boolean,
    val keyChanged: Boolean = false,
    val valueDataTypeChanged: Boolean = false,
    val isPkChanged: Boolean = false
)

data class Schema(
    val nodeTables: List<SchemaNode>,
    val relTables: List<SchemaRel>
)


// --- Data classes for Query Results ---

data class FormattedResult(
    val headers: List<String>,
    val rows: List<List<Any?>>,
    val dataTypes: Map<String, KuzuDataType>,
    val summary: String,
    val rowCount: Long
)

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