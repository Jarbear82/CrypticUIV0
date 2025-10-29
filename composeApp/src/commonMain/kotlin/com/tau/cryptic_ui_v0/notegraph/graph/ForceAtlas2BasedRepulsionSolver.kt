// jarbear82/crypticuiv0/CrypticUIV0-kotlin-graph/composeApp/src/commonMain/kotlin/com/tau/cryptic_ui_v0/notegraph/graph/ForceAtlas2BasedRepulsionSolver.kt
package com.tau.cryptic_ui_v0.notegraph.graph.physics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * ForceAtlas2 Based Repulsion Solver.
 * This is a hybrid approach using the BarnesHut tree structure but applying
 * a force calculation inspired by ForceAtlas2 (degree-dependent, 1/distance^2).
 * Note: This is NOT a full ForceAtlas2 implementation which uses different spatial optimization.
 * @augments BarnesHutSolver
 */
class ForceAtlas2BasedRepulsionSolver(
    body: Body,
    physicsBody: PhysicsBody,
    options: ForceAtlas2BasedOptions
) : BarnesHutSolver(body, physicsBody, options.toBarnesHutOptions()) {

    // Store ForceAtlas specific options separately if needed for future use,
    // but primarily use the converted BarnesHutOptions for the tree structure.
    private val optionsFA: ForceAtlas2BasedOptions = options
    private val _rngFA = Random(options.randomSeed ?: System.currentTimeMillis())

    // Override the calculateForces method from BarnesHutSolver
    override fun calculateForces(distanceIn: Double, dxIn: Double, dyIn: Double, node: Node, branch: Branch) {
        var distance = distanceIn
        var dx = dxIn
        var dy = dyIn

        val nodeRadius = node.shape.radius ?: 0.0
        // Calculate minimum distance using the pre-calculated factor (inherited)
        val minimumDistance = minDistanceFactor * nodeRadius
        val epsilon = 1e-9 // Small value to handle near-zero distances robustly

        // Check for overlap first
        if (effectiveAvoidOverlap > 0 && distance < minimumDistance + epsilon) {
            // Calculate overlap force (same logic as BarnesHutSolver's calculateForces)
            val overlap = max(0.0, minimumDistance - distance)
            val overlapForceMagnitude = abs(options.gravitationalConstant) * (1 + overlap / nodeRadius) * 0.1 // Adjust multiplier as needed

            // Ensure we have a direction for the overlap force
            if (distance < epsilon) {
                dx = (_rngFA.nextDouble() - 0.5) * 2.0
                dy = (_rngFA.nextDouble() - 0.5) * 2.0
                val lenSq = dx*dx + dy*dy
                if (lenSq > 0) {
                    val lenInv = 1.0 / sqrt(lenSq)
                    dx *= lenInv
                    dy *= lenInv
                } else { dx = 1.0; dy = 0.0 } // Fallback
                distance = minimumCalculationDistance // Prevent division by zero later
            }

            // Apply force pushing nodes apart
            val fx = (dx / distance) * overlapForceMagnitude
            val fy = (dy / distance) * overlapForceMagnitude

            physicsBody.forces[node.id]?.let {
                it.x -= fx // <-- MODIFIED: Subtract overlap force
                it.y -= fy // <-- MODIFIED: Subtract overlap force
            }
            // Apply opposite force only if branch represents a single different node
            if (branch.childrenCount == 1 && branch.children.data != null) {
                val childNodeId = branch.children.data!!.id
                if(childNodeId != node.id) {
                    physicsBody.forces[childNodeId]?.let {
                        it.x += fx // <-- MODIFIED: Add overlap force
                        it.y += fy // <-- MODIFIED: Add overlap force
                    }
                }
            }
        } else {
            // ForceAtlas2 inspired gravitational force calculation if no overlap or overlap disabled
            distance = max(minimumCalculationDistance, distance) // Ensure minimum distance

            // ForceAtlas2 specific calculation: degree influences force, distance squared
            val degree = node.edges.size + 1 // Degree = number of connected edges + 1
            // Use branch.mass for approximation (consistent with BarnesHut structure)
            // Use the FA gravitational constant from optionsFA
            val forceMagnitude = (optionsFA.gravitationalConstant * branch.mass * node.options.mass * degree) / distance.pow(2)

            // Force components are proportional to dx/dy over distance
            val fx = (dx / distance) * forceMagnitude
            val fy = (dy / distance) * forceMagnitude

            physicsBody.forces[node.id]?.let {
                it.x -= fx // <-- MODIFIED: Subtract gravitational force
                it.y -= fy // <-- MODIFIED: Subtract gravitational force
            }

            // Apply opposite force only if branch represents a single different node
            if (branch.childrenCount == 1 && branch.children.data != null) {
                val childNodeId = branch.children.data!!.id
                if(childNodeId != node.id) {
                    physicsBody.forces[childNodeId]?.let {
                        it.x += fx // <-- MODIFIED: Add gravitational force
                        it.y += fy // <-- MODIFIED: Add gravitational force
                    }
                }
            }
        }
    }
}


// Options class remains the same
data class ForceAtlas2BasedOptions(
    val theta: Double = 0.5,
    val gravitationalConstant: Double = -50.0,
    val centralGravity: Double = 0.01,
    val springLength: Double = 100.0,
    val springConstant: Double = 0.08,
    val damping: Double = 0.4,
    val avoidOverlap: Double? = 0.0, // Default avoidOverlap based on Vis.js default (0)
    val randomSeed: Long? = null
){
    // Helper function to convert to the format BarnesHutSolver expects
    // We pass the ForceAtlas gravitational constant here, although BarnesHutSolver
    // has its own default. The overridden calculateForces will use the FA constant.
    fun toBarnesHutOptions(): BarnesHutOptions {
        return BarnesHutOptions(
            theta = this.theta,
            gravitationalConstant = this.gravitationalConstant,
            avoidOverlap = this.avoidOverlap,
            randomSeed = this.randomSeed
        )
    }
}