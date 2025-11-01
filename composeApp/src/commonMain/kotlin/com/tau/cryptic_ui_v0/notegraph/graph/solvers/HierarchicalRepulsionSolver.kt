package com.tau.kt_vis_network.network.physics.solvers

import com.tau.cryptic_ui_v0.notegraph.graph.physics.Body
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsBody
import com.tau.cryptic_ui_v0.notegraph.graph.physics.SolverOptions
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Solves hierarchical repulsion forces between nodes.
 *
 * This is a Kotlin port of the JavaScript implementation.
 *
 * @param body The main body containing all nodes.
 * @param physicsBody The physics-specific body with indices and force maps.
 * @param initialOptions The initial solver options.
 */
class HierarchicalRepulsionSolver(
    private val body: Body,
    private val physicsBody: PhysicsBody,
    initialOptions: SolverOptions
) {

    private var options: SolverOptions = initialOptions
    private var overlapAvoidanceFactor: Double = 0.0

    init {
        // Set the initial overlap factor based on options
        updateOverlapFactor()
    }

    /**
     * Updates the solver's options and recalculates derived values.
     */
    fun setOptions(options: SolverOptions) {
        this.options = options
        updateOverlapFactor()
    }

    /**
     * Recalculates the overlap avoidance factor based on the current options.
     */
    private fun updateOverlapFactor() {
        // Use the elvis operator (?:) for (|| 0)
        // Use coerceIn for Math.max(0, Math.min(1, ...))
        this.overlapAvoidanceFactor = (this.options.avoidOverlap).coerceIn(0.0, 1.0)
    }

    /**
     * Calculate the forces the nodes apply on each other based on a repulsion field.
     * This field is linearly approximated.
     */
    fun solve() {
        val nodes = body.nodes
        val nodeIndices = physicsBody.physicsNodeIndices
        val forces = physicsBody.forces

        // Repulsing forces between nodes
        val nodeDistance = options.nodeDistance

        // We loop from i over all but the last entree in the array
        // j loops from i+1 to the last. This way we do not double count any of the indices, nor i === j
        for (i in 0 until nodeIndices.size - 1) {
            val node1Id = nodeIndices[i]
            // Get node from map, skip if it doesn't exist
            val node1 = nodes[node1Id] ?: continue

            for (j in i + 1 until nodeIndices.size) {
                val node2Id = nodeIndices[j]
                // Get node from map, skip if it doesn't exist
                val node2 = nodes[node2Id] ?: continue

                // Nodes only affect nodes on their level
                if (node1.level == node2.level) {

                    // Use shape.radius, assuming 0.0 default if not present
                    val theseNodesDistance = nodeDistance +
                            overlapAvoidanceFactor * (node1.shape.radius / 2.0 + node2.shape.radius / 2.0)

                    val dx = node2.x - node1.x
                    val dy = node2.y - node1.y
                    val distance = sqrt(dx * dx + dy * dy)
                    val steepness = 0.05
                    val repulsingForce: Double

                    if (distance < theseNodesDistance) {
                        // Use the pow() extension function for Math.pow(..., 2)
                        repulsingForce =
                            -(steepness * distance).pow(2) +
                                    (steepness * theseNodesDistance).pow(2)
                    } else {
                        repulsingForce = 0.0
                    }

                    // Normalize force
                    // Check against 0.0 for Double
                    val normalizedForce = if (distance != 0.0) {
                        repulsingForce / distance
                    } else {
                        0.0
                    }

                    val fx = dx * normalizedForce
                    val fy = dy * normalizedForce

                    // Get the mutable Force objects from the map.
                    // Skip if a node doesn't have a corresponding force entry.
                    val force1 = forces[node1.id] ?: continue
                    val force2 = forces[node2.id] ?: continue

                    // Apply the forces by mutating the objects
                    force1.x -= fx
                    force1.y -= fy
                    force2.x += fx
                    force2.y += fy
                }
            }
        }
    }
}