package com.tau.cryptic_terminal

import com.kuzudb.Connection
import com.kuzudb.Database
import com.kuzudb.Value
import com.kuzudb.QueryResult as KuzuQueryResult
import com.kuzudb.FlatTuple
import com.kuzudb.ValueNodeUtil
import com.kuzudb.ValueRelUtil
import com.kuzudb.ValueRecursiveRelUtil
import com.kuzudb.DataTypeID as KuzuTypeId
import java.nio.file.Files
import java.nio.file.Paths
import java.math.BigInteger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@ExperimentalUuidApi
data class Node(
    val id: Uuid,
    val properties: Map<String, Any>
)

@ExperimentalUuidApi
data class Edge (
    val id: Uuid,
    val srcId: Uuid,
    val dstId: Uuid,
    val properties: Map<String, Any>
)

/**
 * A sealed class to represent the outcome of a single query result set.
 */
sealed class ParsedResultSet {
    /**
     * Represents a successfully executed query that returned data.
     * @param headers A list of column names.
     * @param rows A list of rows, where each row is a map of column name to its value.
     */
    data class Success(
        val headers: List<String>,
        val rows: List<Map<String, Any?>>
    ) : ParsedResultSet()

    /**
     * Represents a statement that failed to execute.
     * @param errorMessage The error message from the database.
     */
    data class Failure(
        val errorMessage: String
    ) : ParsedResultSet()
}


class KuzuDBService {
    private var db: Database? = null
    private var conn: Connection? = null

    /**
     * Initialize with a directory path
     */
    fun initialize(directoryPath: String) {
        try {
            val dbDirectory = Paths.get(directoryPath)
            if (!Files.exists(dbDirectory)) {
                Files.createDirectories(dbDirectory)
            }
            val dbPath = dbDirectory.resolve("database.kuzudb").toString()
            db = Database(dbPath)
            conn = Connection(db)
            println("KuzuDB initialized successfully at: $dbPath")
        } catch (e: Exception) {
            println("Failed to initialize KuzuDB: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Initialize in memory database
     */
    fun initialize() {
        try {
            db = Database(":memory:")
            conn = Connection(db)
            println("In-Memory KuzuDB initialized successfully.")
        } catch (e: Exception) {
            println("Failed to initialize KuzuDB: ${e.message}")
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            conn?.close()
            db?.close()
            println("KuzuDB connection closed.")
        } catch (e: Exception) {
            println("Failed to close KuzuDB connection: ${e.message}")
        }
    }

    fun executeQuery(query: String) {
        try {
            val result = conn?.query(query)
            printQuery(query, result)
        } catch (e: Exception) {
            println(e.message)
        }
    }

    fun executeQueryAndParse(query: String): List<ParsedResultSet>? {
        return try {
            conn?.query(query)?.parse()
        } catch (e: Exception) {
            println("An exception occurred during query execution: ${e.message}")
            // Return a single failure object in a list to indicate the whole query failed
            listOf(ParsedResultSet.Failure(e.message ?: "Unknown exception"))
        }
    }
}

/**
 * Parses a KuzuQueryResult into a list of ParsedResultSet objects.
 *
 * This function correctly handles:
 * - Multiple result sets from a single query string.
 * - Distinguishing between successful results and failures for each statement.
 * - Returning data in a structured, type-safe list.
 */
fun KuzuQueryResult.parse(): List<ParsedResultSet> {
    val allParsedResults = mutableListOf<ParsedResultSet>()

    // The 'use' block ensures the initial QueryResult object is always closed.
    this.use { initialResult ->
        var currentResult: KuzuQueryResult? = initialResult

        // Loop through all result sets (for queries with multiple statements)
        do {
            val result = currentResult ?: break

            // 1. Check if this part of the query was successful
            if (!result.isSuccess) {
                allParsedResults.add(ParsedResultSet.Failure(result.errorMessage))
            } else {
                val numColumns = result.numColumns
                val columnNames = List(numColumns.toInt()) { i -> result.getColumnName(i.toLong()) }
                val rowsData = mutableListOf<Map<String, Any?>>()

                // 2. Iterate Through Each Tuple (Row) to build the data
                while (result.hasNext()) {
                    val tuple: FlatTuple = result.next
                    // Create a map for the current row
                    val rowMap = columnNames.associateWith { colName ->
                        val colIndex = columnNames.indexOf(colName)
                        val kuzuValue = tuple.getValue(colIndex.toLong())
                        // CRITICAL FIX: Immediately convert the reused Kuzu Value
                        // into a native Kotlin object to prevent data overwriting.
                        convertKuzuValueToKotlinType(kuzuValue)
                    }
                    rowsData.add(rowMap)
                }
                allParsedResults.add(ParsedResultSet.Success(columnNames, rowsData))
            }

            // 3. Advance to the next result set if it exists
            currentResult = if (result.hasNextQueryResult()) result.nextQueryResult else null

        } while (currentResult != null)
    }
    return allParsedResults
}

/**
 * Deeply converts a reusable Kuzu Value object into a stable, native Kotlin type.
 * This is essential to prevent object reuse issues when parsing results.
 */
private fun convertKuzuValueToKotlinType(kuzuValue: Value?): Any? {
    if (kuzuValue == null || kuzuValue.isNull) {
        return null
    }
    return when (val typeId = kuzuValue.dataType.id) {
        KuzuTypeId.NODE -> {
            val propertyMap = mutableMapOf<String, Any?>()
            propertyMap["_ID"] = ValueNodeUtil.getID(kuzuValue).toString()
            propertyMap["_LABEL"] = ValueNodeUtil.getLabelName(kuzuValue)
            for (i in 0 until ValueNodeUtil.getPropertySize(kuzuValue)) {
                val name = ValueNodeUtil.getPropertyNameAt(kuzuValue, i)
                val value = ValueNodeUtil.getPropertyValueAt(kuzuValue, i)
                propertyMap[name] = convertKuzuValueToKotlinType(value)
            }
            propertyMap
        }
        KuzuTypeId.REL -> {
            val propertyMap = mutableMapOf<String, Any?>()
            propertyMap["_ID"] = ValueRelUtil.getID(kuzuValue).toString()
            propertyMap["_LABEL"] = ValueRelUtil.getLabelName(kuzuValue)
            propertyMap["_SRC"] = ValueRelUtil.getSrcID(kuzuValue).toString()
            propertyMap["_DST"] = ValueRelUtil.getDstID(kuzuValue).toString()
            for (i in 0 until ValueRelUtil.getPropertySize(kuzuValue)) {
                val name = ValueRelUtil.getPropertyNameAt(kuzuValue, i)
                val value = ValueRelUtil.getPropertyValueAt(kuzuValue, i)
                propertyMap[name] = convertKuzuValueToKotlinType(value)
            }
            propertyMap
        }
        KuzuTypeId.RECURSIVE_REL -> {
            val recursiveRelMap = mutableMapOf<String, Any?>()
            val nodes = ValueRecursiveRelUtil.getNodeList(kuzuValue)
            val rels = ValueRecursiveRelUtil.getRelList(kuzuValue)
            recursiveRelMap["_nodes"] = convertKuzuValueToKotlinType(nodes)
            recursiveRelMap["_rels"] = convertKuzuValueToKotlinType(rels)
            recursiveRelMap
        }
        KuzuTypeId.BOOL -> kuzuValue.getValue<Boolean>()
        KuzuTypeId.INT64 -> kuzuValue.getValue<Long>()
        KuzuTypeId.INT32 -> kuzuValue.getValue<Int>()
        KuzuTypeId.INT16 -> kuzuValue.getValue<Short>()
        KuzuTypeId.INT128 -> kuzuValue.getValue<BigInteger>().toString()
        KuzuTypeId.DOUBLE -> kuzuValue.getValue<Double>()
        KuzuTypeId.FLOAT -> kuzuValue.getValue<Float>()
        KuzuTypeId.STRING -> kuzuValue.getValue<String>()
        KuzuTypeId.UUID -> kuzuValue.toString()
        KuzuTypeId.LIST -> kuzuValue.getValue<List<*>>().map { convertKuzuValueToKotlinType(it as Value) }
        KuzuTypeId.MAP -> {
            val originalMap = kuzuValue.getValue<Map<*,*>>()
            originalMap.mapValues { convertKuzuValueToKotlinType(it.value as Value) }
        }
//        KuzuTypeId.STRUCT -> {
//            val structFields = kuzuValue.dataType.structFields
//            val structMap = mutableMapOf<String, Any?>()
//            for(i in structFields.indices) {
//                structMap[structFields[i].name] = convertKuzuValueToKotlinType(kuzuValue.getValue<List<Value>>()[i])
//            }
//            structMap
//        }
        else -> kuzuValue.toString() // Fallback for any other types
    }
}


fun printQuery(query: String, result: KuzuQueryResult?) {
    println("\n\n")
    println("Executing: ${query}...")
    println()
    println("Result:")
    val parsedResults = result?.parse()
    if (parsedResults == null) {
        println("Query did not return a result.")
        return
    }
    parsedResults.forEachIndexed { index, resultSet ->
        println("\n--- Result Set #${index + 1} ---")
        when (resultSet) {
            is ParsedResultSet.Success -> {
                println("✅ Success!")
                if (resultSet.headers.isNotEmpty()) {
                    println("Headers: ${resultSet.headers.joinToString(", ")}")
                }
                if (resultSet.rows.isEmpty() && resultSet.headers.isNotEmpty()) {
                    println("(No rows returned)")
                }
                resultSet.rows.forEach { row ->
                    println(row)
                }
            }
            is ParsedResultSet.Failure -> {
                println("❌ Failure: ${resultSet.errorMessage}")
            }
        }
    }
    println("\n\n")
}