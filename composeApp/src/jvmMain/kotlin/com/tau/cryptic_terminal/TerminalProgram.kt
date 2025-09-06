package com.tau.cryptic_terminal

import kotlinx.coroutines.runBlocking
import java.util.LinkedHashMap
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

fun terminalProgram() = runBlocking {
    println("Welcome to Cryptic Terminal!")

    val db = KuzuDBService()
    try {
        db.initialize() // Initializes an in-memory database by default
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

private suspend fun handleExecution(db: KuzuDBService, query: String) {
    try {
        println("\nExecuting: $query")
        when (val executionResult = db.executeQuery(query)) {
            is ExecutionResult.Success -> {
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
            }
            is ExecutionResult.Error -> {
                println("❌ An error occurred: ${executionResult.message}")
            }
        }
    } catch (e: Exception) {
        println("❌ An unexpected error occurred: ${e.message}")
    }
}

private suspend fun showSchema(db: KuzuDBService) {
    try {
        println("\nFetching database schema...")
        val schema = db.getSchema()
        if (schema != null) {
            println(formatSchema(schema))
        } else {
            println("Could not retrieve schema.")
        }
    } catch (e: Exception) {
        println("❌ An error occurred while fetching schema: ${e.message}")
    }
}

fun formatSchema(schema: Schema): String {
    val sb = StringBuilder()
    sb.appendLine("--- Node Tables ---")
    if (schema.nodeTables.isEmpty()) {
        sb.appendLine("  (No node tables found)")
    } else {
        schema.nodeTables.forEach { table ->
            sb.appendLine("  ${table.id}.${table.name}:")
            table.properties.forEach { (name, type) ->
                sb.appendLine("    - $name: $type")
            }
        }
    }
    sb.appendLine("\n--- Relationship Tables ---")
    if (schema.relTables.isEmpty()) {
        sb.appendLine("  (No relationship tables found)")
    } else {
        schema.relTables.forEach { table ->
            sb.appendLine("  ${table.id}. ${table.name} (FROM ${table.src} TO ${table.dst}):")
            if (table.properties.isEmpty()) {
                sb.appendLine("    (No properties)")
            } else {
                table.properties.forEach { (name, type) ->
                    sb.appendLine("    - $name: $type")
                }
            }
        }
    }
    return sb.toString()
}

fun formatResultAsTable(result: FormattedResult): String {
    if (result.rows.isEmpty()) return "No results to display."

    val rowsAsStrings = result.rows.map { row ->
        row.map { item ->
            when (item) {
                is Map<*, *> -> {
                    if (item.containsKey("_nodes") && item.containsKey("_rels")) {
                        // It's a Recursive Relationship (Path)
                        "PATH(nodes=${item["_nodes"]}, rels=${item["_rels"]})"
                    } else if (item.containsKey("_id") && item.containsKey("_label") && !item.containsKey("_src")) {
                        // It's a Node
                        val id = item["_id"]
                        val label = item["_label"]
                        val props = item.filterKeys { it != "_id" && it != "_label" }
                        "NODE(id=$id, lbl='$label', props=$props)"
                    } else if (item.containsKey("_src") && item.containsKey("_dst") && item.containsKey("_label")) {
                        // It's a Relationship
                        val src = item["_src"]
                        val dst = item["_dst"]
                        val label = item["_label"]
                        val props = item.filterKeys { it !in listOf("_id", "_src", "_dst", "_label") }
                        "REL($src)-[lbl='$label', props=$props]->($dst)"
                    } else {
                        item.toString() // Fallback for other map-like structures (STRUCT, MAP)
                    }
                }
                else -> item?.toString() ?: "NULL"
            }
        }
    }

    val headers = result.headers
    val colWidths = headers.map { it.length }.toMutableList()
    for (row in rowsAsStrings) {
        for (i in headers.indices) {
            val cellLength = row.getOrNull(i)?.length?.coerceAtMost(80) ?: 4
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

    for (row in rowsAsStrings) {
        val rowLine = row.mapIndexed { i, cell ->
            val truncatedCell = if (cell.length > 80) cell.substring(0, 77) + "..." else cell
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