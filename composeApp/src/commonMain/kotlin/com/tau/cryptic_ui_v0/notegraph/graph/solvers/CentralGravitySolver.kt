package com.tau.kt_vis_network.network.physics.solvers

import com.tau.cryptic_ui_v0.notegraph.graph.physics.Body
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Node
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsBody
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Point
import com.tau.cryptic_ui_v0.notegraph.graph.physics.SolverOptions


import kotlin.math.sqrt

// ADDED: Debug flag
private const val DEBUG = true
private var solveCount = 0

/**
 * Central Gravity Solver
 *
 * This class applies a gravitational force pulling all nodes towards the center (0,0).
 * It's a direct Kotlin translation of the provided JavaScript class.
 *
 * @param body The main simulation body containing all nodes.
 * @param physicsBody The physics-specific data, including active nodes and force maps.
 * @param options Configuration for the solver, like gravity strength.
 */
open class CentralGravitySolver(
    protected var body: Body,
    protected var physicsBody: PhysicsBody,
    protected var options: SolverOptions
) {


    /**
     * Calculates and applies the central gravity forces to each node.
     */
    fun solve() {
        if (DEBUG && solveCount % 60 == 0) { // Log every 60 frames
            println("[CentralGravitySolver] Solving... (Frame ${solveCount++}). Gravity: ${options.centralGravity}")
        } else {
            solveCount++
        }

        val nodes = this.body.nodes
        val nodeIndices = this.physicsBody.physicsNodeIndices
        val forces = this.physicsBody.forces

        for (nodeId in nodeIndices) {
            // Get the node from the body, skip if it doesn't exist
            val node = nodes[nodeId] ?: continue

            val dx = -node.x
            val dy = -node.y
            val distance = sqrt(dx * dx + dy * dy)

            // Calculate and apply forces for this node
            calculateForces(distance, dx, dy, forces, node)
        }
    }

    /**
     * Internal helper to calculate the forces based on distance.
     *
     * @param distance The node's distance from the center (0,0).
     * @param dx The x-component of the vector to the center.
     * @param dy The y-component of the vector to the center.
     * @param forces The map of forces to be updated.
     * @param node The node for which to calculate forces.
     */

    protected open fun calculateForces(
        distance: Double,
        dx: Double,
        dy: Double,
        forces: MutableMap<String, Point>,
        node: Node
    ) {
        // Find the force vector for this node, skip if it's not in the map
        val forceVector = forces[node.id] ?: return

        val gravityForce = if (distance == 0.0) {
            0.0
        } else {
            this.options.centralGravity / distance
        }

        if (DEBUG && solveCount % 300 == 0 && node.id == physicsBody.physicsNodeIndices.first()) {
            println("[CentralGravitySolver] Node ${node.id}: dist $distance, gravForce $gravityForce")
        }

        // Apply the calculated force by mutating the force vector
        forceVector.x = dx * gravityForce
        forceVector.y = dy * gravityForce

        if (DEBUG && (forceVector.x.isNaN() || forceVector.y.isNaN())) {
            println("[CentralGravitySolver] WARNING: NaN force for node ${node.id}. Dist: $distance, gravForce: $gravityForce")
        }
    }
}
