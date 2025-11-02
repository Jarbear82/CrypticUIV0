package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tau.cryptic_ui_v0.GraphNode
import com.tau.cryptic_ui_v0.GraphEdge
import kotlin.math.max
import kotlin.math.sqrt

// UPDATED: Constructor is now parameterless
class PhysicsEngine() {

    /**
     * Updates the positions and velocities of all nodes based on physics.
     * @param nodes The current map of nodes.
     * @param edges The list of edges.
     * @param options The *current* physics options from the settings UI.
     * @param dt The time delta (e.g., 16ms).
     * @return A new map of updated nodes.
     */
    // UPDATED: 'options' is now passed into the update method
    fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
        options: PhysicsOptions,
        dt: Float
    ): Map<Long, GraphNode> {
        if (nodes.isEmpty()) return emptyMap()

        val forces = mutableMapOf<Long, Offset>()
        // Create copies to modify
        val newNodes = nodes.mapValues { (_, node) -> node.copy() }

        // 1. Initialize forces
        for (node in newNodes.values) {
            forces[node.id] = Offset.Zero
        }

        // 2. Apply forces
        // 2a. Gravity (pull to center 0,0)
        for (node in newNodes.values) {
            // Do not apply gravity to fixed (dragged) nodes
            if (node.isFixed) continue

            // UPDATED: Using 'options' parameter
            val gravityForce = -node.pos * options.gravity * node.mass
            forces[node.id] = forces[node.id]!! + gravityForce
        }

        // --- MODIFIED: 2b. Repulsion (Barnes-Hut Optimization) ---
        // First, determine the boundaries of all nodes
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (node in newNodes.values) {
            if (node.pos.x < minX) minX = node.pos.x
            if (node.pos.x > maxX) maxX = node.pos.x
            if (node.pos.y < minY) minY = node.pos.y
            if (node.pos.y > maxY) maxY = node.pos.y
        }
        // Add padding to prevent nodes from being exactly on the edge
        val width = (maxX - minX) * 1.2f + 100f // +100f for empty graphs
        val height = (maxY - minY) * 1.2f + 100f
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val boundary = Rect(centerX - width/2, centerY - height/2, centerX + width/2, centerY + height/2)

        // Build the QuadTree
        val quadTree = QuadTree(boundary)
        for (node in newNodes.values) {
            quadTree.insert(node)
        }

        // Calculate repulsion force for each node
        for (node in newNodes.values) {
            // Do not apply repulsion to fixed nodes (but they still repel others)
            if (node.isFixed) continue

            // UPDATED: Using 'options' parameter
            val repulsionForce = quadTree.applyRepulsion(node, options, options.barnesHutTheta)
            forces[node.id] = forces[node.id]!! + repulsionForce
        }
        // --- END MODIFICATION (Replaced O(n^2) loop) ---


        // 2c. Spring (from edges) (Hooke's Law)
        for (edge in edges) {
            val nodeA = newNodes[edge.sourceId]
            val nodeB = newNodes[edge.targetId]

            if (nodeA != null && nodeB != null) {
                // Do not apply spring forces if *either* node is fixed
                if (nodeA.isFixed || nodeB.isFixed) continue

                val delta = nodeB.pos - nodeA.pos
                val dist = delta.getDistance()
                if (dist == 0f) continue

                // The "ideal" length of the spring is the sum of radii + a buffer
                // UPDATED: Using 'options' parameter
                val idealLength = nodeA.radius + nodeB.radius + (options.minDistance * 5)

                val displacement = dist - idealLength
                // UPDATED: Using 'options' parameter
                val springForce = delta.normalized() * displacement * options.spring * edge.strength

                forces[nodeA.id] = forces[nodeA.id]!! + springForce
                forces[nodeB.id] = forces[nodeB.id]!! - springForce
            }
        }

        // --- ADDED: 3. Calculate ForceAtlas2 Adaptive Speed (Swinging & Traction) ---
        var globalSwinging = 0f
        var globalTraction = 0f

        for (node in newNodes.values) {
            if (node.isFixed) continue

            val currentForce = forces[node.id]!!

            // 3a. Calculate Swinging (how much the force vector changed direction)
            val swingingVector = currentForce - node.oldForce
            node.swinging = swingingVector.getDistance()

            // 3b. Calculate Traction (how much force is in a consistent direction)
            val tractionVector = currentForce + node.oldForce
            node.traction = tractionVector.getDistance() / 2f

            // 3c. Sum global values (weighted by mass, which is deg+1)
            globalSwinging += node.mass * node.swinging
            globalTraction += node.mass * node.traction

            // 3d. Store current force for next frame
            node.oldForce = currentForce
        }

        // 3e. Calculate Global Speed
        // This is the "adaptive cooling" part from FA2 paper
        val globalSpeed = if (globalSwinging > 0) {
            // UPDATED: Using 'options' parameter
            options.tolerance * globalTraction / globalSwinging
        } else {
            0.1f // Default speed if no movement
        }.coerceIn(0.01f, 10f) // Add min/max caps


        // 4. Update velocities and positions (Euler integration)
        for (node in newNodes.values) {
            // If node is fixed, set velocity to zero and skip position update
            if (node.isFixed) {
                node.vel = Offset.Zero
                continue
            }

            val force = forces[node.id]!!

            // F = ma -> a = F/m
            val acceleration = force / node.mass

            // v = v0 + at
            var newVel = node.vel + (acceleration * dt)

            // Apply damping
            // UPDATED: Using 'options' parameter
            newVel *= options.damping

            // --- MODIFIED: Apply FA2 Adaptive Speed ---
            // 4a. Calculate Local Speed (slows down swinging nodes)
            val localSpeed = if (node.swinging > 0) {
                (globalSpeed / (1f + globalSpeed * sqrt(node.swinging))).coerceAtLeast(0.01f)
            } else {
                globalSpeed
            }

            // 4b. Apply displacement modulated by local speed
            // We use the localSpeed as a multiplier on the time-step (dt * localSpeed)
            val displacement = newVel * (dt * localSpeed)
            val newPos = node.pos + displacement
            // --- END MODIFICATION ---

            // Update the node in the map
            node.vel = newVel
            node.pos = newPos
        }

        return newNodes
    }

    /**
     * NEW: Runs an internal O(n^2) simulation for nodes within a single cluster.
     * This does *not* use Barnes-Hut or ForceAtlas2 adaptive speed.
     * It applies internal gravity, repulsion, and spring forces.
     *
     * @param microNodes The list of nodes *inside* this cluster.
     * @param microEdges The list of edges *within* this cluster.
     * @param clusterCenter The target center for the internal gravity.
     * @param options The current physics options.
     * @param dt The time delta.
     * @return A list of updated nodes.
     */
    fun updateInternal(
        microNodes: List<GraphNode>,
        microEdges: List<GraphEdge>,
        clusterCenter: Offset,
        options: PhysicsOptions,
        dt: Float
    ): List<GraphNode> {
        if (microNodes.isEmpty()) return emptyList()

        val forces = mutableMapOf<Long, Offset>()
        // Create copies to modify, indexed by ID for edge lookups
        val newNodes = microNodes.associateBy { it.id }.mapValues { it.value.copy() }

        // 1. Initialize forces
        for (node in newNodes.values) {
            forces[node.id] = Offset.Zero
        }

        // 2. Apply forces
        // 2a. Internal Gravity (pull to cluster center)
        for (node in newNodes.values) {
            if (node.isFixed) continue
            val gravityForce = (clusterCenter - node.pos) * options.internalGravity * node.mass
            forces[node.id] = forces[node.id]!! + gravityForce
        }

        // 2b. Repulsion (O(n^2))
        val nodePairs = newNodes.values.toList().combinations()
        for ((nodeA, nodeB) in nodePairs) {
            if (nodeA.isFixed && nodeB.isFixed) continue

            val delta = nodeB.pos - nodeA.pos
            var dist = delta.getDistance()
            if (dist == 0f) dist = 0.1f // Avoid division by zero

            // Collision detection
            val minAllowableDist = nodeA.radius + nodeB.radius + options.minDistance
            var collisionForce = Offset.Zero
            if (dist < minAllowableDist) {
                val overlap = minAllowableDist - dist
                collisionForce = -delta.normalized() * overlap * options.repulsion * 10f // Stronger force
            }

            // Standard ForceAtlas2-style repulsion (1/d)
            val repulsionForce = -delta.normalized() * (options.repulsion * nodeA.mass * nodeB.mass) / dist

            val totalForce = collisionForce + repulsionForce
            if (!nodeA.isFixed) {
                forces[nodeA.id] = forces[nodeA.id]!! - totalForce
            }
            if (!nodeB.isFixed) {
                forces[nodeB.id] = forces[nodeB.id]!! + totalForce
            }
        }

        // 2c. Spring (from micro edges)
        for (edge in microEdges) {
            val nodeA = newNodes[edge.sourceId]
            val nodeB = newNodes[edge.targetId]

            if (nodeA != null && nodeB != null) {
                if (nodeA.isFixed || nodeB.isFixed) continue

                val delta = nodeB.pos - nodeA.pos
                val dist = delta.getDistance()
                if (dist == 0f) continue

                val idealLength = nodeA.radius + nodeB.radius + (options.minDistance * 5)
                val displacement = dist - idealLength
                val springForce = delta.normalized() * displacement * options.spring * edge.strength

                forces[nodeA.id] = forces[nodeA.id]!! + springForce
                forces[nodeB.id] = forces[nodeB.id]!! - springForce
            }
        }

        // 3. Update velocities and positions (simple Euler integration)
        for (node in newNodes.values) {
            if (node.isFixed) {
                node.vel = Offset.Zero
                continue
            }

            val force = forces[node.id]!!
            val acceleration = force / node.mass
            var newVel = node.vel + (acceleration * dt)
            newVel *= options.damping // Apply damping
            val newPos = node.pos + (newVel * dt)

            node.vel = newVel
            node.pos = newPos
        }

        return newNodes.values.toList()
    }
}

// Helper for unique pairs (n=2)
// Added here for updateInternal's O(n^2) loop
private fun <T> List<T>.combinations(n: Int = 2): Sequence<Pair<T, T>> {
    if (n != 2) throw IllegalArgumentException("Only n=2 is supported for pair combinations")
    return sequence {
        for (i in 0 until this@combinations.size - 1) {
            for (j in i + 1 until this@combinations.size) {
                yield(this@combinations[i] to this@combinations[j])
            }
        }
    }
}

// Helper to normalize offset
// Made internal so updateInternal can use it
internal fun Offset.normalized(): Offset {
    val mag = this.getDistance()
    return if (mag == 0f) Offset.Zero else this / mag
}
