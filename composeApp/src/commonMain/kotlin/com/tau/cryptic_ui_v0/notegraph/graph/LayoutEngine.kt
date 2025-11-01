package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.ui.geometry.Offset
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.SolverType
import com.tau.cryptic_ui_v0.notegraph.graph.layout.HierarchicalLayoutAlgorithm
// --- ADDED: Imports for KamadaKawai data conversion ---
import com.tau.cryptic_ui_v0.notegraph.graph.layout.KamadaKawai
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Node
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Edge
import com.tau.cryptic_ui_v0.notegraph.graph.physics.NodeOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.FixedOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Shape
import com.tau.cryptic_ui_v0.notegraph.graph.physics.EdgeOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.EdgeType
import com.tau.cryptic_ui_v0.notegraph.graph.physics.SimulationStatus

// ---

/**
 * Main engine to orchestrate graph layout.
 * It decides whether to use a physics-based simulation or a static hierarchical layout.
 */
class LayoutEngine(
    initialNodes: List<NodeDisplayItem>,
    initialEdges: List<EdgeDisplayItem>,
    private val width: Float,
    private val height: Float,
    private val coroutineScope: CoroutineScope,
    private val layoutOptions: LayoutOptions,
    private val physicsOptions: PhysicsOptions
) {
    // The physics engine is now an internal component
    private val physicsEngine: PhysicsEngine = PhysicsEngine(
        initialNodes,
        initialEdges,
        width,
        height,
        coroutineScope,
        physicsOptions,
        // Pass the node radius from style options to physics for solver calculations
        (physicsOptions.nodeRadius ?: DEFAULT_NODE_RADIUS_DP.value)
    )

    // Expose the node positions and stability state
    val nodePositionsState: StateFlow<Map<String, Offset>> = physicsEngine.nodePositionsState
    val status: StateFlow<SimulationStatus> = physicsEngine.status

    init {
        // Perform initial layout calculation on init
        updateData(initialNodes, initialEdges, isInitialLoad = true)
    }

    /**
     * Updates the data for the layout.
     * This will trigger either a hierarchical calculation or restart the physics simulation.
     */
    fun updateData(newNodes: List<NodeDisplayItem>, newEdges: List<EdgeDisplayItem>, isInitialLoad: Boolean = false) {
        // Places new nodes intelligently and publishes on initial load
        physicsEngine.updateData(newNodes, newEdges, isInitialLoad)

        // 2. Decide which layout strategy to use
        if (layoutOptions.hierarchical.enabled) {
            // --- Hierarchical Layout ---
            physicsEngine.stopSimulation()

            // Calculate static positions using the new algorithm
            val nodeLevels = HierarchicalLayoutAlgorithm.calculatePositions(
                physicsEngine.nodes,
                physicsEngine.edges,
                layoutOptions.hierarchical
            )
            // Inform physics engine of the levels (for HierarchicalRepulsion solver)
            physicsEngine.updateNodeLevels(nodeLevels)

            // Publish the new static positions
            physicsEngine.publishPositions()

            // Optionally, run physics *after* setting the hierarchical positions
            if (layoutOptions.hierarchical.runPhysicsAfter) {
                // Ensure the correct solver is selected for hierarchical physics
                if (physicsOptions.solver != SolverType.HIERARCHICAL) {
                    println("Warning: Hierarchical layout is enabled with 'runPhysicsAfter', but solver is not 'HIERARCHICAL'. Consider changing physicsOptions.solver.")
                }
                physicsEngine.startSimulation()
            }
        } else if (isInitialLoad && layoutOptions.improvedLayout) {
            // --- UPDATED: Run KamadaKawai layout ---

            // 1. Convert PhysicsEngine data to solver data
            val physicsNodes = physicsEngine.nodes
            val physicsEdges = physicsEngine.edges
            val nodeRadius = (physicsOptions.nodeRadius ?: DEFAULT_NODE_RADIUS_DP.value).toDouble()

            val solverNodes: Map<String, Node> = physicsNodes.mapValues { (_, pn) ->
                Node(
                    id = pn.id,
                    x = pn.x.toDouble(),
                    y = pn.y.toDouble(),
                    options = NodeOptions(
                        mass = pn.mass.toDouble(),
                        fixed = FixedOptions(x = pn.isFixed, y = pn.isFixed)
                    ),
                    shape = Shape(radius = nodeRadius),
                    level = pn.level,
                    edges = emptyList() // Not needed by KamadaKawai
                )
            }

            val solverEdges: Map<String, Edge> = physicsEdges.mapNotNull { (_, pe) ->
                val fromNode = solverNodes[pe.from]
                val toNode = solverNodes[pe.to]
                if (fromNode != null && toNode != null) {
                    pe.id to Edge(
                        id = pe.id,
                        fromId = pe.from,
                        toId = pe.to,
                        options = EdgeOptions(length = pe.springLength.toDouble()),
                        from = fromNode,
                        to = toNode,
                        connected = true,
                        edgeType = EdgeType(null)
                    )
                } else {
                    null // Should not happen if data is clean
                }
            }.toMap()

            // 2. Run the layout algorithm (this modifies solverNodes in-place)
            KamadaKawai.calculatePositions(
                solverNodes,
                solverEdges,
                physicsOptions.defaultSpringLength.toDouble(),
                physicsOptions.defaultSpringConstant.toDouble()
            )

            // 3. Update the PhysicsEngine nodes with the new positions
            solverNodes.forEach { (id, solverNode) ->
                physicsEngine.nodes[id]?.apply {
                    x = solverNode.x.toFloat()
                    y = solverNode.y.toFloat()
                }
            }

            // 4. Publish new positions and start physics
            physicsEngine.publishPositions()
            physicsEngine.startSimulation()

        } else {
            // --- Standard Physics-Based Layout ---
            if (isInitialLoad) {
                // On first load, physicsEngine.updateData already set random positions
                // and published them.

                // NOW, perform the synchronous pre-run.
                if (layoutOptions.preRunSteps > 0) {
                    // Run a fixed number of steps synchronously
                    physicsEngine.preRunSimulation(layoutOptions.preRunSteps)
                } else {
                    // No pre-run, just start the async simulation
                    physicsEngine.startSimulation()
                }
            } else {
                // Not initial load (e.g., node added), just start simulation
                // The new node was placed intelligently by PhysicsEngine
                physicsEngine.startSimulation()
            }
        }
    }

    /**
     * Notifies the engine of a node position update (e.g., from dragging).
     * This is always delegated to the physics engine.
     */
    fun notifyNodePositionUpdate(id: String, x: Float, y: Float, isDragging: Boolean) {
        physicsEngine.notifyNodePositionUpdate(id, x, y, isDragging)
    }

    /**
     * Stops any running simulation.
    }
     */
    fun stopSimulation() {
        physicsEngine.stopSimulation()
    }
}
