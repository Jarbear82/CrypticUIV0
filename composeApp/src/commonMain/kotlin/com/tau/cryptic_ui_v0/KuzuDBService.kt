package com.tau.cryptic_ui_v0

/**
 * Expected service for KuzuDB interactions.
 */
expect class KuzuDBService() {

    /**
     * Initializes the database connection.
     */
    fun initialize(directoryPath: String? = null)

    /**
     * Retrieves metadata about the current database.
     */
    fun getDBMetaData(): DBMetaData

    /**
     * Closes the database connection.
     */
    fun close()

    fun isInitialized(): Boolean

    /**
     * Executes a Cypher query and returns the result.
     */
    suspend fun executeQuery(query: String): ExecutionResult

    /**
     * Finds the primary key for a given table.
     */
    suspend fun getPrimaryKey(tableName: String): String?
}