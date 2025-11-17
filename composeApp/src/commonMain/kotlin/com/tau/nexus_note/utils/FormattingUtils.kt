package com.tau.nexus_note.utils

/**
 * Formats a string to PascalCase.
 * Removes all non-alphanumeric characters and capitalizes the first letter of each "word".
 * "my new node" -> "MyNewNode"
 * "my-new-node" -> "MyNewNode"
 * "my_new_node" -> "MyNewNode"
 */
fun String.toPascalCase(): String {
    val words = this.split(Regex("[^a-zA-Z0-9]")).filter { it.isNotBlank() }
    return words.joinToString("") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

/**
 * Formats a string to camelCase.
 * "my property" -> "myProperty"
 */
fun String.toCamelCase(): String {
    val pascal = this.toPascalCase()
    return pascal.replaceFirstChar { it.lowercase() }
}

/**
 * Formats a string to SCREAMING_SNAKE_CASE.
 * "my new edge" -> "MY_NEW_EDGE"
 * "my-new-edge" -> "MY_NEW_EDGE"
 */
fun String.toScreamingSnakeCase(): String {
    val words = this.split(Regex("[^a-zA-Z0-9]")).filter { it.isNotBlank() }
    return words.joinToString("_") { it.uppercase() }
}