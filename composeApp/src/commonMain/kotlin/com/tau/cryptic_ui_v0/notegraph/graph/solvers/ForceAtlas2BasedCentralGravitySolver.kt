package com.tau.kt_vis_network.network.physics.solvers

// --- ADDED IMPORTS ---
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Body
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Node
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsBody
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Point
import com.tau.cryptic_ui_v0.notegraph.graph.physics.SolverOptions
// ---

// ADDED: Debug flag
private const val DEBUG = true
private var solveCount = 0 // Note: This will be separate from other solvers

/**
 * ForceAtlas2-based central gravity solver.
 *
 * This implementation is a direct Kotlin port of the JavaScript version.
 * It assumes that [CentralGravitySolver] is an abstract base class
 * with an abstract method `calculateForces`.
 *
 * @param body The main simulation body (type 'Any' as in JS).
 * @param physicsBody The object holding physics state like forces and velocities.
 * @param options Configuration options for the solver.
 */
class ForceAtlas2BasedCentralGravitySolver(
    // --- UPDATED: body type from Any to Body ---
    body: Body,
    physicsBody: PhysicsBody,
    options: SolverOptions
) : CentralGravitySolver(body, physicsBody, options) {

    /**
     * Calculates the gravity force for a single node.
     *
     * This force is proportional to the node's degree (plus one) and its mass.
     * It overrides the abstract method from [CentralGravitySolver].
     *
     * @param distance The distance from the center (0,0) to the node.
     * @param dx The x-component of the normalized direction vector from the node to the center.
     * @param dy The y-component of the normalized direction vector from the node to the center.
     * @param forces A map of node IDs to their corresponding force vectors. This map is modified in-place.
     * @param node The node for which to calculate the force.
     */
    // --- UPDATED: Vector2D to Point ---
    override fun calculateForces(
        distance: Double,
        dx: Double,
        dy: Double,
        forces: MutableMap<String, Point>,
        node: Node
    ) {
        if (DEBUG && solveCount == 0) { // Log once
            println("[ForceAtlas2Gravity] Solving...")
            solveCount++
        }

        if (distance > 0) {
            // In ForceAtlas2, gravity is proportional to the node's degree.
            // .size is the Kotlin equivalent of .length for collections
            val degree = node.edges.size + 1
            val gravityForce = options.centralGravity * degree * node.options.mass

            if (DEBUG && solveCount % 300 == 0 && node.id == physicsBody.physicsNodeIndices.first()) {
                println("[ForceAtlas2Gravity] Node ${node.id}: dist $distance, degree $degree, gravForce $gravityForce")
            }

            // Get the force vector for the node, creating it if it doesn't exist.
            // This is a safe way to handle map[key].x in Kotlin
            // --- UPDATED: Vector2D() to Point(0.0, 0.0) ---
            val force = forces.getOrPut(node.id) { Point(0.0, 0.0) }

            // Apply the calculated force.
            force.x = dx * gravityForce
            force.y = dy * gravityForce

            if (DEBUG && (force.x.isNaN() || force.y.isNaN())) {
                println("[ForceAtlas2Gravity] WARNING: NaN force for node ${node.id}. Dist: $distance, gravForce: $gravityForce")
            }
        }
        // If distance is 0, no force is applied (node is at the center).
    }
}
