package com.tau.cryptic_terminal

import kotlin.system.exitProcess

enum class Choice(val value: String, val description: String) {
    Quit("0", "Quit"),
    ExecuteQuery("1", "Execute a Cypher Query"),
    ShowSchema("2", "Show Database Schema"),
    ListNodes("3", "List all Node records"),
    ListEdges("4", "List all Relationship records"),
    ListAll("5", "List all Node and Relationship records");

    companion object {
        fun fromString(value: String): Choice? {
            return entries.find { it.value == value }
        }
    }
}

fun terminalProgram() {
    println("Welcome to Cryptic Terminal!")

    // Using a file-based DB for persistence across sessions.
    // To use in-memory, just call db.initialize() with no arguments.
    val db = KuzuDBService()
    try {
        db.initialize()
    } catch (e: IllegalStateException) {
        println("Could not start the application. Exiting.")
        exitProcess(1)
    }

    var running = true
    while (running) {
        println("\n-------------------------------------------------")
        printMenu()
        println("-------------------------------------------------")
        val choice = getChoice()

        when (choice) {
            Choice.ExecuteQuery -> {
                print("Enter your Cypher query: ")
                val query = readlnOrNull() ?: ""
                if (query.isNotBlank()) {
                    handleExecution(db, query)
                } else {
                    println("Query cannot be empty.")
                }
            }
            Choice.ShowSchema -> showSchema(db)
            Choice.ListNodes -> handleExecution(db, "MATCH (n) RETURN n;")
            Choice.ListEdges -> handleExecution(db, "MATCH ()-[r]->() RETURN r;")
            Choice.ListAll -> handleExecution(db, "MATCH (n)-[r]->(m) RETURN n, r, m;")
            Choice.Quit -> {
                running = false
                db.close()
                println("Goodbye!")
            }
        }
    }
}

/**
 * Handles the execution of a query and prints the formatted results or error.
 */
private fun handleExecution(db: KuzuDBService, query: String) {
    try {
        println("\nExecuting: $query")
        val executionResult = db.executeQuery(query)
        if (executionResult.isSchemaChanged) {
            println("📢 Schema has changed as a result of the query.")
        }
        if (executionResult.results.isEmpty()) {
            println("Query executed successfully, but returned no data.")
            return
        }
        executionResult.results.forEachIndexed { index, result ->
            if (executionResult.results.size > 1) {
                println("\n--- Result Set ${index + 1} ---")
            }
            if (result.rows.isEmpty()) {
                println("This result set is empty.")
            } else {
                println(formatResultAsTable(result))
            }
            println("Returned ${result.rowCount} rows. ${result.summary}")
        }
    } catch (e: Exception) {
        println("❌ An error occurred: ${e.message}")
    }
}

/**
 * Fetches the schema using the service and then prints a formatted version.
 */
private fun showSchema(db: KuzuDBService) {
    try {
        println("\nFetching database schema...")
        val schema = db.getSchema()
        println(formatSchema(schema))
    } catch (e: Exception) {
        println("❌ An error occurred while fetching schema: ${e.message}")
    }
}

/**
 * Formats the Schema data class into a readable string.
 */
fun formatSchema(schema: Schema): String {
    val sb = StringBuilder()
    sb.appendLine("--- Node Tables ---")
    if (schema.nodeTables.isEmpty()) {
        sb.appendLine("  (No node tables found)")
    } else {
        schema.nodeTables.forEach { table ->
            sb.appendLine("  ${table.name}:")
            table.properties.forEach { prop ->
                val pk = if (prop["primary_key"] == "true") " (PK)" else ""
                sb.appendLine("    - ${prop["name"]}: ${prop["type"]}$pk")
            }
        }
    }
    sb.appendLine("\n--- Relationship Tables ---")
    if (schema.relTables.isEmpty()) {
        sb.appendLine("  (No relationship tables found)")
    } else {
        schema.relTables.forEach { table ->
            sb.appendLine("  ${table.name} (FROM ${table.src} TO ${table.dst}):")
            table.properties.forEach { prop ->
                sb.appendLine("    - ${prop["name"]}: ${prop["type"]}")
            }
        }
    }
    return sb.toString()
}

/**
 * Formats a single query result into a pretty ASCII table string.
 * Truncates wide columns to keep the table readable.
 */
fun formatResultAsTable(result: FormattedResult): String {
    if (result.rows.isEmpty()) return "No results to display."
    val headers = result.headers
    val rows = result.rows
    val colWidths = headers.map { it.length }.toMutableList()
    for (row in rows) {
        for (i in headers.indices) {
            val cellLength = row.getOrNull(i)?.length?.coerceAtMost(80) ?: 4 // Limit width and handle NULL
            if (cellLength > colWidths[i]) {
                colWidths[i] = cellLength
            }
        }
    }
    val sb = StringBuilder()
    val separator = colWidths.joinToString(prefix = "+-", postfix = "-+", separator = "-+-") { "-".repeat(it) }
    sb.appendLine(separator)
    val headerLine = headers.mapIndexed { i, h -> h.padEnd(colWidths[i]) }.joinToString(" | ", "| ", " |")
    sb.appendLine(headerLine)
    sb.appendLine(separator)
    for (row in rows) {
        val rowLine = row.mapIndexed { i, cell ->
            val cellStr = cell ?: "NULL"
            val truncatedCell = if (cellStr.length > 80) cellStr.substring(0, 77) + "..." else cellStr
            truncatedCell.padEnd(colWidths[i])
        }.joinToString(" | ", "| ", " |")
        sb.appendLine(rowLine)
    }
    sb.appendLine(separator)
    return sb.toString()
}


fun printMenu() {
    println("Menu:")
    for (entry in Choice.entries) {
        println("${entry.value}. ${entry.description}")
    }
}

fun getChoice(): Choice {
    while (true) {
        print("Enter a number: ")
        val input = readlnOrNull()
        if (input != null) {
            val choice = Choice.fromString(input)
            if (choice != null) return choice
        }
        println("Invalid choice. Please try again.")
    }
}

