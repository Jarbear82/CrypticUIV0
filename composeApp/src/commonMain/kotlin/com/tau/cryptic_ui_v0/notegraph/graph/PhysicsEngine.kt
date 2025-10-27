package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.geometry.Offset
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

// Internal representation for physics simulation
data class PhysicsNode(
    val id: String,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var isFixed: Boolean = false, // Keep track of temporary fixing during drag
    val mass: Float = 1f          // Default mass
)

data class PhysicsEdge(
    val id: String,
    val from: String,
    val to: String,
    val springLength: Float = 150f, // Default spring length
    val springConstant: Float = 0.04f // Default spring constant
)

// Options class for Physics Engine
data class PhysicsOptions(
    val repulsionConstant: Float = -800f,
    val damping: Float = 0.09f,
    val timeStep: Float = 0.5f,
    val minVelocity: Float = 0.05f,
    val selfReferenceSpringLength: Float = 80f,
    val defaultSpringLength: Float = 150f,
    val defaultSpringConstant: Float = 0.04f
)

class PhysicsEngine(
    initialNodes: List<NodeDisplayItem>,
    initialEdges: List<EdgeDisplayItem>,
    private val width: Float,
    private val height: Float,
    private val coroutineScope: CoroutineScope, // Inject scope for simulation loop
    private val options: PhysicsOptions = PhysicsOptions() // Use options class
) {
    internal val nodes = mutableMapOf<String, PhysicsNode>()
    private val edges = mutableMapOf<String, PhysicsEdge>()

    // Expose node positions as StateFlow
    private val _nodePositions = MutableStateFlow<Map<String, Offset>>(emptyMap())
    val nodePositionsState: StateFlow<Map<String, Offset>> = _nodePositions.asStateFlow()

    // Expose stabilization state
    private val _isStable = MutableStateFlow(true)
    val isStable: StateFlow<Boolean> = _isStable.asStateFlow()

    private var simulationJob: Job? = null
    private var isSimulationRunning = false

    init {
        updateDataInternal(initialNodes, initialEdges, true) // Initial setup
        startSimulation() // Start simulation on init
    }

    // Public method to update data and potentially restart simulation
    fun updateData(newNodes: List<NodeDisplayItem>, newEdges: List<EdgeDisplayItem>) {
        updateDataInternal(newNodes, newEdges, false) // Don't reset positions for updates
        // Decide if simulation needs restarting based on changes (e.g., new nodes/edges)
        startSimulation() // For simplicity, always restart on data change for now
    }

    // Internal method to handle data updates
    private fun updateDataInternal(newNodes: List<NodeDisplayItem>, newEdges: List<EdgeDisplayItem>, isInitial: Boolean) {
        val currentNodes = nodes.keys.toSet()
        val newNodesMap = newNodes.associateBy { it.id() }
        val newNodesIds = newNodesMap.keys

        // Remove nodes no longer present
        (currentNodes - newNodesIds).forEach { nodes.remove(it) }

        // Add or update nodes
        newNodesMap.values.forEach { displayNode ->
            val id = displayNode.id()
            if (!nodes.containsKey(id)) {
                // Add new node
                nodes[id] = PhysicsNode(
                    id = id,
                    x = if (isInitial) (Random.nextFloat() - 0.5f) * width * 0.5f else 0f,
                    y = if (isInitial) (Random.nextFloat() - 0.5f) * height * 0.5f else 0f
                )
            } else if (!isInitial) {
                // If not initial load and node exists, don't reset position here
                // We might update fixed status or mass later if needed
            }
        }

        // --- Update Edges ---
        val currentEdges = edges.keys.toSet()
        val newEdgesMap = newEdges.associateBy { it.id() }
        val newEdgesIds = newEdgesMap.keys

        // Remove edges no longer present
        (currentEdges - newEdgesIds).forEach { edges.remove(it) }

        // Add or update edges
        newEdgesMap.values.forEach { displayEdge ->
            val edgeId = displayEdge.id()
            if (!edges.containsKey(edgeId)) {
                val fromId = displayEdge.src.id()
                val toId = displayEdge.dst.id()
                if (nodes.containsKey(fromId) && nodes.containsKey(toId)) {
                    edges[edgeId] = PhysicsEdge(
                        id = edgeId,
                        from = fromId,
                        to = toId,
                        springLength = options.defaultSpringLength, // Use options
                        springConstant = options.defaultSpringConstant // Use options
                    )
                }
            }
        }

        // Immediately update position state if initial or if nodes were removed
        if (isInitial || (currentNodes - newNodesIds).isNotEmpty()) {
            _nodePositions.value = getCurrentNodePositions()
        }
    }


    fun getNodePosition(id: String): Offset? {
        return nodes[id]?.let { Offset(it.x, it.y) }
    }

    // Renamed for clarity - used by InteractionHandler
    fun notifyNodePositionUpdate(id: String, x: Float, y: Float, isDragging: Boolean) {
        nodes[id]?.apply {
            this.x = x
            this.y = y
            // Fix node during drag, reset velocity
            this.isFixed = isDragging
            if (isDragging) {
                this.vx = 0f
                this.vy = 0f
            }
        }
        // If dragging stopped, ensure simulation runs
        if (!isDragging) {
            _isStable.value = false
            startSimulation()
        }
        // Publish update immediately for responsiveness during drag
        _nodePositions.value = getCurrentNodePositions()
    }


    private fun getCurrentNodePositions(): Map<String, Offset> {
        return nodes.mapValues { Offset(it.value.x, it.value.y) }.toList().toMutableStateMap() // Create a new map to trigger state update
    }

    fun startSimulation() {
        if (isSimulationRunning && simulationJob?.isActive == true) {
            _isStable.value = false // Ensure it runs if already running but stabilization needed
            return
        }
        isSimulationRunning = true
        _isStable.value = false
        simulationJob?.cancel() // Cancel previous job if any
        simulationJob = coroutineScope.launch {
            while (isActive && !_isStable.value) {
                _isStable.value = simulateStepInternal()
                // Update the StateFlow with the new positions
                _nodePositions.value = getCurrentNodePositions()
                delay(16) // ~60 FPS
            }
            isSimulationRunning = false // Mark simulation as stopped
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        isSimulationRunning = false
        _isStable.value = true // Assume stable when stopped externally
    }

    // Renamed to internal simulation step logic
    private fun simulateStepInternal(): Boolean {

        val forces = mutableMapOf<String, Offset>()
        nodes.values.forEach { forces[it.id] = Offset.Zero }

        // 1. Calculate Repulsion Forces
        val nodeValues = nodes.values.toList()
        for (i in nodeValues.indices) {
            for (j in i + 1 until nodeValues.size) {
                val node1 = nodeValues[i]
                val node2 = nodeValues[j]

                val dx = node2.x - node1.x
                val dy = node2.y - node1.y
                var distanceSquared = dx * dx + dy * dy
                if (distanceSquared < 1f) distanceSquared = 1f
                val distance = sqrt(distanceSquared)

                val force = options.repulsionConstant / distanceSquared // Use options
                val fx = force * dx / distance
                val fy = force * dy / distance

                if (!node1.isFixed) {
                    forces[node1.id] = forces.getValue(node1.id) - Offset(fx, fy)
                }
                if (!node2.isFixed) {
                    forces[node2.id] = forces.getValue(node2.id) + Offset(fx, fy)
                }
            }
        }

        // 2. Calculate Spring Forces for Edges
        edges.values.forEach { edge ->
            val fromNode = nodes[edge.from]
            val toNode = nodes[edge.to]

            if (fromNode != null && toNode != null) {
                val dx = toNode.x - fromNode.x
                val dy = toNode.y - fromNode.y
                var distance = sqrt(dx * dx + dy * dy)
                distance = max(0.1f, distance)

                val targetLength = if (fromNode.id == toNode.id) options.selfReferenceSpringLength else edge.springLength // Use options
                val displacement = distance - targetLength
                val force = edge.springConstant * displacement // Use edge-specific or default

                val fx = (force * dx / distance)
                val fy = (force * dy / distance)

                if (!fromNode.isFixed) {
                    forces[fromNode.id] = forces.getValue(fromNode.id) + Offset(fx, fy)
                }
                if (!toNode.isFixed) {
                    forces[toNode.id] = forces.getValue(toNode.id) - Offset(fx, fy)
                }
            }
        }

        // 3. Update Velocities and Positions
        var maxVelocitySq = 0f
        nodes.values.forEach { node ->
            if (!node.isFixed) {
                val force = forces.getValue(node.id)
                val ax = force.x / node.mass
                val ay = force.y / node.mass

                node.vx = (node.vx + ax * options.timeStep) * (1f - options.damping) // Use options
                node.vy = (node.vy + ay * options.timeStep) * (1f - options.damping) // Use options

                node.x += node.vx * options.timeStep // Use options
                node.y += node.vy * options.timeStep // Use options

                val velocitySq = node.vx * node.vx + node.vy * node.vy
                if (velocitySq > maxVelocitySq) {
                    maxVelocitySq = velocitySq
                }
            } else {
                node.vx = 0f
                node.vy = 0f
            }
        }
        // Check stability based on max velocity squared
        return maxVelocitySq < options.minVelocity * options.minVelocity // Use options
    }
}

// Keep the extension functions in the same file or make PhysicsNode public
fun NodeDisplayItem.id(): String = "${this.label}_${this.primarykeyProperty.value?.toString()}"
fun EdgeDisplayItem.id(): String = "${this.src.id()}_${this.label}_${this.dst.id()}"