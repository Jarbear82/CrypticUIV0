package com.tau.kt_vis_network.network.physics.solvers

// --- ADDED IMPORTS ---
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Body
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsBody
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Point
import com.tau.cryptic_ui_v0.notegraph.graph.physics.SolverOptions
// ---
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Calculates hierarchical spring forces for a physics simulation in pure Kotlin.
 *
 * @param body The graph structure containing nodes and edges.
 * @param physicsBody The state of the physics simulation (forces, etc.).
 * @param options Configuration for the solver.
 */
class HierarchicalSpringSolver(
    private var body: Body,
    private var physicsBody: PhysicsBody,
    private var options: SolverOptions
) {

    /**
     * Updates the solver's options.
     */
    fun setOptions(options: SolverOptions) {
        this.options = options
    }

    /**
     * This function calculates the spring forces on the nodes, accounting for the hierarchical levels.
     */
    fun solve() {
        val edges = body.edges
        val factor = 0.5
        val edgeIndices = physicsBody.physicsEdgeIndices
        val nodeIndices = physicsBody.physicsNodeIndices
        val forces = physicsBody.forces

        // Temporary storage for hierarchical spring forces.
        // In JS, these were added dynamically to the force object.
        // In Kotlin, we use a separate map.
        // --- UPDATED: Vector2D to Point ---
        val springForces = mutableMapOf<String, Point>()
        for (nodeId in nodeIndices) {
            // --- UPDATED: Vector2D to Point ---
            springForces[nodeId] = Point(0.0, 0.0)
        }

        // forces caused by the edges, modelled as springs
        for (edgeId in edgeIndices) {
            // --- UPDATED: Use edgeId to get from map ---
            val edge = edges[edgeId] ?: continue // Safety check

            if (edge.connected) {
                // Get edge length, defaulting to options if not specified
                val edgeLength = edge.options.length ?: options.springLength

                val dx = edge.from.x - edge.to.x
                val dy = edge.from.y - edge.to.y

                // Ensure distance is not zero to avoid division by zero
                val distance = max(sqrt(dx * dx + dy * dy), 0.01)

                // the 1/distance is so the fx and fy can be calculated without sine or cosine.
                val springForce = (options.springConstant * (edgeLength - distance)) / distance
                val fx = dx * springForce
                val fy = dy * springForce

                // This is the core hierarchical logic:
                if (edge.to.level != edge.from.level) {
                    // If nodes are on different levels, apply to temporary hierarchical spring forces
                    springForces[edge.toId]?.let {
                        it.x -= fx
                        it.y -= fy
                    }
                    springForces[edge.fromId]?.let {
                        it.x += fx
                        it.y += fy
                    }
                } else {
                    // If nodes are on the same level, apply directly to main forces with a factor
                    forces[edge.toId]?.let {
                        it.x -= factor * fx
                        it.y -= factor * fy
                    }
                    forces[edge.fromId]?.let {
                        it.x += factor * fx
                        it.y += factor * fy
                    }
                }
            }
        }

        // normalize and apply hierarchical spring forces
        val springForceLimit = 1.0 // Max spring force
        for (nodeId in nodeIndices) {
            val sForce = springForces[nodeId]
            val mainForce = forces[nodeId]

            if (sForce != null && mainForce != null) {
                // Limit the spring force
                val springFx = min(springForceLimit, max(-springForceLimit, sForce.x))
                val springFy = min(springForceLimit, max(-springForceLimit, sForce.y))

                // Add the limited hierarchical force to the main force
                mainForce.x += springFx
                mainForce.y += springFy
            }
        }

        // retain energy balance (distribute total force evenly)
        var totalFx = 0.0
        var totalFy = 0.0
        for (nodeId in nodeIndices) {
            forces[nodeId]?.let {
                totalFx += it.x
                totalFy += it.y
            }
        }

        if (nodeIndices.isNotEmpty()) {
            val correctionFx = totalFx / nodeIndices.size
            val correctionFy = totalFy / nodeIndices.size

            for (nodeId in nodeIndices) {
                forces[nodeId]?.let {
                    it.x -= correctionFx
                    it.y -= correctionFy
                }
            }
        }
    }
}
