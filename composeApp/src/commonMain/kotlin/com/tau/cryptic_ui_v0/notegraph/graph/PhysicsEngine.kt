package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.ui.geometry.Offset
import com.tau.cryptic_ui_v0.GraphNode
import com.tau.cryptic_ui_v0.GraphEdge
import kotlin.math.max
import kotlin.math.sqrt

class PhysicsEngine(private val options: PhysicsOptions) {

    /**
     * Updates the positions and velocities of all nodes based on physics.
     * @param nodes The current map of nodes.
     * @param edges The list of edges.
     * @param dt The time delta (e.g., 16ms).
     * @return A new map of updated nodes.
     */
    fun update(
        nodes: Map<Long, GraphNode>,
        edges: List<GraphEdge>,
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
            val gravityForce = -node.pos * options.gravity * node.mass
            forces[node.id] = forces[node.id]!! + gravityForce
        }

        // 2b. Repulsion (from other nodes)
        val nodePairs = newNodes.values.toList().combinations(2)
        for ((nodeA, nodeB) in nodePairs) {
            val delta = nodeB.pos - nodeA.pos
            var dist = delta.getDistance()
            if (dist == 0f) dist = 0.1f // Avoid division by zero

            val minAllowableDist = nodeA.radius + nodeB.radius + options.minDistance

            // Strong repulsion if overlapping (collision)
            if (dist < minAllowableDist) {
                val overlap = minAllowableDist - dist
                val collisionForce = -delta.normalized() * overlap * options.repulsion * 10f
                forces[nodeA.id] = forces[nodeA.id]!! + collisionForce
                forces[nodeB.id] = forces[nodeB.id]!! - collisionForce
            }

            // Standard repulsion (Coulomb's Law)
            val repulsionForce = -delta.normalized() * (options.repulsion * nodeA.mass * nodeB.mass) / (dist * dist)
            forces[nodeA.id] = forces[nodeA.id]!! + repulsionForce
            forces[nodeB.id] = forces[nodeB.id]!! - repulsionForce
        }

        // 2c. Spring (from edges) (Hooke's Law)
        for (edge in edges) {
            val nodeA = newNodes[edge.sourceId]
            val nodeB = newNodes[edge.targetId]

            if (nodeA != null && nodeB != null) {
                val delta = nodeB.pos - nodeA.pos
                val dist = delta.getDistance()
                if (dist == 0f) continue

                // The "ideal" length of the spring is the sum of radii + a buffer
                val idealLength = nodeA.radius + nodeB.radius + (options.minDistance * 5)

                val displacement = dist - idealLength
                val springForce = delta.normalized() * displacement * options.spring * edge.strength

                forces[nodeA.id] = forces[nodeA.id]!! + springForce
                forces[nodeB.id] = forces[nodeB.id]!! - springForce
            }
        }

        // 3. Update velocities and positions (Euler integration)
        for (node in newNodes.values) {
            val force = forces[node.id]!!

            // F = ma -> a = F/m
            val acceleration = force / node.mass

            // v = v0 + at
            var newVel = node.vel + (acceleration * dt)

            // Apply damping
            newVel *= options.damping

            // p = p0 + vt
            val newPos = node.pos + (newVel * dt)

            // Update the node in the map
            node.vel = newVel
            node.pos = newPos
        }

        return newNodes
    }
}

// Helper for unique pairs (n=2)
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
private fun Offset.normalized(): Offset {
    val mag = this.getDistance()
    return if (mag == 0f) Offset.Zero else this / mag
}
