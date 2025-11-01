package com.tau.cryptic_ui_v0

import com.tau.cryptic_ui_v0.db.AppDatabase
expect class SqliteDbService() {
    val database: AppDatabase

    /**
     * Initializes the .sqlite file at the given path.
     * This will also create the companion .media directory.
     */
    fun initialize(path: String)
    fun close()
}