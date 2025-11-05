package com.tau.nexus_note

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.tau.nexus_note.db.AppDatabase
import java.io.File

actual class SqliteDbService actual constructor() {
    private var driver: SqlDriver? = null

    // Store the database in a private, nullable backing field
    private var _database: AppDatabase? = null

    // Implement the 'expect val' with a custom getter
    actual val database: AppDatabase
        get() = _database ?: throw IllegalStateException("Database not initialized. Call initialize() first.")

    actual fun initialize(path: String) {
        val isMemoryDb = path == ":memory:"
        val dbFile = File(path)
        val dbExists = if (isMemoryDb) false else dbFile.exists() // Check if the file exists

        if (!isMemoryDb) {
            val mediaDir = File(dbFile.parent, "${dbFile.nameWithoutExtension}.media")
            // Create media directory
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
        }

        // Setup driver
        driver = JdbcSqliteDriver("jdbc:sqlite:$path")

        if (!dbExists) { // Only create the schema if the database is new
            AppDatabase.Schema.create(driver!!)
        }

        _database = AppDatabase(driver!!) // Assign to the private backing field
    }

    actual fun close() {
        driver?.close()
    }
}