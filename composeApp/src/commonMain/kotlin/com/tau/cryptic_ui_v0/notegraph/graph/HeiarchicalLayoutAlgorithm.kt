package com.tau.cryptic_ui_v0.notegraph.graph

import kotlin.math.max

/**
 * A basic algorithm for calculating static hierarchical node positions.
 */
object HierarchicalLayoutAlgorithm {

    /**
     * Calculates positions and assigns them directly to the PhysicsNode objects.
     * Returns a map of [NodeId, Level] for use by the physics engine.
     */
    fun calculatePositions(
        nodes: MutableMap<String, PhysicsNode>,
        edges: Map<String, PhysicsEdge>,
        options: HierarchicalOptions
    ): Map<String, Int> {

        val nodeLevels = mutableMapOf<String, Int>()
        val nodesByLevel = mutableMapOf<Int, MutableList<String>>()
        val edgeLookup = edges.values.groupBy { it.from }

        // 1. Find root nodes (nodes with no incoming edges)
        val allNodeIds = nodes.keys
        val targetNodeIds = edges.values.map { it.to }.toSet()
        val rootNodeIds = (allNodeIds - targetNodeIds).toMutableList()

        // Handle cycle graphs: if no roots found, pick one node
        if (rootNodeIds.isEmpty() && allNodeIds.isNotEmpty()) {
            rootNodeIds.add(allNodeIds.first())
        }

        // 2. Assign levels via Breadth-First Search (BFS)
        val queue = ArrayDeque<Pair<String, Int>>() // (nodeId, level)
        rootNodeIds.forEach {
            queue.add(it to 0)
            nodeLevels[it] = 0
        }

        var maxLevel = 0
        while (queue.isNotEmpty()) {
            val (nodeId, level) = queue.removeFirst()

            // Update max level
            maxLevel = max(maxLevel, level)

            // Add to nodesByLevel map
            nodesByLevel.getOrPut(level) { mutableListOf() }.add(nodeId)

            // Find children
            edgeLookup[nodeId]?.forEach { edge ->
                val childId = edge.to
                if (!nodeLevels.containsKey(childId)) { // Visit only once
                    nodeLevels[childId] = level + 1
                    queue.add(childId to level + 1)
                }
            }
        }

        // Add any unvisited nodes (disconnected components) to level 0
        allNodeIds.forEach {
            if (!nodeLevels.containsKey(it)) {
                nodeLevels[it] = 0
                nodesByLevel.getOrPut(0) { mutableListOf() }.add(it)
            }
        }

        // 3. Assign X, Y coordinates
        val (levelSeparation, nodeSeparation) = when (options.direction) {
            HierarchicalDirection.UD -> options.levelSeparation to options.nodeSeparation
            HierarchicalDirection.DU -> -options.levelSeparation to options.nodeSeparation
            HierarchicalDirection.LR -> options.nodeSeparation to options.levelSeparation
            HierarchicalDirection.RL -> -options.nodeSeparation to options.levelSeparation
        }

        for (level in 0..maxLevel) {
            val nodesInLevel = nodesByLevel[level] ?: continue
            val levelWidth = (nodesInLevel.size - 1) * nodeSeparation
            val startX = -levelWidth / 2f

            nodesInLevel.forEachIndexed { index, nodeId ->
                val node = nodes[nodeId] ?: continue

                val (x, y) = when (options.direction) {
                    HierarchicalDirection.UD, HierarchicalDirection.DU -> {
                        (startX + index * nodeSeparation) to (level * levelSeparation)
                    }
                    HierarchicalDirection.LR, HierarchicalDirection.RL -> {
                        (level * levelSeparation) to (startX + index * nodeSeparation)
                    }
                }

                // Set position directly
                node.x = x
                node.y = y
                node.vx = 0f
                node.vy = 0f
            }
        }

        return nodeLevels
    }
}