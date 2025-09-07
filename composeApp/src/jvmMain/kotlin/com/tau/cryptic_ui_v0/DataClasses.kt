package com.tau.cryptic_ui_v0

data class DisplayItem(
    val id: String,
    val label: String,
    val primaryKey: String,
    val properties: Map<String, Any?> = emptyMap()
)