package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.ui.geometry.Offset
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val isStable: StateFlow<Boolean> = physicsEngine.isStable

    init {
        // Perform initial layout calculation on init
        updateData(initialNodes, initialEdges, isInitialLoad = true)
    }

    /**
     * Updates the data for the layout.
     * This will trigger either a hierarchical calculation or restart the physics simulation.
     */
    fun updateData(newNodes: List<NodeDisplayItem>, newEdges: List<EdgeDisplayItem>, isInitialLoad: Boolean = false) {
        // 1. Update the physics engine's internal data representation
        physicsEngine.updateData(newNodes, newEdges)

        // 2. Decide which layout strategy to use
        if (layoutOptions.hierarchical.enabled) {
            // --- Hierarchical Layout ---
            physicsEngine.stopSimulation()

            // Calculate static positions
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
        } else {
            // --- Physics-Based Layout ---
            // If it's the first load, reset positions to random
            if (isInitialLoad) {
                physicsEngine.resetNodePositions()
            }
            // Start (or restart) the simulation
            physicsEngine.startSimulation()
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
     */
    fun stopSimulation() {
        physicsEngine.stopSimulation()
    }
}