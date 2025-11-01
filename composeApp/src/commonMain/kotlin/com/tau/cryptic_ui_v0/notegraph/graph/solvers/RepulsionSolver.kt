package com.tau.kt_vis_network.network.physics.solvers

import kotlin.math.sqrt
import kotlin.random.Random

import com.tau.cryptic_ui_v0.notegraph.graph.physics.Body
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsBody
import com.tau.cryptic_ui_v0.notegraph.graph.physics.RepulsionOptions

/**
 * Calculates repulsion forces between nodes in a physics simulation.
 *
 * This is a pure Kotlin port of the JavaScript RepulsionSolver,
 * designed for use in Kotlin Multiplatform projects.
 *
 * @param body The main simulation body containing all nodes.
 * @param physicsBody The state container for active nodes and forces.
 * @param options Initial configuration for the solver.
 */
class RepulsionSolver(
    private val body: Body,
    private val physicsBody: PhysicsBody,
    options: RepulsionOptions
) {

    /**
     * Public, mutable options.
     * Can be changed at runtime via [setOptions].
     */
    var options: RepulsionOptions = options
        private set // Allow external read, but only internal (via setOptions) write.

    /**
     * Pure Kotlin random number generator, seeded for deterministic behavior
     * similar to the original JS version.
     */
    private val rng = Random("REPULSION SOLVER".hashCode().toLong())

    /**
     * Updates the solver's configuration options.
     *
     * @param newOptions The new options to apply.
     */
    fun setOptions(newOptions: RepulsionOptions) {
        this.options = newOptions
    }

    /**
     * Calculate the forces the nodes apply on each other based on a repulsion field.
     * This field is linearly approximated.
     */
    fun solve() {
        val nodes = body.nodes
        val nodeIndices = physicsBody.physicsNodeIndices
        val forces = physicsBody.forces
        val nodeDistance = options.nodeDistance

        // If nodeDistance is zero or negative, repulsion is undefined or infinite.
        // We skip calculation to avoid division by zero and nonsensical forces.
        if (nodeDistance <= 0) {
            return
        }

        // Approximation constants from the original JS logic
        val a = -2.0 / 3.0 / nodeDistance
        val b = 4.0 / 3.0

        // We loop from i over all but the last entree in the array
        // j loops from i+1 to the last. This way we do not double count any of the indices, nor i === j
        for (i in 0 until nodeIndices.size - 1) {
            val node1 = nodes[nodeIndices[i]] ?: continue // Get node1 or skip if ID is invalid

            for (j in i + 1 until nodeIndices.size) {
                val node2 = nodes[nodeIndices[j]] ?: continue // Get node2 or skip if ID is invalid

                val dx = node2.x - node1.x
                val dy = node2.y - node1.y

                var distance = sqrt(dx * dx + dy * dy)

                // Store the original dx, as it might be overwritten if distance is 0
                var effectiveDx = dx

                // Same condition as BarnesHutSolver, making sure nodes are never 100% overlapping.
                if (distance == 0.0) {
                    distance = 0.1 * rng.nextDouble() // Use Kotlin's RNG
                    effectiveDx = distance
                }

                if (distance < 2 * nodeDistance) {
                    val repulsingForce = if (distance < 0.5 * nodeDistance) {
                        1.0
                    } else {
                        a * distance + b // linear approx
                    }

                    val forceMagnitude = repulsingForce / distance
                    val fx = effectiveDx * forceMagnitude
                    val fy = dy * forceMagnitude

                    // Get the force vectors from the map.
                    // If a node is in physicsNodeIndices, it MUST have a force vector.
                    // We use ?: continue for safety, but `!!` would also be justifiable
                    // if the contract is strict.
                    val force1 = forces[node1.id] ?: continue
                    val force2 = forces[node2.id] ?: continue

                    // Apply the forces
                    force1.x -= fx
                    force1.y -= fy
                    force2.x += fx
                    force2.y += fy
                }
            }
        }
    }
}
