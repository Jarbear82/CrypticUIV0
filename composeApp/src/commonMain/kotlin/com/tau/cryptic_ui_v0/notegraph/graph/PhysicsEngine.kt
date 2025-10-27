package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.ui.geometry.Offset
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.sqrt

// Internal representation for physics simulation
data class PhysicsNode(
    val id: String,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val isFixed: Boolean = false, // Assuming nodes are not fixed by default
    val mass: Float = 1f          // Default mass
)

data class PhysicsEdge(
    val id: String,
    val from: String,
    val to: String,
    val springLength: Float = 150f, // Default spring length
    val springConstant: Float = 0.04f // Default spring constant
)


class PhysicsEngine(
    initialNodes: List<NodeDisplayItem>,
    initialEdges: List<EdgeDisplayItem>,
    private val width: Float,
    private val height: Float
) {
    // Make nodes internal to allow access from extension function in the same module
    internal val nodes = mutableMapOf<String, PhysicsNode>()
    private val edges = mutableMapOf<String, PhysicsEdge>()

    // Physics parameters (can be adjusted)
    private val repulsionConstant: Float = -800f // Adjusted for potentially stronger repulsion
    private val damping: Float = 0.09f
    private val timeStep: Float = 0.5f
    private val minVelocity: Float = 0.05f // Threshold to consider stabilized
    private val selfReferenceSpringLength: Float = 80f // Smaller length for self-loops

    init {
        updateData(initialNodes, initialEdges)
    }

    fun updateData(newNodes: List<NodeDisplayItem>, newEdges: List<EdgeDisplayItem>) {
        val currentNodes = nodes.keys.toSet()
        val newNodesMap = newNodes.associateBy { it.id() }
        val newNodesIds = newNodesMap.keys

        // Remove nodes no longer present
        (currentNodes - newNodesIds).forEach { nodes.remove(it) }

        // Add or update nodes
        newNodesMap.values.forEach { displayNode ->
            val id = displayNode.id()
            if (nodes.containsKey(id)) {
                // Optionally update properties like fixed status if they can change
                // nodes[id]?.isFixed = displayNode.fixed // Example
            } else {
                // Add new node, potentially with random initial position if needed
                nodes[id] = PhysicsNode(
                    id = id,
                    // Simple random placement within bounds for new nodes
                    x = (kotlin.random.Random.nextFloat() - 0.5f) * width * 0.5f,
                    y = (kotlin.random.Random.nextFloat() - 0.5f) * height * 0.5f
                    // isFixed = displayNode.fixed // Example
                )
            }
        }

        // --- Update Edges ---
        val currentEdges = edges.keys.toSet()
        val newEdgesMap = newEdges.associateBy { it.id() } // Assuming edges have unique IDs, construct if needed
        val newEdgesIds = newEdgesMap.keys

        // Remove edges no longer present
        (currentEdges - newEdgesIds).forEach { edges.remove(it) }

        // Add or update edges
        newEdgesMap.values.forEach { displayEdge ->
            val edgeId = displayEdge.id()
            if (!edges.containsKey(edgeId)) {
                val fromId = displayEdge.src.id()
                val toId = displayEdge.dst.id()
                // Only add if both nodes exist
                if (nodes.containsKey(fromId) && nodes.containsKey(toId)) {
                    edges[edgeId] = PhysicsEdge(
                        id = edgeId,
                        from = fromId,
                        to = toId
                        // Set spring length/constant based on options if available
                    )
                }
            }
        }
    }


    fun getNodePosition(id: String): Offset? {
        return nodes[id]?.let { Offset(it.x, it.y) }
    }

    fun updateNodePosition(id: String, x: Float, y: Float) {
        nodes[id]?.apply {
            this.x = x
            this.y = y
            // Reset velocity when manually moved
            this.vx = 0f
            this.vy = 0f
        }
    }

    fun isStable(): Boolean {
        return nodes.values.all { sqrt(it.vx * it.vx + it.vy * it.vy) < minVelocity }
    }

    // --- Simulation Step ---
    suspend fun simulateStep(): Boolean {
        if (!coroutineContext.isActive) return true // Stop if coroutine is cancelled

        val forces = mutableMapOf<String, Offset>()
        nodes.values.forEach { forces[it.id] = Offset.Zero }

        // 1. Calculate Repulsion Forces (Barnes-Hut approximation omitted for simplicity)
        val nodeValues = nodes.values.toList()
        for (i in nodeValues.indices) {
            for (j in i + 1 until nodeValues.size) {
                val node1 = nodeValues[i]
                val node2 = nodeValues[j]

                val dx = node2.x - node1.x
                val dy = node2.y - node1.y
                var distanceSquared = dx * dx + dy * dy
                if (distanceSquared < 1f) distanceSquared = 1f // Avoid division by zero
                val distance = sqrt(distanceSquared)

                // Repulsion force: F = k_rep / distance^2
                // Force components: Fx = F * (dx / distance), Fy = F * (dy / distance)
                // Simplified: Fx = k_rep * dx / distance^3, Fy = k_rep * dy / distance^3
                val force = repulsionConstant / distanceSquared // Adjusted force calculation
                val fx = force * dx / distance
                val fy = force * dy / distance

                if (!node1.isFixed) {
                    forces[node1.id] = forces.getValue(node1.id) - Offset(fx, fy)
                }
                if (!node2.isFixed) {
                    forces[node2.id] = forces.getValue(node2.id) + Offset(fx, fy)
                }
            }
        }


        // 2. Calculate Spring Forces for Edges
        edges.values.forEach { edge ->
            val fromNode = nodes[edge.from]
            val toNode = nodes[edge.to]

            if (fromNode != null && toNode != null) {
                val dx = toNode.x - fromNode.x
                val dy = toNode.y - fromNode.y
                var distance = sqrt(dx * dx + dy * dy)
                distance = max(0.1f, distance) // Avoid division by zero


                val targetLength = if (fromNode.id == toNode.id) selfReferenceSpringLength else edge.springLength
                val displacement = distance - targetLength
                val force = edge.springConstant * displacement

                val fx = (force * dx / distance)
                val fy = (force * dy / distance)


                if (!fromNode.isFixed) {
                    forces[fromNode.id] = forces.getValue(fromNode.id) + Offset(fx, fy)
                }
                if (!toNode.isFixed) {
                    forces[toNode.id] = forces.getValue(toNode.id) - Offset(fx, fy)
                }
            }
        }

        // 3. Update Velocities and Positions
        var maxVelocity = 0f
        nodes.values.forEach { node ->
            if (!node.isFixed) {
                val force = forces.getValue(node.id)

                // Verlet integration might be more stable, but Euler is simpler for now
                // Acceleration: a = F / m
                val ax = force.x / node.mass
                val ay = force.y / node.mass

                // Update velocity: v = (v + a * dt) * damping
                node.vx = (node.vx + ax * timeStep) * (1f - damping)
                node.vy = (node.vy + ay * timeStep) * (1f - damping)


                // Update position: x = x + v * dt
                node.x += node.vx * timeStep
                node.y += node.vy * timeStep

                // Keep nodes within bounds (simple implementation) - Optional
                // Use coerceIn which is cleaner
                // node.x = node.x.coerceIn(-width / 2f, width / 2f)
                // node.y = node.y.coerceIn(-height / 2f, height / 2f)

                val velocity = sqrt(node.vx * node.vx + node.vy * node.vy)
                if (velocity > maxVelocity) {
                    maxVelocity = velocity
                }
            } else {
                // Ensure fixed nodes have zero velocity
                node.vx = 0f
                node.vy = 0f
            }
        }
        // Return true if stabilized
        return maxVelocity < minVelocity
    }
}

// Helper extension to get a stable ID string for map keys
fun NodeDisplayItem.id(): String = "${this.label}_${this.primarykeyProperty.value?.toString()}"
fun EdgeDisplayItem.id(): String = "${this.src.id()}_${this.label}_${this.dst.id()}"