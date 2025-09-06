package com.tau.cryptic_terminal

import com.kuzudb.Connection as KuzuConnection
import com.kuzudb.Database as KuzuDatabase
import com.kuzudb.QueryResult as KuzuQueryResult
import com.kuzudb.DataType as KuzuDataType
import com.kuzudb.Value as KuzuValue
import com.kuzudb.Version as KuzuVersion
import com.kuzudb.DataTypeID as KuzuDataTypeID
import com.kuzudb.KuzuList
import com.kuzudb.KuzuStruct
import com.kuzudb.KuzuMap
import com.kuzudb.ValueNodeUtil
import com.kuzudb.ValueRelUtil
import com.kuzudb.ValueRecursiveRelUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths
import java.math.BigInteger

//  --- Data classes for Actual Nodes and Relationships ---
data class NodeTable(
    val id: BigInteger,
    val label: 
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

// --- The Main Service Class ---

class KuzuDBService {
    private var db: KuzuDatabase? = null
    private var conn: KuzuConnection? = null
    private var currentSchemaSignature: String? = null

    fun initialize(directoryPath: String? = null) {
        try {
            db = if (directoryPath != null) {
                val dbDirectory = Paths.get(directoryPath)
                if (!Files.exists(dbDirectory)) {
                    Files.createDirectories(dbDirectory)
                }
                println("Initializing KuzuDB at: $directoryPath")
                KuzuDatabase(directoryPath)
            } else {
                println("Initializing In-Memory KuzuDB.")
                KuzuDatabase(":memory:")
            }
            conn = KuzuConnection(db)
            println("KuzuDB Initialized. Version: ${KuzuVersion.getVersion()}")
            currentSchemaSignature = fetchSchemaSignature()
        } catch (e: Exception) {
            println("Failed to initialize KuzuDB: ${e.message}")
            throw IllegalStateException("Could not initialize Kuzu database.", e)
        }
    }

    fun close() {
        try {
            conn?.close()
            db?.close()
            println("KuzuDB connection closed.")
        } catch (e: Exception) {
            println("Error closing KuzuDB connection: ${e.message}")
        }
    }

    fun isInitialized(): Boolean = db != null && conn != null

    suspend fun executeQuery(query: String): ExecutionResult {
        if (!isInitialized()) return ExecutionResult.Error("Database not initialized.")
        return withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<FormattedResult>()
                val queryResult = conn!!.query(query)
                results.add(processSingleResult(queryResult))

                var nextResult = queryResult
                while (nextResult.hasNextQueryResult()) {
                    nextResult = nextResult.nextQueryResult
                    results.add(processSingleResult(nextResult))
                }

                val newSchemaSignature = fetchSchemaSignature()
                val schemaChanged = newSchemaSignature != currentSchemaSignature
                if (schemaChanged) {
                    currentSchemaSignature = newSchemaSignature
                }

                ExecutionResult.Success(results, schemaChanged)
            } catch (e: Exception) {
                ExecutionResult.Error(e.message ?: "An unknown error occurred.")
            }
        }
    }

    suspend fun getSchema(): Schema? {
        if (!isInitialized()) return null
        return withContext(Dispatchers.IO) {
            try {
                val nodeTables = mutableListOf<NodeTableSchema>()
                val relTables = mutableListOf<RelTableSchema>()

                val tablesResult = conn!!.query("CALL SHOW_TABLES() RETURN *;")
                println("\n---\n $tablesResult\n---\n")
                while (tablesResult.hasNext()) {
                    val row = tablesResult.next
                    println("Row $row")
                    // 2. Changed how values are retrieved to prevent casting errors
                    val tableId = row.getValue(0).getValue<BigInteger>()
                    val tableName = row.getValue(1).getValue<Any>().toString()
                    val tableType = row.getValue(2).getValue<Any>().toString()

                    if (tableType == "NODE") {
                        val properties = mutableListOf<Pair<String, Any>>()
                        // 1. Added "RETURN *"
                        val propertiesResult = conn!!.query("CALL TABLE_INFO('$tableName') RETURN *;")
                        while (propertiesResult.hasNext()) {
                            val propRow = propertiesResult.next
                            // 2. Changed value retrieval
                            val propName = propRow.getValue(1).getValue<Any>().toString()
                            val propType = propRow.getValue(2).getValue<Any>()
                            val isPrimary = propRow.getValue(4).getValue<Boolean>()
                            properties.add(propName to (if (isPrimary) "$propType (PK)" else propType))
                        }
                        nodeTables.add(NodeTableSchema(tableId, tableName, properties))
                    } else if (tableType == "REL") {
                        val properties = mutableListOf<Pair<String, Any>>()
                        val propertiesResult = conn!!.query("CALL TABLE_INFO('$tableName') RETURN *;")
                        while (propertiesResult.hasNext()) {
                            val propRow = propertiesResult.next
                            val propName = propRow.getValue(1).getValue<Any>().toString()
                            if (propName !in listOf("_src", "_dst")) {
                                val propType = propRow.getValue(2).getValue<Any>()
                                properties.add(propName to propType)
                            }
                        }
                        val connResult = conn!!.query("CALL SHOW_CONNECTION('$tableName') RETURN *;")
                        var from = ""
                        var to = ""
                        if (connResult.hasNext()) {
                            val connRow = connResult.next
                            from = connRow.getValue(0).getValue<Any>().toString()
                            to = connRow.getValue(1).getValue<Any>().toString()
                        }
                        relTables.add(RelTableSchema(tableId,tableName, from, to, properties))
                    }
                }
                Schema(nodeTables, relTables)
            } catch (e: Exception) {
                println("Error fetching schema: ${e.message}")
                null
            }
        }
    }

    private fun fetchSchemaSignature(): String {
        return try {
            conn?.query("CALL SHOW_TABLES() RETURN *;")?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getFormattedValue(v: KuzuValue): Any? {
        if (v.isNull) return null
        return when (v.dataType.id) {
            KuzuDataTypeID.BOOL, KuzuDataTypeID.INT64, KuzuDataTypeID.INT32, KuzuDataTypeID.INT16, KuzuDataTypeID.INT8,
            KuzuDataTypeID.UINT64, KuzuDataTypeID.UINT32, KuzuDataTypeID.UINT16, KuzuDataTypeID.UINT8,
            KuzuDataTypeID.INT128, KuzuDataTypeID.DOUBLE, KuzuDataTypeID.FLOAT, KuzuDataTypeID.STRING,
            KuzuDataTypeID.SERIAL -> v.getValue<Any>()
            KuzuDataTypeID.DATE, KuzuDataTypeID.TIMESTAMP, KuzuDataTypeID.INTERVAL,
            KuzuDataTypeID.INTERNAL_ID, KuzuDataTypeID.BLOB, KuzuDataTypeID.UUID -> v.toString()

            KuzuDataTypeID.NODE -> {
                val nodeProperties = mutableMapOf<String, Any?>()
                nodeProperties["_id"] = ValueNodeUtil.getID(v).toString()
                nodeProperties["_label"] = ValueNodeUtil.getLabelName(v)
                for (i in 0 until ValueNodeUtil.getPropertySize(v)) {
                    val propName = ValueNodeUtil.getPropertyNameAt(v, i)
                    ValueNodeUtil.getPropertyValueAt(v, i).use { propValue ->
                        nodeProperties[propName] = getFormattedValue(propValue.clone())
                    }
                }
                nodeProperties
            }
            KuzuDataTypeID.REL -> {
                val relProperties = mutableMapOf<String, Any?>()
                relProperties["_id"] = ValueRelUtil.getID(v).toString()
                relProperties["_label"] = ValueRelUtil.getLabelName(v)
                relProperties["_src"] = ValueRelUtil.getSrcID(v).toString()
                relProperties["_dst"] = ValueRelUtil.getDstID(v).toString()
                for (i in 0 until ValueRelUtil.getPropertySize(v)) {
                    val propName = ValueRelUtil.getPropertyNameAt(v, i)
                    ValueRelUtil.getPropertyValueAt(v, i).use { propValue ->
                        relProperties[propName] = getFormattedValue(propValue.clone())
                    }
                }
                relProperties
            }
            KuzuDataTypeID.RECURSIVE_REL -> {
                val recursiveRel = mutableMapOf<String, Any?>()
                ValueRecursiveRelUtil.getNodeList(v).clone().use { nodesValue ->
                    recursiveRel["_nodes"] = getFormattedValue(nodesValue)
                }
                ValueRecursiveRelUtil.getRelList(v).clone().use { relsValue ->
                    recursiveRel["_rels"] = getFormattedValue(relsValue)
                }
                recursiveRel
            }
            KuzuDataTypeID.LIST, KuzuDataTypeID.ARRAY -> {
                KuzuList(v).use { list ->
                    (0 until list.listSize).map { i ->
                        list.getListElement(i).clone().use { element ->
                            getFormattedValue(element)
                        }
                    }
                }
            }
            KuzuDataTypeID.STRUCT, KuzuDataTypeID.UNION -> {
                KuzuStruct(v).use { struct ->
                    val structMap = mutableMapOf<String, Any?>()
                    for (i in 0 until struct.numFields) {
                        val fieldName = struct.getFieldNameByIndex(i)
                        struct.getValueByIndex(i).clone().use { fieldValue ->
                            structMap[fieldName] = getFormattedValue(fieldValue)
                        }
                    }
                    structMap
                }
            }
            KuzuDataTypeID.MAP -> {
                KuzuMap(v).use { map ->
                    val resultMap = mutableMapOf<Any?, Any?>()
                    for (i in 0 until map.numFields) {
                        val key = map.getKey(i).clone().use { getFormattedValue(it) }
                        val value = map.getValue(i).clone().use { getFormattedValue(it) }
                        resultMap[key] = value
                    }
                    resultMap
                }
            }
            else -> v.toString() // Fallback
        }
    }

    private fun processSingleResult(result: KuzuQueryResult): FormattedResult = runBlocking {
        withContext(Dispatchers.IO) {
            val numColumns = result.numColumns
            val headers = (0 until numColumns).map { result.getColumnName(it) }
            val dataTypesMap = headers.zip((0 until numColumns).map { result.getColumnDataType(it) }).toMap()
            val rows = mutableListOf<List<Any?>>()

            result.resetIterator()
            while (result.hasNext()) {
                val tuple = result.next
                val rowValues = (0 until numColumns).map { colIdx ->
                    tuple.getValue(colIdx).clone().use { v ->
                        getFormattedValue(v)
                    }
                }
                rows.add(rowValues)
            }
            val summary = "Execution: ${result.querySummary.executionTime}ms, Compilation: ${result.querySummary.compilingTime}ms"
            FormattedResult(headers, rows, dataTypesMap, summary, result.numTuples)
        }
    }
}