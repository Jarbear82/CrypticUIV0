package com.tau.kt_vis_network.network.physics.solvers

// --- ADDED IMPORTS ---
import com.tau.cryptic_ui_v0.notegraph.graph.physics.BarnesHutOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Body
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Branch
import com.tau.cryptic_ui_v0.notegraph.graph.physics.ForceAtlas2Options
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Node
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsBody
// ---
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**
 * Kotlin translation of the ForceAtlas2BasedRepulsionSolver.
 *
 * @augments BarnesHutSolver
 */
class ForceAtlas2BasedRepulsionSolver(
    body: Body,
    physicsBody: PhysicsBody,
    // --- UPDATED: Constructor now accepts ForceAtlas2Options ---
    options: ForceAtlas2Options
    // --- UPDATED: Pass a new BarnesHutOptions instance to the super constructor ---
) : BarnesHutSolver(
    body,
    physicsBody,
    BarnesHutOptions(
        theta = options.theta,
        gravitationalConstant = options.gravitationalConstant,
        centralGravity = options.centralGravity,
        springLength = options.springLength,
        springConstant = options.springConstant,
        damping = options.damping,
        avoidOverlap = options.avoidOverlap,
        nodeDistance = options.nodeDistance
    )
) {

    // --- Store the original FA2 options if needed for FA2-specific logic ---
    private var fa2Options: ForceAtlas2Options = options

    /**
     * Replaced Alea() with Kotlin's standard Random number generator.
     * We use the string's hashCode as a seed to mimic the original's
     * seeded (deterministic) behavior.
     * For a non-deterministic seed, you could use `Random(System.currentTimeMillis())`
     * or for a standard instance, just `Random.Default`.
     */
    private val rng = Random("FORCE ATLAS 2 BASED REPULSION SOLVER".hashCode())

    /**
     * Calculate the forces based on the distance.
     *
     * Note: In Kotlin, private functions are typically named without a
     * leading underscore, e.g., `calculateForces`.
     * Kept `_calculateForces` to match JS, but `calculateForces` is more idiomatic.
     */
    override fun _calculateForces(
        distance: Double,
        dx: Double,
        dy: Double,
        node: Node,
        // --- FIXED: Changed ParentBranch typo to Branch ---
        parentBranch: Branch
    ) {
        // Use local vars since parameters are vals
        var localDistance = distance
        var localDx = dx

        if (localDistance == 0.0) {
            localDistance = 0.1 * rng.nextDouble() // Get a random value between 0.0 and 1.0
            localDx = localDistance
        }

        // JS `node.shape.radius` check implies it could be 0 or null.
        // Assuming `radius` is a non-negative Double, checking > 0.0 is equivalent.
        if (this.overlapAvoidanceFactor > 0.0 && node.shape.radius > 0.0) {
            localDistance = max(
                0.1 + this.overlapAvoidanceFactor * node.shape.radius,
                localDistance - node.shape.radius
            )
        }

        val degree = node.edges.size + 1

        // ---
        // --- FIX: This must be pow(3), not pow(2). ---
        // ---
        // The force is F/d^2, but the component fx = F * (dx/d), which
        // leads to a d^3 in the denominator.
        val gravityForce = (this.options.gravitationalConstant *
                parentBranch.mass *
                node.options.mass *
                degree) / localDistance.pow(3) // <-- WAS pow(2)

        val fx = localDx * gravityForce

        // Note: The original JS code only reassigned `dx`, not `dy`.
        // `fy` is calculated with the original `dy` parameter.
        val fy = dy * gravityForce

        // Safely access and update the forces map.
        // The ?.apply {} block will only execute if the node.id exists in the map.
        // The original JS would have crashed if the entry was missing.
        this.physicsBody.forces[node.id]?.apply {
            x += fx
            y += fy
        }
    }
}