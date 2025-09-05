package com.tau.cryptic_terminal

import com.kuzudb.Connection
import com.kuzudb.Database
import com.kuzudb.QueryResult as KuzuQueryResult
import com.kuzudb.FlatTuple
import java.nio.file.Files
import java.nio.file.Paths
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
        var result: KuzuQueryResult? = null
        try {
            result =  conn?.query(query)
            printQuery(query, result)
        } catch (e: Exception) {
            println(e.message)
        }
    }
}

/**
 * Parses and prints the contents of a KuzuQueryResult directly.
 */
fun KuzuQueryResult.parseAndPrint() {
    this.use { initialResult ->
        var currentResult: KuzuQueryResult? = initialResult
        var resultSetCounter = 1

        do {
            val result = currentResult ?: break

            println("\n--- Result Set #$resultSetCounter ---")
            resultSetCounter++

            if (!result.isSuccess) {
                println("❌ Query Failed: ${result.errorMessage}")
            } else {
                val numColumns = result.numColumns
                if (numColumns == 0L) {
                    println("Query successful and returned no data.")
                    result.querySummary?.let { println("📊 Summary: $it") }
                } else {
                    val columnNames = List(numColumns.toInt()) { index ->
                        result.getColumnName(index.toLong())
                    }

                    println("✅ Success!")
                    println("Headers: ${columnNames.joinToString(" | ")}")
                    println("-".repeat(columnNames.joinToString(" | ").length))

                    while (result.hasNext()) {
                        val tuple: FlatTuple = result.next
                        val rowString = columnNames.mapIndexed { index, name ->
                            val value = tuple.getValue(index.toLong())
                            "$name: $value"
                        }.joinToString("  |  ")
                        println(rowString)
                    }
                    result.querySummary?.let { println("📊 Summary: $it") }
                }
            }

            currentResult = if (result.hasNextQueryResult()) result.nextQueryResult else null

        } while (currentResult != null)
    }
}

fun printQuery(query: String, result: KuzuQueryResult?) {
    println("\n\n")
    println("Executing: ${query}...")
    println()
    println("Result:")
    result?.parseAndPrint()
    println("\n\n")
}