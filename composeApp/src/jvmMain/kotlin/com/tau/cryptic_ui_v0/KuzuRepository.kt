package com.tau.cryptic_ui_v0

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KuzuRepository {
    private val dbService = KuzuDBService()

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            dbService.initialize()
        }
    }

    fun getDBMetaData(): DBMetaData {
        return dbService.getDBMetaData()
    }

    fun close() {
        dbService.close()
    }

    suspend fun executeQuery(query: String): ExecutionResult {
        return dbService.executeQuery(query)
    }

    suspend fun getPrimaryKey(tableName: String): String? {
        return dbService.getPrimaryKey(tableName)
    }
}