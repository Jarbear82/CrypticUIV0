package com.tau.kt_vis_network.network.physics.solvers

import com.tau.cryptic_ui_v0.notegraph.graph.physics.Body
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Node
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsBody
import com.tau.cryptic_ui_v0.notegraph.graph.physics.SolverOptions
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.math.max
import kotlin.math.sqrt


/**
 * Calculates spring forces for a physics simulation in pure Kotlin.
 *
 * @param body The graph structure containing nodes and edges.
 * @param physicsBody The state of the physics simulation (forces, etc.).
 * @param options Configuration for the solver.
 */
class SpringSolver(
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
     * This function calculates the spring forces on the nodes.
     */
    fun solve() {
        val edgeIds = physicsBody.physicsEdgeIndices // This is now List<String>
        val edges = body.edges // This is now Map<String, Edge>

        // forces caused by the edges, modelled as springs
        for (edgeId in edgeIds) {
            val edge = edges[edgeId] ?: continue // Fetch edge by ID from the map

            if (edge.connected && edge.toId != edge.fromId) {
                // only calculate forces if nodes exist in the body
                if (body.nodes.containsKey(edge.toId) && body.nodes.containsKey(edge.fromId)) {

                    val viaNode = edge.edgeType.via
                    if (viaNode != null) {
                        // This is a smooth edge with an intermediate node
                        val edgeLength = edge.options.length ?: options.springLength
                        val node1 = edge.to
                        val node2 = viaNode
                        val node3 = edge.from

                        calculateSpringForce(node1, node2, 0.5 * edgeLength)
                        calculateSpringForce(node2, node3, 0.5 * edgeLength)
                    } else {
                        // This is a direct edge
                        // The * 1.5 multiplier was in the original JS to make straight edges
                        // appear similar in length to smooth edges (which are pushed apart
                        // by the repulsion on the 'via' node).
                        val edgeLength = edge.options.length ?: (options.springLength * 1.5)
                        calculateSpringForce(edge.from, edge.to, edgeLength)
                    }
                }
            }
        }
    }

    /**
     * Calculates the spring force between two nodes and applies it.
     *
     * @param node1 The first node.
     * @param node2 The second node.
     * @param edgeLength The target length of the "spring" between them.
     */
    private fun calculateSpringForce(node1: Node, node2: Node, edgeLength: Double) {
        val dx = node1.x - node2.x
        val dy = node1.y - node2.y
        // Ensure distance is not zero to avoid division by zero.
        val distance = max(sqrt(dx * dx + dy * dy), 0.01)

        // the 1/distance is so the fx and fy can be calculated without sine or cosine.
        val springForce = (options.springConstant * (edgeLength - distance)) / distance
        val fx = dx * springForce
        val fy = dy * springForce

        // Apply forces to nodes that are part of the physics simulation
        physicsBody.forces[node1.id]?.let {
            it.x += fx
            it.y += fy
        }

        physicsBody.forces[node2.id]?.let {
            it.x -= fx
            it.y -= fy
        }
    }
}
