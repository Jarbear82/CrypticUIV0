package com.tau.nexus_note.db

import app.cash.sqldelight.ColumnAdapter
import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.SchemaProperty
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Reusable Json instance for database serialization.
 */
private val dbJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}

/**
 * Adapter for `SchemaDefinition.properties_json`
 * Converts List<SchemaProperty> to/from a JSON String.
 */
val schemaPropertyAdapter = object : ColumnAdapter<List<SchemaProperty>, String> {
    override fun decode(databaseValue: String): List<SchemaProperty> {
        return dbJson.decodeFromString(ListSerializer(SchemaProperty.serializer()), databaseValue)
    }
    override fun encode(value: List<SchemaProperty>): String {
        return dbJson.encodeToString(ListSerializer(SchemaProperty.serializer()), value)
    }
}

/**
 * Adapter for `SchemaDefinition.connections_json`
 * Converts List<ConnectionPair> to/from a nullable JSON String.
 * (This includes your fix)
 */
//val connectionPairAdapter = object : ColumnAdapter<List<ConnectionPair>, String?> {
//    override fun decode(databaseValue: String?): List<ConnectionPair> {
//        return databaseValue?.let {
//            dbJson.decodeFromString(ListSerializer(ConnectionPair.serializer()), it)
//        } ?: emptyList()
//    }
//    override fun encode(value: List<ConnectionPair>): String? {
//        // Return null if the list is empty, or encode it
//        return if (value.isEmpty()) null else {
//            dbJson.encodeToString(ListSerializer(ConnectionPair.serializer()), value)
//        }
//    }
//}

val connectionPairAdapter = object : ColumnAdapter<List<ConnectionPair>, String> {
    override fun decode(databaseValue: String): List<ConnectionPair> {
        // This decode must assume non-null input, so we use your logic differently
        return dbJson.decodeFromString(ListSerializer(ConnectionPair.serializer()), databaseValue)
    }

    override fun encode(value: List<ConnectionPair>): String {
        // Must return a non-null string, handle the empty case
        return if (value.isEmpty()) "[]" else { // Encode as empty JSON array or default
            dbJson.encodeToString(ListSerializer(ConnectionPair.serializer()), value)
        }
    }
}

/**
 * Adapter for `Node.properties_json` and `Edge.properties_json`
 * Converts Map<String, String> to/from a JSON String.
 */
val stringMapAdapter = object : ColumnAdapter<Map<String, String>, String> {
    // THIS IS THE FIX: The serializer logic is moved *inside* the methods
    // to avoid the type inference error at initialization.
    // private val serializer = MapSerializer(String.serializer(), String.serializer()) // <- This was the error

    override fun decode(databaseValue: String): Map<String, String> {
        return dbJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), databaseValue)
    }
    override fun encode(value: Map<String, String>): String {
        return dbJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), value)
    }
}