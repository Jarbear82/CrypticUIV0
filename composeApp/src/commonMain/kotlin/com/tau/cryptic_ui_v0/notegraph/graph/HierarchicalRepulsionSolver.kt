package com.tau.cryptic_ui_v0.notegraph.graph.physics

import kotlin.math.*
import kotlin.random.Random

/**
 * Hierarchical Repulsion Solver (from user prompt)
 */
class HierarchicalRepulsionSolver(
    private val body: Body,
    private val physicsBody: PhysicsBody,
    options: HierarchicalRepulsionOptions
) {
    // FIX: Removed 'private' and will remove the clashing 'setOptions' function
    var options: HierarchicalRepulsionOptions = options
        set(value) {
            field = value
            overlapAvoidanceFactor = max(0.0, min(1.0, value.avoidOverlap ?: 0.0))
        }
    private var overlapAvoidanceFactor: Double

    init {
        this.options = options // Apply initial options through custom setter
        overlapAvoidanceFactor = max(0.0, min(1.0, this.options.avoidOverlap ?: 0.0))
    }

    // FIX: Removed the clashing 'setOptions' function.
    // To set options, just assign to the public 'options' property.

    fun solve() {
        val nodes = body.nodes
        val nodeIndices = physicsBody.physicsNodeIndices
        val forces = physicsBody.forces
        val nodeDistance = options.nodeDistance

        for (i in 0 until nodeIndices.size - 1) {
            val node1 = nodes[nodeIndices[i]] ?: continue

            for (j in i + 1 until nodeIndices.size) {
                val node2 = nodes[nodeIndices[j]] ?: continue

                if (node1.level == node2.level) {
                    val radius1 = node1.shape.radius ?: 0.0
                    val radius2 = node2.shape.radius ?: 0.0
                    val theseNodesDistance = nodeDistance + overlapAvoidanceFactor * (radius1 / 2.0 + radius2 / 2.0)

                    val dx = node2.x - node1.x
                    val dy = node2.y - node1.y
                    val distance = sqrt(dx * dx + dy * dy)

                    val steepness = 0.05
                    var repulsingForce = 0.0

                    if (distance > 0 && distance < theseNodesDistance) {
                        repulsingForce = steepness.pow(2) * (theseNodesDistance.pow(2) - distance.pow(2)) / distance
                    }

                    if (repulsingForce != 0.0 && !distance.isNaN()) {
                        val fx = dx * repulsingForce
                        val fy = dy * repulsingForce

                        forces[node1.id]?.let {
                            it.x -= fx
                            it.y -= fy
                        }
                        forces[node2.id]?.let {
                            it.x += fx
                            it.y += fy
                        }
                    } else if (distance == 0.0) {
                        val randomForce = 0.01 * options.springConstant
                        val angle = Random.nextDouble(0.0, 2 * PI)
                        val fx = cos(angle) * randomForce
                        val fy = sin(angle) * randomForce

                        forces[node1.id]?.let { it.x -= fx; it.y -= fy }
                        forces[node2.id]?.let { it.x += fx; it.y += fy }
                    }
                }
            }
        }
    }
}

data class HierarchicalRepulsionOptions(
    val nodeDistance: Double = 120.0,
    val avoidOverlap: Double? = 0.0,
    val springConstant: Double = 0.01
)