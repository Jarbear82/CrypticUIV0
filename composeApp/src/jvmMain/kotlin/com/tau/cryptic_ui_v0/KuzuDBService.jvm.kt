package com.tau.cryptic_ui_v0

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


// --- The Main Service Class ---

class KuzuDBService {
    private var db: KuzuDatabase? = null
    private var conn: KuzuConnection? = null
    private var currentSchemaSignature: String? = null
    private var storagePath: String? = null

    fun initialize(directoryPath: String? = null) {
        try {
            storagePath = directoryPath
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

    fun getDBMetaData(): DBMetaData {
        val name = if (storagePath != null) Paths.get(storagePath).fileName.toString() else "In-Memory"
        val version = KuzuVersion.getVersion()
        val storage = storagePath ?: "RAM"
        return DBMetaData(name, version, storage)
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
        println("\n\n Executing: $query")

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

                println("Result: $results\n\n")

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
                while (tablesResult.hasNext()) {
                    val row = tablesResult.next
                    val tableName = row.getValue(1).getValue<Any>().toString()
                    val tableType = row.getValue(2).getValue<Any>().toString()

                    if (tableType == "NODE") {
                        val properties = mutableListOf<SchemaProperty>()
                        val propertiesResult = conn!!.query("CALL TABLE_INFO('$tableName') RETURN *;")
                        while (propertiesResult.hasNext()) {
                            val propRow = propertiesResult.next
                            val propName = propRow.getValue(1).getValue<Any>().toString()
                            val propType = propRow.getValue(2).dataType
                            val isPrimary = propRow.getValue(4).getValue<Boolean>()
                            properties.add(SchemaProperty(propName, propType, isPrimary))
                        }
                        nodeTables.add(NodeTableSchema(tableName, properties))
                    } else if (tableType == "REL") {
                        val properties = mutableListOf<SchemaProperty>()
                        val propertiesResult = conn!!.query("CALL TABLE_INFO('$tableName') RETURN *;")
                        while (propertiesResult.hasNext()) {
                            val propRow = propertiesResult.next
                            val propName = propRow.getValue(1).getValue<Any>().toString()
                            // Exclude internal properties from the schema list
                            if (propName !in listOf("_src", "_dst", "_id")) {
                                val propType = propRow.getValue(2).dataType
                                // Relationship properties cannot be primary keys
                                properties.add(SchemaProperty(key = propName, valueDataType = propType, isPrimaryKey = false))
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
                        relTables.add(RelTableSchema(label = tableName, srcLabel = from, dstLabel = to, properties = properties))
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
                val properties = mutableMapOf<String, Any?>()
                for (i in 0 until ValueNodeUtil.getPropertySize(v)) {
                    val propName = ValueNodeUtil.getPropertyNameAt(v, i)
                    ValueNodeUtil.getPropertyValueAt(v, i).use { propValue ->
                        properties[propName] = getFormattedValue(propValue.clone())
                    }
                }
                val node = NodeValue(
                    id = ValueNodeUtil.getID(v).toString(),
                    label = ValueNodeUtil.getLabelName(v),
                    properties = properties
                )
                println("Created: $node")
                return node
            }
            KuzuDataTypeID.REL -> {
                val properties = mutableMapOf<String, Any?>()
                for (i in 0 until ValueRelUtil.getPropertySize(v)) {
                    val propName = ValueRelUtil.getPropertyNameAt(v, i)
                    ValueRelUtil.getPropertyValueAt(v, i).use { propValue ->
                        properties[propName] = getFormattedValue(propValue.clone())
                    }
                }
                val rel = RelValue(
                    id = ValueRelUtil.getID(v).toString(),
                    label = ValueRelUtil.getLabelName(v),
                    src = ValueRelUtil.getSrcID(v).toString(),
                    dst = ValueRelUtil.getDstID(v).toString(),
                    properties = properties
                )
                println("Created: $rel")
                return rel
            }
            KuzuDataTypeID.RECURSIVE_REL -> {
                val nodesList = ValueRecursiveRelUtil.getNodeList(v).clone().use { nodesValue ->
                    getFormattedValue(nodesValue) as? List<NodeValue>
                }
                val relsList = ValueRecursiveRelUtil.getRelList(v).clone().use { relsValue ->
                    getFormattedValue(relsValue) as? List<RelValue>
                }
                RecursiveRelValue(nodes = nodesList ?: emptyList(), rels = relsList ?: emptyList())
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

    suspend fun getPrimaryKey(tableName: String): String? = withContext(Dispatchers.IO) {
        try {
            val propertiesResult = conn!!.query("CALL TABLE_INFO('$tableName') RETURN *;")
            while (propertiesResult.hasNext()) {
                val propRow = propertiesResult.next
                val isPrimary = propRow.getValue(4).getValue<Boolean>()
                if (isPrimary) {
                    return@withContext propRow.getValue(1).getValue<Any>().toString()
                }
            }
            null
        } catch (e: Exception) {
            println("Error getting primary key for table $tableName: ${e.message}")
            null
        }
    }
}