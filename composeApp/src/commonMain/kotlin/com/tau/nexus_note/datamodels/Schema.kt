package com.tau.nexus_note.datamodels

import kotlinx.serialization.Serializable

/**
 * Defines the constant string values for supported property types in a schema.
 */
object SchemaPropertyTypes {
    const val TEXT = "Text"
    const val LONG_TEXT = "LongText"
    const val IMAGE = "Image"
    const val AUDIO = "Audio"
    const val DATE = "Date"
    const val NUMBER = "Number"
    /**
     * This new type signifies that the property's value
     * should be the ID of an Edge.
     */
    const val EDGE_REF = "EdgeRef"

    /**
     * Returns the list of all standard, user-selectable types.
     */
    fun getCreatableTypes(): List<String> {
        return listOf(TEXT, LONG_TEXT, IMAGE, AUDIO, DATE, NUMBER, EDGE_REF)
    }
}


/**
 * Represents a user-defined property within a schema.
 * This is serialized to/from JSON.
 * @param name The name of the property (e.g., "Description", "Due Date").
 * @param type The data type for the UI (e.g., "Text", "Image", "Date").
 */
@Serializable
data class SchemaProperty(
    val name: String,
    val type: String,
    val isDisplayProperty: Boolean = false // New: Marks this as the one to show in lists
)

/**
 * Represents a connection pair for an edge schema.
 * @param src The name of the source node schema (e.t., "Person").
 * @param dst The name of the destination node schema (e.g., "Location").
 */
@Serializable // Also serializable for storing in SchemaDefinition properties
data class ConnectionPair(
    val src: String,
    val dst: String
)

/**
 * UI-facing model for a schema definition (either Node or Edge).
 * This is built from the 'SchemaDefinition' table.
 * @param id The unique ID from the 'SchemaDefinition' table.
 * @param type "NODE" or "EDGE".
 * @param name The name of the schema (e.g., "Person", "KNOWS").
 * @param properties The list of user-defined properties.
 * @param connections For EDGE schemas, the list of allowed connections.
 */
data class SchemaDefinitionItem(
    val id: Long,
    val type: String,
    val name: String,
    val properties: List<SchemaProperty>,
    val connections: List<ConnectionPair>? = null
)