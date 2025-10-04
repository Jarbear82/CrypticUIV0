package com.tau.cryptic_ui_v0.viewmodels

import androidx.compose.runtime.mutableStateOf
import com.tau.cryptic_ui_v0.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QueryViewModel(
    private val repository: KuzuRepository,
    private val viewModelScope: CoroutineScope,
    private val metadataViewModel: MetadataViewModel
) {

    private val _query = mutableStateOf("")
    val query = _query

    private val _queryResult = MutableStateFlow<ExecutionResult?>(null)
    val queryResult = _queryResult.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun executeQuery() {
        viewModelScope.launch {
            metadataViewModel.clearNodeList()
            metadataViewModel.clearEdgeList()
            val result = repository.executeQuery(query.value)
            _queryResult.value = result
            if (result !is ExecutionResult.Success) return@launch

            val tempNodeMap: Map<String, NodeDisplayItem>

            // First Pass: Collect all unique NodeValues from the result
            println("First Pass: Find all nodes")
            val nodeValues = mutableSetOf<NodeValue>()
            fun findNodeValues(value: Any?) {
                when (value) {
                    is NodeValue -> nodeValues.add(value)
                    is RecursiveRelValue -> value.nodes.forEach(::findNodeValues)
                    is List<*> -> value.forEach(::findNodeValues)
                }
            }
            result.results.forEach { formattedResult ->
                formattedResult.rows.forEach { row ->
                    row.forEach(::findNodeValues)
                }
            }

            // Process all nodes concurrently and wait for them to finish
            println("Processing ${nodeValues.size} nodes...")
            val nodeItems = async {
                nodeValues.map { nodeValue ->
                    async {
                        val pkName = repository.getPrimaryKey(nodeValue.label) ?: "_id"
                        val pkValue = nodeValue.properties[pkName]
                        val nodeItem = NodeDisplayItem(
                            label = nodeValue.label,
                            primarykeyProperty = DisplayItemProperty(key = pkName, value = pkValue)
                        )
                        // Return a pair of the node's ID and the created item
                        nodeValue.id to nodeItem
                    }
                }.awaitAll()
            }.await()
            // Create the map after all nodes have been processed
            tempNodeMap = nodeItems.toMap()
            metadataViewModel.addNodes(tempNodeMap.values.toSet())

            // Second Pass: Now that tempNodeMap is populated, find all relationships
            println("Second Pass: Find all relationships")

            val newRels = mutableSetOf<RelDisplayItem>()
            fun findRelData(value: Any?) {
                when (value) {
                    is RelValue -> {
                        val srcNode = tempNodeMap[value.src]
                        val dstNode = tempNodeMap[value.dst]
                        if (srcNode != null && dstNode != null) {
                            newRels.add(
                                RelDisplayItem(
                                    label = value.label,
                                    src = srcNode,
                                    dst = dstNode
                                )
                            )
                        }
                    }
                    is RecursiveRelValue -> value.rels.forEach(::findRelData)
                    is List<*> -> value.forEach(::findRelData)
                }
            }

            // TODO: If no nodes were returned and rels were, retrieve their acompanying nodes

            result.results.forEach { formattedResult ->
                formattedResult.rows.forEach { row ->
                    row.forEach(::findRelData)
                }
            }

            metadataViewModel.addRels(newRels)
        }
    }

    fun clearQueryResult() {
        _queryResult.value = null
    }
}