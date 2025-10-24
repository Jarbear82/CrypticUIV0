package com.tau.cryptic_ui_v0

// --- Helper Functions ---
private fun String.withBackticks(): String {
    return if (reservedWords.contains(this.uppercase())) "`$this`" else this
}

private fun formatValue(value: Any?): String {
    return when (value) {
        is String -> "'${value.replace("'", "\\'")}'" // Escape single quotes
        null -> "NULL"
        else -> value.toString()
    }
}

// --- Create Functions ---

// Create Node
suspend fun createNode(dbService: KuzuDBService, node: NodeTable) {
    val label = node.label
    val propertiesString = node.properties.joinToString(", ") {
        "${it.key.withBackticks()}: ${formatValue(it.value)}"
    }
    val query = "CREATE (n:${label.withBackticks()} {${propertiesString}})"
    dbService.executeQuery(query)
}

// Create Edge
suspend fun createEdge(dbService: KuzuDBService, edge: EdgeTable) {
    val label = edge.label
    val src = edge.src
    val dst = edge.dst

    // FIX: Read mutable property into a local variable
    val edgeProperties = edge.properties

    val propertiesString = if (edgeProperties?.isNotEmpty() == true) {
        // Use the local variable here
        "{${edgeProperties.joinToString(", ") { "${it.key.withBackticks()}: ${formatValue(it.value)}" }}}"
    } else {
        ""
    }

    val srcPk = src.primarykeyProperty
    val dstPk = dst.primarykeyProperty
    val formattedSrcPkValue = formatValue(srcPk.value)
    val formattedDstPkValue = formatValue(dstPk.value)

    val query = """
        MATCH (a:${src.label.withBackticks()}), (b:${dst.label.withBackticks()})
        WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue
        CREATE (a)-[r:${label.withBackticks()} $propertiesString]->(b)
    """.trimIndent()
    dbService.executeQuery(query)
}

// Create Node Schema
suspend fun createNodeSchema(dbService: KuzuDBService, nodeSchema: NodeSchemaCreationState) {
    val pk = nodeSchema.properties.first { it.isPrimaryKey }
    val properties = nodeSchema.properties.joinToString(", ") {
        "${it.name.withBackticks()} ${it.type}"
    }
    val query = "CREATE NODE TABLE ${nodeSchema.tableName.withBackticks()} ($properties, PRIMARY KEY (${pk.name.withBackticks()}))"
    dbService.executeQuery(query)
}

// Create Edge Schema
suspend fun createEdgeSchema(dbService: KuzuDBService, edgeSchema: EdgeSchemaCreationState) {
    val properties = if (edgeSchema.properties.isNotEmpty()) {
        ", " + edgeSchema.properties.joinToString(", ") {
            "${it.name.withBackticks()} ${it.type}"
        }
    } else {
        ""
    }
    val propertiesString = if (edgeSchema.properties.isNotEmpty()) {
        ", " + edgeSchema.properties.joinToString(", ") {
            "${it.name.withBackticks()} ${it.type}"
        }
    } else { "" }

    val fromToString = edgeSchema.connections.joinToString(", ") {
        "FROM ${it.src.withBackticks()} TO ${it.dst.withBackticks()}"
    }

    val query = "CREATE REL TABLE ${edgeSchema.tableName.withBackticks()} ($fromToString$propertiesString)"
    dbService.executeQuery(query)
}


// --- Read Functions ---

suspend fun getSchema(dbService: KuzuDBService): Schema? {
    val execResult = dbService.executeQuery("CALL SHOW_TABLES() RETURN *;")
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
                val tableInfoResult = dbService.executeQuery("CALL TABLE_INFO(\"$tableName\") RETURN *;")
                if (tableInfoResult is ExecutionResult.Success) {
                    val properties = tableInfoResult.results.first().rows.map { row ->
                        SchemaProperty(row[1].toString(), row[2].toString(), row[4] as Boolean)
                    }
                    nodeSchemaList.add(SchemaNode(tableName, properties))
                }
            }
            "REL" -> {
                val tableInfoResult = dbService.executeQuery("CALL TABLE_INFO(\"$tableName\") RETURN *;")
                val properties = if (tableInfoResult is ExecutionResult.Success) {
                    tableInfoResult.results.first().rows.map { row ->
                        SchemaProperty(row[1].toString(), row[2].toString(), isPrimaryKey = false)
                    }
                } else {
                    emptyList()
                }

                val showConnectionResult = dbService.executeQuery("CALL SHOW_CONNECTION(\"$tableName\") RETURN *;")
                if (showConnectionResult is ExecutionResult.Success) {
                    // Map all returned rows into a List<ConnectionPair>
                    val connections = showConnectionResult.results.first().rows.map { row ->
                        ConnectionPair(src = row[0] as String, dst = row[1] as String)
                    }

                    // Add only ONE SchemaEdge with the full list of connections
                    if (connections.isNotEmpty()) {
                        edgeSchemaList.add(SchemaEdge(tableName, connections, properties))
                    }
                }
            }
        }
    }
    return Schema(nodeTables = nodeSchemaList, edgeTables = edgeSchemaList)
}

suspend fun listNodes(dbService: KuzuDBService): List<NodeDisplayItem> {
    val nodes = mutableListOf<NodeDisplayItem>()
    getSchema(dbService)?.nodeTables?.forEach { table ->
        val pk = dbService.getPrimaryKey(table.label) ?: return@forEach
        val query = "MATCH (n:${table.label.withBackticks()}) RETURN n.${pk.withBackticks()}"
        val result = dbService.executeQuery(query)
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

suspend fun listEdges(dbService: KuzuDBService): List<EdgeDisplayItem> {
    val edges = mutableListOf<EdgeDisplayItem>()
    getSchema(dbService)?.edgeTables?.forEach { table ->
        // Loop over every connection pair this edge schema supports
        table.connections.forEach { connection ->
            val srcPkName = dbService.getPrimaryKey(connection.src) ?: return@forEach
            val dstPkName = dbService.getPrimaryKey(connection.dst) ?: return@forEach

            val query = "MATCH (src:${connection.src.withBackticks()})-[r:${table.label.withBackticks()}]->(dst:${connection.dst.withBackticks()}) RETURN src.${srcPkName.withBackticks()}, dst.${dstPkName.withBackticks()}"

            val result = dbService.executeQuery(query)
            if (result is ExecutionResult.Success) {
                result.results.firstOrNull()?.rows?.forEach { row ->
                    val srcNode = NodeDisplayItem(connection.src, DisplayItemProperty(srcPkName, row[0]))
                    val dstNode = NodeDisplayItem(connection.dst, DisplayItemProperty(dstPkName, row[1]))
                    edges.add(EdgeDisplayItem(label = table.label, src = srcNode, dst = dstNode))
                }
            }
        }
    }
    return edges
}

suspend fun getNode(dbService: KuzuDBService, item: NodeDisplayItem): NodeTable? {
    val pkKey = item.primarykeyProperty.key
    val pkValue = item.primarykeyProperty.value
    val formattedPkValue = formatValue(pkValue)

    val query = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue RETURN n"
    val result = dbService.executeQuery(query)

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

suspend fun getEdge(dbService: KuzuDBService, item: EdgeDisplayItem): EdgeTable? {
    val srcPk = item.src.primarykeyProperty
    val dstPk = item.dst.primarykeyProperty
    val formattedSrcPkValue = formatValue(srcPk.value)
    val formattedDstPkValue = formatValue(dstPk.value)

    val query = "MATCH (a:${item.src.label.withBackticks()})-[r:${item.label.withBackticks()}]->(b:${item.dst.label.withBackticks()}) " +
            "WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue " +
            "RETURN r LIMIT 1"
    val result = dbService.executeQuery(query)

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

suspend fun updateNodeProperties(dbService: KuzuDBService, label: String, pk: DisplayItemProperty, propertiesToSet: List<TableProperty>) {
    if (propertiesToSet.isEmpty()) return
    val pkKey = pk.key.withBackticks()
    val pkValue = formatValue(pk.value)
    val setString = propertiesToSet.joinToString(", ") {
        "n.${it.key.withBackticks()} = ${formatValue(it.value)}"
    }
    val query = "MATCH (n:${label.withBackticks()}) WHERE n.$pkKey = $pkValue SET $setString"
    dbService.executeQuery(query)
}

suspend fun updateEdgeProperties(dbService: KuzuDBService, item: EdgeDisplayItem, propertiesToSet: List<TableProperty>) {
    if (propertiesToSet.isEmpty()) return
    val srcPk = item.src.primarykeyProperty
    val dstPk = item.dst.primarykeyProperty
    val formattedSrcPkValue = formatValue(srcPk.value)
    val formattedDstPkValue = formatValue(dstPk.value)

    val setString = propertiesToSet.joinToString(", ") {
        "r.${it.key.withBackticks()} = ${formatValue(it.value)}"
    }

    val query = "MATCH (a:${item.src.label.withBackticks()})-[r:${item.label.withBackticks()}]->(b:${item.dst.label.withBackticks()}) " +
            "WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue " +
            "SET $setString"
    dbService.executeQuery(query)
}

suspend fun updateNodeSchemaAddProperty(dbService: KuzuDBService, tableName: String, propertyName: String, propertyType: String) {
    val query = "ALTER TABLE ${tableName.withBackticks()} ADD ${propertyName.withBackticks()} $propertyType"
    dbService.executeQuery(query)
}

suspend fun updateNodeSchemaDropProperty(dbService: KuzuDBService, tableName: String, propertyName: String) {
    val query = "ALTER TABLE ${tableName.withBackticks()} DROP ${propertyName.withBackticks()}"
    dbService.executeQuery(query)
}

suspend fun updateNodeSchemaRenameProperty(dbService: KuzuDBService, tableName: String, oldName: String, newName: String) {
    val query = "ALTER TABLE ${tableName.withBackticks()} RENAME ${oldName.withBackticks()} TO ${newName.withBackticks()}"
    dbService.executeQuery(query)
}

suspend fun updateNodeSchemaRenameTable(dbService: KuzuDBService, oldName: String, newName: String) {
    val query = "ALTER TABLE ${oldName.withBackticks()} RENAME TO ${newName.withBackticks()}"
    dbService.executeQuery(query)
}

suspend fun updateEdgeSchemaAddProperty(dbService: KuzuDBService, tableName: String, propertyName: String, propertyType: String) {
    val query = "ALTER TABLE ${tableName.withBackticks()} ADD ${propertyName.withBackticks()} $propertyType"
    dbService.executeQuery(query)
}

suspend fun updateEdgeSchemaDropProperty(dbService: KuzuDBService, tableName: String, propertyName: String) {
    val query = "ALTER TABLE ${tableName.withBackticks()} DROP ${propertyName.withBackticks()}"
    dbService.executeQuery(query)
}

suspend fun updateEdgeSchemaRenameProperty(dbService: KuzuDBService, tableName: String, oldName: String, newName: String) {
    val query = "ALTER TABLE ${tableName.withBackticks()} RENAME ${oldName.withBackticks()} TO ${newName.withBackticks()}"
    dbService.executeQuery(query)
}

suspend fun updateEdgeSchemaRenameTable(dbService: KuzuDBService, oldName: String, newName: String) {
    val query = "ALTER TABLE ${oldName.withBackticks()} RENAME TO ${newName.withBackticks()}"
    dbService.executeQuery(query)
}


// --- Delete Functions ---
suspend fun deleteNode(dbService: KuzuDBService, item: NodeDisplayItem) {
    val pkKey = item.primarykeyProperty.key
    val pkValue = item.primarykeyProperty.value
    val formattedPkValue = formatValue(pkValue)
    val query = "MATCH (n:${item.label.withBackticks()}) WHERE n.${pkKey.withBackticks()} = $formattedPkValue DETACH DELETE n"
    dbService.executeQuery(query)
}

suspend fun deleteEdge(dbService: KuzuDBService, item: EdgeDisplayItem) {
    val srcPk = item.src.primarykeyProperty
    val dstPk = item.dst.primarykeyProperty
    val formattedSrcPkValue = formatValue(srcPk.value)
    val formattedDstPkValue = formatValue(dstPk.value)

    val query = "MATCH (a:${item.src.label.withBackticks()})-[r:${item.label.withBackticks()}]->(b:${item.dst.label.withBackticks()}) " +
            "WHERE a.${srcPk.key.withBackticks()} = $formattedSrcPkValue AND b.${dstPk.key.withBackticks()} = $formattedDstPkValue " +
            "DELETE r"
    dbService.executeQuery(query)
}

suspend fun deleteSchemaNode(dbService: KuzuDBService, item: SchemaNode) {
    val query = "DROP TABLE ${item.label.withBackticks()}"
    dbService.executeQuery(query)
}

suspend fun deleteSchemaEdge(dbService: KuzuDBService, item: SchemaEdge) {
    val query = "DROP TABLE ${item.label.withBackticks()}"
    dbService.executeQuery(query)
}