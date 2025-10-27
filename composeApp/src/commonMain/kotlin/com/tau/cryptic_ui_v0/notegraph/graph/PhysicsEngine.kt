package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.geometry.Offset
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.notegraph.graph.physics.*
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Node as SolverNode
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Body as SolverBody
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsBody as SolverPhysicsBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// Internal representation for physics simulation
data class PhysicsNode(
    val id: String,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var isFixed: Boolean = false,
    val mass: Float = 1f
)

data class PhysicsEdge(
    val id: String,
    val from: String,
    val to: String,
    val springLength: Float = 150f,
    val springConstant: Float = 0.04f
)

// Options class is now in GraphOptions.kt

class PhysicsEngine(
    initialNodes: List<NodeDisplayItem>,
    initialEdges: List<EdgeDisplayItem>,
    private val width: Float,
    private val height: Float,
    private val coroutineScope: CoroutineScope,
    private val options: PhysicsOptions = PhysicsOptions(),
    private val nodeRadius: Float = DEFAULT_NODE_RADIUS_DP.value
) {
    // Make internal so LayoutEngine and HierarchicalLayoutAlgorithm can access
    internal val nodes = mutableMapOf<String, PhysicsNode>()
    internal val edges = mutableMapOf<String, PhysicsEdge>()
    private val nodeLevels = mutableMapOf<String, Int>()

    private val _nodePositions = MutableStateFlow<Map<String, Offset>>(emptyMap())
    val nodePositionsState: StateFlow<Map<String, Offset>> = _nodePositions.asStateFlow()

    private val _isStable = MutableStateFlow(true)
    val isStable: StateFlow<Boolean> = _isStable.asStateFlow()

    private var simulationJob: Job? = null
    private var isSimulationRunning = false

    private var edgeLookup: Map<String, List<PhysicsEdge>> = emptyMap()

    init {
        // LayoutEngine will call updateData
    }

    fun updateData(newNodes: List<NodeDisplayItem>, newEdges: List<EdgeDisplayItem>) {
        updateDataInternal(newNodes, newEdges, false)
    }

    private fun updateDataInternal(newNodes: List<NodeDisplayItem>, newEdges: List<EdgeDisplayItem>, isInitial: Boolean) {
        val currentNodes = nodes.keys.toSet()
        val newNodesMap = newNodes.associateBy { it.id() }
        val newNodesIds = newNodesMap.keys

        (currentNodes - newNodesIds).forEach { nodes.remove(it) }

        newNodesMap.values.forEach { displayNode ->
            val id = displayNode.id()
            if (!nodes.containsKey(id)) {
                nodes[id] = PhysicsNode(
                    id = id,
                    x = if (isInitial) (Random.nextFloat() - 0.5f) * width * 0.5f else 0f,
                    y = if (isInitial) (Random.nextFloat() - 0.5f) * height * 0.5f else 0f
                )
            }
        }

        val currentEdges = edges.keys.toSet()
        val newEdgesMap = newEdges.associateBy { it.id() }
        val newEdgesIds = newEdgesMap.keys

        (currentEdges - newEdgesIds).forEach { edges.remove(it) }

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
                        springLength = options.defaultSpringLength,
                        springConstant = options.defaultSpringConstant
                    )
                }
            }
        }

        edgeLookup = edges.values.groupBy { it.from }

        if (isInitial || (currentNodes - newNodesIds).isNotEmpty()) {
            publishPositions()
        }
    }

    fun resetNodePositions() {
        nodes.values.forEach {
            it.x = (Random.nextFloat() - 0.5f) * width * 0.5f
            it.y = (Random.nextFloat() - 0.5f) * height * 0.5f
            it.vx = 0f
            it.vy = 0f
        }
        publishPositions()
    }

    fun updateNodeLevels(newNodeLevels: Map<String, Int>) {
        nodeLevels.clear()
        nodeLevels.putAll(newNodeLevels)
    }

    fun notifyNodePositionUpdate(id: String, x: Float, y: Float, isDragging: Boolean) {
        nodes[id]?.apply {
            this.x = x
            this.y = y
            this.isFixed = isDragging
            if (isDragging) {
                this.vx = 0f
                this.vy = 0f
            }
        }
        if (!isDragging) {
            _isStable.value = false
            startSimulation()
        }
        publishPositions()
    }

    fun publishPositions() {
        _nodePositions.value = getCurrentNodePositions()
    }

    internal fun getCurrentNodePositions(): Map<String, Offset> {
        return nodes.mapValues { Offset(it.value.x, it.value.y) }.toList().toMutableStateMap()
    }

    fun startSimulation() {
        if (isSimulationRunning && simulationJob?.isActive == true) {
            _isStable.value = false
            return
        }
        isSimulationRunning = true
        _isStable.value = false
        simulationJob?.cancel()
        simulationJob = coroutineScope.launch {
            while (isActive && !_isStable.value) {
                _isStable.value = simulateStepInternal()
                publishPositions()
                delay(16)
            }
            isSimulationRunning = false
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        isSimulationRunning = false
        _isStable.value = true
    }

    private fun simulateStepInternal(): Boolean {
        val forces = mutableMapOf<String, Offset>()
        nodes.values.forEach { forces[it.id] = Offset.Zero }

        applyRepulsionForces(forces)
        applySpringForces(forces)
        applyCentralGravity(forces)

        return updateVelocitiesAndPositions(forces)
    }

    private fun applyRepulsionForces(forces: MutableMap<String, Offset>) {
        if (nodes.isEmpty()) return

        when (options.solver) {
            SolverType.REPEL -> applyRepulsionForces_Default(forces)
            SolverType.BARNES_HUT, SolverType.FORCE_ATLAS_2, SolverType.HIERARCHICAL -> {
                val (body, physicsBody, physicsForces) = prepareSolverData()

                when (options.solver) {
                    SolverType.BARNES_HUT ->
                        BarnesHutSolver(body, physicsBody, options.barnesHut).solve()
                    SolverType.FORCE_ATLAS_2 ->
                        ForceAtlas2BasedRepulsionSolver(body, physicsBody, options.forceAtlas).solve()
                    SolverType.HIERARCHICAL ->
                        HierarchicalRepulsionSolver(body, physicsBody, options.hierarchicalRepulsion).solve()
                    else -> {}
                }

                applySolverForces(physicsForces, forces)
            }
        }
    }

    private fun applyRepulsionForces_Default(forces: MutableMap<String, Offset>) {
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

                val force = options.repulsionConstant / distanceSquared
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
    }

    private fun applySpringForces(forces: MutableMap<String, Offset>) {
        edges.values.forEach { edge ->
            val fromNode = nodes[edge.from]
            val toNode = nodes[edge.to]

            if (fromNode != null && toNode != null) {
                val dx = toNode.x - fromNode.x
                val dy = toNode.y - fromNode.y
                var distance = sqrt(dx * dx + dy * dy)
                distance = max(0.1f, distance)

                val targetLength = if (fromNode.id == toNode.id) options.selfReferenceSpringLength else edge.springLength
                val displacement = distance - targetLength
                val force = edge.springConstant * displacement

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
    }

    private fun applyCentralGravity(forces: MutableMap<String, Offset>) {
        if (options.centralGravity <= 0f) return

        nodes.values.forEach { node ->
            if (!node.isFixed) {
                val dx = -node.x
                val dy = -node.y
                val fx = dx * options.centralGravity
                val fy = dy * options.centralGravity
                forces[node.id] = forces.getValue(node.id) + Offset(fx, fy)
            }
        }
    }

    private fun updateVelocitiesAndPositions(forces: MutableMap<String, Offset>): Boolean {
        var maxVelocitySq = 0f
        val maxVel = options.maxVelocity

        nodes.values.forEach { node ->
            if (!node.isFixed) {
                val force = forces.getValue(node.id)
                val ax = force.x / node.mass
                val ay = force.y / node.mass

                node.vx = (node.vx + ax * options.timeStep) * (1f - options.damping)
                node.vy = (node.vy + ay * options.timeStep) * (1f - options.damping)

                // FIX: Enforce maxVelocity
                val velocity = sqrt(node.vx * node.vx + node.vy * node.vy)
                if (velocity > maxVel) {
                    val ratio = maxVel / velocity
                    node.vx *= ratio
                    node.vy *= ratio
                }

                node.x += node.vx * options.timeStep
                node.y += node.vy * options.timeStep

                val velocitySq = node.vx * node.vx + node.vy * node.vy
                if (velocitySq > maxVelocitySq) {
                    maxVelocitySq = velocitySq
                }
            } else {
                node.vx = 0f
                node.vy = 0f
            }
        }
        // FIX: Compare against minVelocity, not minVelocity * minVelocity
        return sqrt(maxVelocitySq) < options.minVelocity
    }

    private fun prepareSolverData(): Triple<SolverBody, SolverPhysicsBody, MutableMap<String, Point>> {
        val physicsForces = mutableMapOf<String, Point>()

        val solverNodes = nodes.mapValues { (id, node) ->
            physicsForces[id] = Point(0.0, 0.0)
            SolverNode(
                id = id,
                x = node.x.toDouble(),
                y = node.y.toDouble(),
                options = NodeOptions(
                    mass = node.mass.toDouble(),
                    fixed = FixedOptions(x = node.isFixed, y = node.isFixed)
                ),
                shape = Shape(radius = nodeRadius.toDouble()),
                level = nodeLevels[id] ?: 0,
                edges = (edgeLookup[id] ?: emptyList()) + edges.values.filter { it.to == id }
            )
        }

        val nodeIndices = nodes.keys.toList()
        val body = object : SolverBody { override val nodes = solverNodes }
        val physicsBody = object : SolverPhysicsBody {
            override val physicsNodeIndices = nodeIndices
            override val forces = physicsForces
        }

        return Triple(body, physicsBody, physicsForces)
    }

    private fun applySolverForces(
        physicsForces: Map<String, Point>,
        forces: MutableMap<String, Offset>
    ) {
        physicsForces.forEach { (id, force) ->
            forces[id] = forces.getValue(id) + Offset(force.x.toFloat(), force.y.toFloat())
        }
    }
}

fun NodeDisplayItem.id(): String = "${this.label}_${this.primarykeyProperty.value?.toString()}"
fun EdgeDisplayItem.id(): String = "${this.src.id()}_${this.label}_${this.dst.id()}"