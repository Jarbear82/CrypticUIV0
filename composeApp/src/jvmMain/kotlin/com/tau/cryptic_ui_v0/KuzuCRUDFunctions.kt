package com.tau.cryptic_ui_v0

import kotlinx.coroutines.launch

// --- Helper Functions ---
private fun String.withBackticks(): String {
    return if (reservedWords.contains(this.uppercase())) "`$this`" else this
}

private fun formatPkValue(value: Any?): String {
    return if (value is String) "'$value'" else value.toString()
}

// --- Create Functions ---

// Create Node
suspend fun createNode(repository: KuzuRepository, node: NodeTable) {
    val label = node.label
    val propertiesString = node.properties.joinToString(", ") {
        "${it.key.withBackticks()}: ${formatPkValue(it.value)}"
    }
    val query = "CREATE (n:${label.withBackticks()} {${propertiesString}})"
    repository.executeQuery(query)
}

// Create Edge
suspend fun createEdge(repository: KuzuRepository, edge: EdgeTable) {
    val label = edge.label
    val src = edge.src
    val dst = edge.dst

    val propertiesString = if (edge.properties?.isNotEmpty() == true) {
        "{${edge.properties.joinToString(", ") { "${it.key.withBackticks()}: ${formatPkValue(it.value)}" }}}"
    } else {
        ""
    }

    val srcPk = src.primarykeyProperty
    val dstPk = dst.primarykeyProperty
    val formattedSrcPkValue = formatPkValue(srcPk.value)
    val formattedDstPkValue = formatPkValue(dstPk.value)

    val query = """
        MATCH (a:${src.label.withBackticks()}), (b:${dst.label.withBackticks()})
        WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue
        CREATE (a)-[r:${label.withBackticks()} $propertiesString]->(b)
    """.trimIndent()
    repository.executeQuery(query)
}

// Create Node Schema
suspend fun createNodeSchema(repository: KuzuRepository, nodeSchema: NodeSchemaCreationState) {
    val pk = nodeSchema.properties.first { it.isPrimaryKey }
    val properties = nodeSchema.properties.joinToString(", ") {
        "${it.name.withBackticks()} ${it.type}"
    }
    val query = "CREATE NODE TABLE ${nodeSchema.tableName.withBackticks()} ($properties, PRIMARY KEY (${pk.name.withBackticks()}))"
    repository.executeQuery(query)
}

// Create Edge Schema
suspend fun createEdgeSchema(repository: KuzuRepository, edgeSchema: EdgeSchemaCreationState) {
    val properties = if (edgeSchema.properties.isNotEmpty()) {
        ", " + edgeSchema.properties.joinToString(", ") {
            "${it.name.withBackticks()} ${it.type}"
        }
    } else {
        ""
    }
    val query = "CREATE REL TABLE ${edgeSchema.tableName.withBackticks()} (FROM ${edgeSchema.srcTable!!.withBackticks()} TO ${edgeSchema.dstTable!!.withBackticks()}$properties)"
    repository.executeQuery(query)
}


// --- Read Functions ---

suspend fun getSchema(repository: KuzuRepository): Schema? {
    val execResult = repository.executeQuery("CALL SHOW_TABLES() RETURN *;")
    if (execResult !is ExecutionResult.Success) {
        println("Failed to fetch schema")
        return null
    }

    val nodeSchemaList = mutableListOf<SchemaNode>()
    val edgeSchemaList = mutableListOf<SchemaEdge>()
    val allTables = execResult.results.first().rows.map { it[1] as String to it[2] as String }

    for ((tableName, tableType) in allTables) {
        when (tableType) {
            "NODE" -> {
                val tableInfoResult = repository.executeQuery("CALL TABLE_INFO(\"$tableName\") RETURN *;")
                if (tableInfoResult is ExecutionResult.Success) {
                    val properties = tableInfoResult.results.first().rows.map { row ->
                        SchemaProperty(row[1].toString(), row[2].toString(), row[4] as Boolean)
                    }
                    nodeSchemaList.add(SchemaNode(tableName, properties))
                }
            }
            "REL" -> {
                val tableInfoResult = repository.executeQuery("CALL TABLE_INFO(\"$tableName\") RETURN *;")
                val properties = if (tableInfoResult is ExecutionResult.Success) {
                    tableInfoResult.results.first().rows.map { row ->
                        SchemaProperty(row[1].toString(), row[2].toString(), isPrimaryKey = false)
                    }
                } else {
                    emptyList()
                }

                val showConnectionResult = repository.executeQuery("CALL SHOW_CONNECTION(\"$tableName\") RETURN *;")
                if (showConnectionResult is ExecutionResult.Success) {
                    showConnectionResult.results.first().rows.forEach { row ->
                        edgeSchemaList.add(SchemaEdge(tableName, row[0] as String, row[1] as String, properties))
                    }
                }
            }
        }
    }
    return Schema(nodeTables = nodeSchemaList, edgeTables = edgeSchemaList)
}

suspend fun listNodes(repository: KuzuRepository): List<NodeDisplayItem> {
    val nodes = mutableListOf<NodeDisplayItem>()
    getSchema(repository)?.nodeTables?.forEach { table ->
        val pk = repository.getPrimaryKey(table.label) ?: return@forEach
        val query = "MATCH (n:${table.label.withBackticks()}) RETURN n.${pk.withBackticks()}"
        val result = repository.executeQuery(query)
        if (result is ExecutionResult.Success) {
            result.results.firstOrNull()?.rows?.forEach { row ->
                nodes.add(
                    NodeDisplayItem(
                        label = table.label,
                        primarykeyProperty = DisplayItemProperty(key = pk, value = row[0])
                    )
                )
            }
        }
    }
    return nodes
}

suspend fun listEdges(repository: KuzuRepository): List<EdgeDisplayItem> {
    val edges = mutableListOf<EdgeDisplayItem>()
    getSchema(repository)?.edgeTables?.forEach { table ->
        val srcPkName = repository.getPrimaryKey(table.srcLabel) ?: return@forEach
        val dstPkName = repository.getPrimaryKey(table.dstLabel) ?: return@forEach
        val query = "MATCH (src:${table.srcLabel.withBackticks()})-[r:${table.label.withBackticks()}]->(dst:${table.dstLabel.withBackticks()}) RETURN src.${srcPkName.withBackticks()}, dst.${dstPkName.withBackticks()}"
        val result = repository.executeQuery(query)
        if (result is ExecutionResult.Success) {
            result.results.firstOrNull()?.rows?.forEach { row ->
                val srcNode = NodeDisplayItem(table.srcLabel, DisplayItemProperty(srcPkName, row[0]))
                val dstNode = NodeDisplayItem(table.dstLabel, DisplayItemProperty(dstPkName, row[1]))
                edges.add(EdgeDisplayItem(label = table.label, src = srcNode, dst = dstNode))
            }
        }
    }
    return edges
}

suspend fun getNode(repository: KuzuRepository, item: NodeDisplayItem): NodeTable? {
    val pkKey = item.primarykeyProperty.key
    val pkValue = item.primarykeyProperty.value
    val formattedPkValue = formatPkValue(pkValue)

    val query = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue RETURN n"
    val result = repository.executeQuery(query)

    if (result is ExecutionResult.Success) {
        val nodeValue = result.results.firstOrNull()?.rows?.firstOrNull()?.getOrNull(0) as? NodeValue
        if (nodeValue != null) {
            val properties = nodeValue.properties.map { (key, value) ->
                TableProperty(key, value, (key == pkKey), valueChanged = false)
            }
            return NodeTable(item.label, properties, labelChanged = false, propertiesChanged = false)
        }
    }
    return null
}

suspend fun getEdge(repository: KuzuRepository, item: EdgeDisplayItem): EdgeTable? {
    val srcPk = item.src.primarykeyProperty
    val dstPk = item.dst.primarykeyProperty
    val formattedSrcPkValue = formatPkValue(srcPk.value)
    val formattedDstPkValue = formatPkValue(dstPk.value)

    val query = "MATCH (a:${item.src.label.withBackticks()})-[r:${item.label.withBackticks()}]->(b:${item.dst.label.withBackticks()}) " +
            "WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue " +
            "RETURN r LIMIT 1"
    val result = repository.executeQuery(query)

    if (result is ExecutionResult.Success) {
        val edgeValue = result.results.firstOrNull()?.rows?.firstOrNull()?.getOrNull(0) as? EdgeValue
        if (edgeValue != null) {
            val properties = edgeValue.properties.mapNotNull { (key, value) ->
                if (key.startsWith("_")) return@mapNotNull null
                TableProperty(key, value, isPrimaryKey = false, valueChanged = false)
            }
            return EdgeTable(item.label, item.src, item.dst, properties, labelChanged = false, srcChanged = false, dstChanged = false, propertiesChanged = false)
        }
    }
    return null
}


// --- Update Functions ---

// --- Delete Functions ---
suspend fun deleteNode(repository: KuzuRepository, item: NodeDisplayItem) {
    val pkKey = item.primarykeyProperty.key
    val pkValue = item.primarykeyProperty.value
    val formattedPkValue = formatPkValue(pkValue)
    val query = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue DETACH DELETE n"
    repository.executeQuery(query)
}

suspend fun deleteEdge(repository: KuzuRepository, item: EdgeDisplayItem) {
    val srcPk = item.src.primarykeyProperty
    val dstPk = item.dst.primarykeyProperty
    val formattedSrcPkValue = formatPkValue(srcPk.value)
    val formattedDstPkValue = formatPkValue(dstPk.value)

    val query = "MATCH (a:${item.src.label.withBackticks()})-[r:${item.label.withBackticks()}]->(b:${item.dst.label.withBackticks()}) " +
            "WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue " +
            "DELETE r"
    repository.executeQuery(query)
}

suspend fun deleteSchemaNode(repository: KuzuRepository, item: SchemaNode) {
    val query = "DROP TABLE ${item.label.withBackticks()}"
    repository.executeQuery(query)
}

suspend fun deleteSchemaEdge(repository: KuzuRepository, item: SchemaEdge) {
    val query = "DROP TABLE ${item.label.withBackticks()}"
    repository.executeQuery(query)
}