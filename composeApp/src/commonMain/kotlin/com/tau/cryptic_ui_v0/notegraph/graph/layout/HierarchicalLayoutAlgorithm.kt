package com.tau.cryptic_ui_v0.notegraph.graph.layout

import com.tau.cryptic_ui_v0.notegraph.graph.HierarchicalOptions
import com.tau.cryptic_ui_v0.notegraph.graph.HierarchicalDirection
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsNode
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsEdge
import java.util.LinkedList
import java.util.Queue

/**
 * Performs a static hierarchical layout calculation.
 * This class implements the logic from vis-network.js for level assignment,
 * initial positioning, and refinement.
 */
internal class HierarchicalLayoutAlgorithm(
    private val nodes: Map<String, PhysicsNode>,
    private val edges: Map<String, PhysicsEdge>,
    private val options: HierarchicalOptions
) {
    private val strategy: HierarchicalDirectionStrategy = when (options.direction) {
        HierarchicalDirection.UD, HierarchicalDirection.DU -> VerticalStrategy(options)
        HierarchicalDirection.LR, HierarchicalDirection.RL -> HorizontalStrategy(options)
    }

    private val nodeLevels: MutableMap<String, Int> = mutableMapOf()
    private val parentMap: Map<String, List<String>>
    private val childMap: Map<String, List<String>>

    init {
        // Build adjacency maps
        val parents = mutableMapOf<String, MutableList<String>>()
        val children = mutableMapOf<String, MutableList<String>>()

        edges.values.forEach { edge ->
            children.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
            parents.getOrPut(edge.to) { mutableListOf() }.add(edge.from)
        }
        parentMap = parents
        childMap = children
    }

    companion object {
        /**
         * Main entry point for the layout.
         */
        fun calculatePositions(
            nodes: Map<String, PhysicsNode>,
            edges: Map<String, PhysicsEdge>,
            options: HierarchicalOptions
        ): Map<String, Int> {
            val algorithm = HierarchicalLayoutAlgorithm(nodes, edges, options)
            return algorithm.run()
        }
    }

    /**
     * Executes the layout steps and returns the calculated node levels.
     */
    private fun run(): Map<String, Int> {
        // 1. Assign levels
        assignLevels()

        // 2. Initial placement
        val distribution = placeNodes()

        // 3. Refine: Parent Centralization
        centralizeParents(distribution)

        return nodeLevels
    }

    /**
     * Assigns levels to all nodes using a Breadth-First Search from the roots.
     */
    private fun assignLevels() {
        val queue: Queue<Pair<String, Int>> = LinkedList()

        // Find root nodes (nodes with no incoming edges or only to self)
        nodes.keys.forEach { nodeId ->
            val parentIds = parentMap[nodeId]?.filter { it != nodeId } ?: emptyList()
            if (parentIds.isEmpty()) {
                queue.add(nodeId to 0)
                nodeLevels[nodeId] = 0
            }
        }

        // BFS to assign levels
        while (queue.isNotEmpty()) {
            val (nodeId, level) = queue.poll()

            val childrenIds = childMap[nodeId]?.filter { it != nodeId } ?: emptyList()
            childrenIds.forEach { childId ->
                if (nodes.containsKey(childId) && !nodeLevels.containsKey(childId)) {
                    nodeLevels[childId] = level + 1
                    queue.add(childId to level + 1)
                }
            }
        }

        // Handle disconnected components or cycles (assign level 0)
        nodes.keys.forEach { nodeId ->
            if (!nodeLevels.containsKey(nodeId)) {
                nodeLevels[nodeId] = 0
            }
        }
    }

    /**
     * Performs initial placement of nodes based on their level.
     */
    private fun placeNodes(): Map<Int, List<String>> {
        val distribution = mutableMapOf<Int, MutableList<String>>()
        val placedNodes = mutableSetOf<String>()

        nodeLevels.entries
            .sortedBy { it.value }
            .forEach { (nodeId, level) ->
                val node = nodes[nodeId] ?: return@forEach

                // Add to level distribution
                distribution.getOrPut(level) { mutableListOf() }.add(nodeId)

                // Fix the node's level coordinate
                strategy.fix(node, level)

                // Place the node
                if (!placedNodes.contains(nodeId)) {
                    placeNode(nodeId, level, distribution, placedNodes)
                }
            }

        return distribution
    }

    /**
     * Recursively places a node and its children.
     * This is a simplified initial placement, not the full block-shifting algorithm.
     */
    private fun placeNode(nodeId: String, level: Int, distribution: Map<Int, List<String>>, placedNodes: MutableSet<String>) {
        val node = nodes[nodeId] ?: return
        if (placedNodes.contains(nodeId)) return

        placedNodes.add(nodeId)

        val levelNodes = distribution[level] ?: return
        val pos = (levelNodes.indexOf(nodeId) - levelNodes.size / 2) * options.nodeSeparation
        strategy.setPosition(node, pos)
    }

    /**
     * Refinement step: Center parents over their children.
     * Iterates from the second-to-last level up to the root.
     */
    private fun centralizeParents(distribution: Map<Int, List<String>>) {
        if (!options.parentCentralization) return

        val maxLevel = distribution.keys.maxOrNull() ?: 0

        for (level in (maxLevel - 1) downTo 0) {
            val levelNodes = distribution[level] ?: continue
            levelNodes.forEach { nodeId ->
                val node = nodes[nodeId] ?: return@forEach
                val childrenIds = (childMap[nodeId] ?: emptyList())
                    .filter { nodes.containsKey(it) && nodeLevels[it] == level + 1 }

                if (childrenIds.isNotEmpty()) {
                    val children = childrenIds.mapNotNull { nodes[it] }

                    // Find the average position of the children
                    val avgChildPos = children.sumOf { strategy.getPosition(it).toDouble() } / children.size

                    // Set parent position
                    // In a more complex implementation, we would check for collisions here.
                    // For this simple version, we just set it.
                    strategy.setPosition(node, avgChildPos.toFloat())
                }
            }
        }
    }
}
