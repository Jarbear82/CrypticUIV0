package com.tau.cryptic_terminal

import com.kuzudb.Connection as KuzuConnection
import com.kuzudb.Database as KuzuDatabase
import com.kuzudb.QueryResult as KuzuQueryResult
import com.kuzudb.DataType as KuzuDataType
import com.kuzudb.Value as KuzuValue
import com.kuzudb.Version as KuzuVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths

// --- Data classes for Schema Representation ---

data class NodeTableSchema(
    val name: String,
    val properties: List<Map<String, String>>
)

data class RelTableSchema(
    val name: String,
    val src: String,
    val dst: String,
    val properties: List<Map<String, String>>
)

data class Schema(
    val nodeTables: List<NodeTableSchema>,
    val relTables: List<RelTableSchema>
)


// --- Data classes for Query Results ---

/**
 * A data class to hold the parsed results from a single query result set.
 * @param headers A list of column names.
 * @param rows A list of rows, where each row is a list of its column values as strings.
 * @param dataTypes A map of column names to their corresponding Kuzu data types.
 * @param summary A summary of the query execution times.
 * @param rowCount The number of rows in this result set.
 */
data class FormattedResult(
    val headers: List<String>,
    val rows: List<List<String?>>,
    val dataTypes: Map<String, KuzuDataType>,
    val summary: String,
    val rowCount: Long
)

/**
 * A container for the result of a query execution, indicating if the schema changed.
 */
data class ExecutionResult(
    val results: List<FormattedResult>,
    val isSchemaChanged: Boolean
)


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

    /**
     * Executes a Cypher query and returns a structured result.
     * It also checks if the query modified the database schema.
     */
    fun executeQuery(query: String): ExecutionResult {
        val connection = conn ?: throw IllegalStateException("Database not initialized.")
        val processedResults = mutableListOf<FormattedResult>()
        var queryException: Exception? = null

        try {
            // Using .use ensures the queryResult is closed automatically
            connection.query(query).use { queryResult ->
                var currentResult: KuzuQueryResult? = queryResult
                do {
                    val resultToProcess = currentResult ?: break
                    processedResults.add(processSingleResult(resultToProcess))
                    currentResult = if (resultToProcess.hasNextQueryResult()) resultToProcess.nextQueryResult else null
                } while (currentResult != null)
            }
        } catch (e: Exception) {
            queryException = e
        }

        val newSchemaSignature = fetchSchemaSignature()
        val isSchemaChanged = (currentSchemaSignature != newSchemaSignature)
        if (isSchemaChanged) {
            currentSchemaSignature = newSchemaSignature
        }

        queryException?.let { throw it }

        return ExecutionResult(processedResults, isSchemaChanged)
    }

    /**
     * Fetches the complete database schema.
     */
    fun getSchema(): Schema {
        val tablesResult = executeQuery("CALL SHOW_TABLES() RETURN *;").results.firstOrNull()
        val nodeTables = mutableListOf<NodeTableSchema>()
        val relTables = mutableListOf<RelTableSchema>()

        tablesResult?.rows?.forEach { row ->
            val tableName = row[1] ?: ""
            val tableType = row[2] ?: ""
            val propertiesResult = executeQuery("CALL TABLE_INFO('$tableName') RETURN *;").results.first()
            val properties = propertiesResult.rows.map { propRow ->
                mapOf(
                    "property_id" to (propRow[0] ?: ""),
                    "name" to (propRow[1] ?: ""),
                    "type" to (propRow[2] ?: ""),
                    "primary_key" to (propRow[4] ?: "false")
                )
            }

            if (tableType == "NODE") {
                nodeTables.add(NodeTableSchema(tableName, properties))
            } else if (tableType == "REL") {
                val connResult = executeQuery("CALL SHOW_CONNECTION('$tableName') RETURN *;").results.first().rows.first()
                relTables.add(RelTableSchema(tableName, connResult[0] ?: "N/A", connResult[1] ?: "N/A", properties))
            }
        }
        return Schema(nodeTables, relTables)
    }

    private fun fetchSchemaSignature(): String {
        return try {
            conn?.query("CALL SHOW_TABLES() RETURN *;")?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun processSingleResult(result: KuzuQueryResult): FormattedResult = runBlocking {
        withContext(Dispatchers.IO) {
            val numColumns = result.numColumns
            val headers = (0 until numColumns).map { result.getColumnName(it) }
            val dataTypesMap = headers.zip((0 until numColumns).map { result.getColumnDataType(it) }).toMap()
            val rows = mutableListOf<List<String?>>()

            result.resetIterator()
            while (result.hasNext()) {
                val tuple = result.next
                val rowValues = (0 until numColumns).map { colIdx ->
                    // .clone() is CRITICAL as the underlying FlatTuple object is reused by the API.
                    tuple.getValue(colIdx).clone().use { v ->
                        if (v.isNull) null else v.toString()
                    }
                }
                rows.add(rowValues)
            }
            val summary = "Execution: ${result.querySummary.executionTime}ms, Compilation: ${result.querySummary.compilingTime}ms"
            FormattedResult(headers, rows, dataTypesMap, summary, result.numTuples)
        }
    }
}

