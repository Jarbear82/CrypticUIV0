package com.tau.cryptic_terminal
enum class Choice(val value: String, val description: String) {
    Quit("0", "Quit"),
    ExecuteQuery("1", "Execute a Cypher Query"),
    ShowSchema("2", "Show Database Schema"),
    ListNodes("3", "List Node Tables"),
    ListEdges("4", "List Relationship Tables"),
    ListNodesAndEdges("5", "List Nodes and Edges");


    companion object {
        fun fromString(value: String): Choice? {
            return entries.find { it.value == value }
        }
    }
}

fun terminalProgram() {
    println("Welcome to Cryptic Terminal!")

    val db = KuzuDBService()
    var running = true

    while (running) {
        println("\n----------------")
        printMenu()
        println("----------------")
        val choice = getChoice()

        when (choice) {
            Choice.ExecuteQuery -> {
                print("Enter your Cypher query: ")
                val query = readlnOrNull() ?: ""
                db.executeQuery(query)
            }
            Choice.ShowSchema -> showSchema(db)
            Choice.ListNodes -> showNodes(db)
            Choice.ListEdges -> showEdges(db)
            Choice.ListNodesAndEdges -> showNodesAndEdges(db)
            Choice.Quit -> {
                running = false
                println("Goodbye!")
            }
        }
    }
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
            if (choice != null) {
                return choice
            }
        }
        println("Invalid choice. Please try again.")
    }
}

fun showSchema(db: KuzuDBService) {
    val result = db.executeQuery("CALL SHOW_TABLES() RETURN *")
    println(result)
}

fun showNodes(db: KuzuDBService) {
    val result = db.executeQuery("MATCH (n) RETURN n")
    println(result)
}

fun showEdges(db: KuzuDBService) {
    val result = db.executeQuery("MATCH (n)-[r]->(m) RETURN n")
    println(result)
}

fun showNodesAndEdges(db: KuzuDBService) {
    val result = db.executeQuery("MATCH (n)-[r]->(m) RETURN n, m, r")
    println(result)
}