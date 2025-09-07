package com.tau.cryptic_ui_v0

import com.kuzudb.DataType as KuzuDataType
import java.math.BigInteger

data class DisplayItem(
    val id: String,
    val label: String,
    val primaryKey: String,
    val properties: Map<String, Any?> = emptyMap()
)

data class RelDisplayItem(
    val id: String,
    val label: String,
    val src: String,
    val dst: String,
    val srcLabel: String,
    val dstLabel: String,
    val properties: Map<String, Any?> = emptyMap()
)

//  --- Data classes for Actual Nodes and Relationships ---
data class NodeTable(
    val id: BigInteger,
    val label: String,
    val nodeProperties: List<Pair<String, Any>>
)

data class RelTable(
    // TODO: ADD src and dest
    val id: BigInteger,
    val label: String,
    val nodeProperties: List<Pair<String, Any>>
)

data class RecursiveRelTable(
    // TODO: implement similar to java api recursive_rel
    val id: BigInteger,
    val label: String,
    val nodeProperties: List<Pair<String, Any>>
)

// --- Data classes for Schema Representation ---

data class NodeTableSchema(
    val id: BigInteger,
    val name: String,
    val properties: List<Pair<String, Any>>
)
data class RelTableSchema(
    val id: BigInteger,
    val name: String,
    val src: String,
    val dst: String,
    val properties: List<Pair<String, Any>>
)
data class Schema(
    val nodeTables: List<NodeTableSchema>,
    val relTables: List<RelTableSchema>
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