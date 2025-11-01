package com.tau.cryptic_ui_v0.notegraph.graph.layout

import com.tau.cryptic_ui_v0.notegraph.graph.physics.Node
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Edge
import kotlin.Int.Companion.MAX_VALUE

/**
 * Implements the Floyd-Warshall all-pairs shortest path algorithm.
 * This is used by KamadaKawai to determine the ideal distance between nodes.
 *
 * This version is updated to work with Node and Edge from PhysicsInterfaces.kt
 */
internal class FloydWarshall {

    /**
     * Calculates the shortest path distances between all nodes in the graph.
     *
     * @param nodes The map of nodes in the graph.
     * @param edges The map of edges in the graph.
     * @return A map where [fromId][toId] = distance.
     */
    fun getDistances(
        nodes: Map<String, Node>,
        edges: Map<String, Edge>
    ): Map<String, Map<String, Int>> {
        val nodeIds = nodes.keys.toList()
        val nodeCount = nodeIds.size
        // Map NodeId to its index in the 'nodeIds' list for matrix operations
        val nodeIndexMap = nodeIds.withIndex().associate { (index, id) -> id to index }

        // Initialize distance matrix
        // Using Int.MAX_VALUE / 2 to prevent overflow during addition
        val maxDist = MAX_VALUE / 2
        val dist = Array(nodeCount) { IntArray(nodeCount) { maxDist } }

        for (i in 0 until nodeCount) {
            dist[i][i] = 0
        }

        // Add initial edge weights
        for (edge in edges.values) {
            val fromIndex = nodeIndexMap[edge.fromId]
            val toIndex = nodeIndexMap[edge.toId]

            if (fromIndex != null && toIndex != null) {
                // KamadaKawai uses shortest path *length*, so we use 1
                val weight = 1
                dist[fromIndex][toIndex] = weight
                dist[toIndex][fromIndex] = weight // Assuming undirected graph
            }
        }

        // Floyd-Warshall algorithm
        for (k in 0 until nodeCount) {
            for (i in 0 until nodeCount) {
                for (j in 0 until nodeCount) {
                    val newDist = dist[i][k] + dist[k][j]
                    if (newDist < dist[i][j]) {
                        dist[i][j] = newDist
                    }
                }
            }
        }

        // Convert the matrix back to a Map<String, Map<String, Int>>
        val distanceMap = mutableMapOf<String, Map<String, Int>>()
        for (i in 0 until nodeCount) {
            val fromId = nodeIds[i]
            val rowMap = mutableMapOf<String, Int>()
            for (j in 0 until nodeCount) {
                val toId = nodeIds[j]
                val finalDist = if (dist[i][j] >= maxDist) MAX_VALUE else dist[i][j]
                rowMap[toId] = finalDist
            }
            distanceMap[fromId] = rowMap
        }
        return distanceMap
    }
}
