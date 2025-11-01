package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.geometry.Offset
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.notegraph.graph.physics.*

import com.tau.cryptic_ui_v0.notegraph.graph.physics.Node as PhysicsSolverNode
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Body as PhysicsSolverBody
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsBody as PhysicsSolverPhysicsBody

import com.tau.cryptic_ui_v0.notegraph.graph.physics.Edge
import com.tau.cryptic_ui_v0.notegraph.graph.physics.EdgeOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.EdgeType

import com.tau.kt_vis_network.network.physics.solvers.BarnesHutSolver
import com.tau.kt_vis_network.network.physics.solvers.CentralGravitySolver
import com.tau.kt_vis_network.network.physics.solvers.ForceAtlas2BasedCentralGravitySolver
import com.tau.kt_vis_network.network.physics.solvers.ForceAtlas2BasedRepulsionSolver
import com.tau.kt_vis_network.network.physics.solvers.HierarchicalRepulsionSolver
import com.tau.kt_vis_network.network.physics.solvers.HierarchicalSpringSolver
import com.tau.kt_vis_network.network.physics.solvers.RepulsionSolver
import com.tau.kt_vis_network.network.physics.solvers.SpringSolver

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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random


class PhysicsEngine(
    initialNodes: List<NodeDisplayItem>,
    initialEdges: List<EdgeDisplayItem>,
    private val width: Float,
    private val height: Float,
    private val coroutineScope: CoroutineScope,
    private val options: PhysicsOptions, // Now required
    private val nodeRadius: Float = DEFAULT_NODE_RADIUS_DP.value
) {
    internal val nodes = mutableMapOf<String, PhysicsNode>()
    internal val edges = mutableMapOf<String, PhysicsEdge>()
    // --- REMOVED: nodeLevels map (level is now in PhysicsNode) ---
    // private val nodeLevels = mutableMapOf<String, Int>()

    private val _nodePositions = MutableStateFlow<Map<String, Offset>>(emptyMap())
    val nodePositionsState: StateFlow<Map<String, Offset>> = _nodePositions.asStateFlow()

    // --- NEW: Simulation Status Flow ---
    private val _status = MutableStateFlow<SimulationStatus>(SimulationStatus.Stable)
    val status: StateFlow<SimulationStatus> = _status.asStateFlow()

    private var simulationJob: Job? = null
    private var isSimulationRunning = false

    // --- NEW: State for adaptive timestep and stabilization ---
    private var stabilizationCounter = 0
    private var maxVelocityLastFrame: Float = 0f

    private var edgeLookup: Map<String, List<PhysicsEdge>> = emptyMap()

    fun updateData(newNodes: List<NodeDisplayItem>, newEdges: List<EdgeDisplayItem>, isInitial: Boolean = false) {
        updateDataInternal(newNodes, newEdges, isInitial)
    }

    internal fun updateDataInternal(newNodes: List<NodeDisplayItem>, newEdges: List<EdgeDisplayItem>, isInitial: Boolean) {
        val currentNodes = nodes.keys.toSet()
        val newNodesMap = newNodes.associateBy { it.id() }
        val newNodesIds = newNodesMap.keys

        (currentNodes - newNodesIds).forEach { nodes.remove(it) }

        val nodesAdded = (newNodesIds - currentNodes).isNotEmpty()

        val initialPositions = mutableMapOf<String, Offset>()
        if (isInitial) {
            val nodeCount = newNodes.size
            if (nodeCount > 0) {
                val goldenAngle = (Math.PI * (3.0 - sqrt(5.0))).toFloat()
                val maxRadius = min(width, height) * 0.4f
                val c = if (nodeCount > 1) maxRadius / sqrt(nodeCount.toFloat()) else 0f

                newNodes.forEachIndexed { index, node ->
                    val radius = c * sqrt(index.toFloat() + 1)
                    val angle = index * goldenAngle
                    val x = (radius * cos(angle)).toFloat()
                    val y = (radius * sin(angle)).toFloat()
                    initialPositions[node.id()] = Offset(x, y)
                }
            }
        }

        newNodesMap.values.forEach { displayNode ->
            val id = displayNode.id()
            if (!nodes.containsKey(id)) {
                val (startX, startY) = if (isInitial) {
                    val pos = initialPositions[id] ?: Offset(0f, 0f)
                    pos.x to pos.y
                } else {
                    val neighborIds = newEdges.filter { it.src.id() == id || it.dst.id() == id }
                        .map { if (it.src.id() == id) it.dst.id() else it.src.id() }
                        .toSet()
                    val existingNeighbors = nodes.values.filter { it.id in neighborIds }
                    if (existingNeighbors.isNotEmpty()) {
                        existingNeighbors.map { it.x }.average().toFloat() to
                                existingNeighbors.map { it.y }.average().toFloat()
                    } else if (nodes.isNotEmpty()) {
                        nodes.values.map { it.x }.average().toFloat() to
                                nodes.values.map { it.y }.average().toFloat()
                    } else {
                        0f to 0f
                    }
                }
                nodes[id] = PhysicsNode(id = id, x = startX, y = startY)
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

        if (isInitial) {
            publishPositions()
        }

        if (nodesAdded && !isInitial) {
            destabilize()
        }
    }

    fun resetNodePositions() {
        val nodeCount = nodes.size
        if (nodeCount == 0) {
            publishPositions()
            return
        }

        val goldenAngle = (Math.PI * (3.0 - sqrt(5.0))).toFloat()
        val maxRadius = min(width, height) * 0.4f
        val c = if (nodeCount > 1) maxRadius / sqrt(nodeCount.toFloat()) else 0f

        nodes.values.toList().forEachIndexed { index, node ->
            val radius = c * sqrt(index.toFloat() + 1)
            val angle = index * goldenAngle
            node.x = (radius * cos(angle)).toFloat()
            node.y = (radius * sin(angle)).toFloat()
            node.vx = 0f
            node.vy = 0f
        }

        publishPositions()
        destabilize()
        startSimulation()
    }

    fun updateNodeLevels(newNodeLevels: Map<String, Int>) {
        // --- UPDATED: Set level directly on PhysicsNode ---
        newNodeLevels.forEach { (id, level) ->
            nodes[id]?.level = level
        }
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
            destabilize()
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

    /** Sets the simulation status to Running and resets stabilization. */
    private fun destabilize() {
        stabilizationCounter = 0
        _status.value = SimulationStatus.Running
    }

    fun preRunSimulation(steps: Int) {
        if (steps <= 0) return

        destabilize()
        var i = 0
        while (i < steps && _status.value != SimulationStatus.Stable) {
            // 1. Calculate timestep
            val timeStep = if (options.adaptiveTimeStep) {
                calculateAdaptiveTimeStep()
            } else {
                options.timeStep
            }

            // 2. Calculate forces
            val forces = mutableMapOf<String, Offset>()
            nodes.values.forEach { forces[it.id] = Offset.Zero }
            simulateStepInternal(forces) // Applies forces

            // 3. Update positions and get velocity
            val maxVelocity = updateVelocitiesAndPositions(forces, timeStep)
            maxVelocityLastFrame = maxVelocity

            // 4. Update status
            updateSimulationStatus(maxVelocity)
            i++
        }

        publishPositions()

        if (_status.value != SimulationStatus.Stable) {
            startSimulation() // Start async if not stable
        }
    }

    fun startSimulation() {
        if (isSimulationRunning && simulationJob?.isActive == true) {
            destabilize() // Reset stabilization counter if already running
            return
        }
        isSimulationRunning = true
        destabilize()

        simulationJob?.cancel()
        simulationJob = coroutineScope.launch {
            while (isActive && _status.value != SimulationStatus.Stable) {
                // 1. Calculate timestep
                val timeStep = if (options.adaptiveTimeStep) {
                    calculateAdaptiveTimeStep()
                } else {
                    options.timeStep
                }

                // 2. Calculate forces
                val forces = mutableMapOf<String, Offset>()
                nodes.values.forEach { forces[it.id] = Offset.Zero }
                simulateStepInternal(forces) // Applies forces

                // 3. Update positions and get velocity
                val maxVelocity = updateVelocitiesAndPositions(forces, timeStep)
                maxVelocityLastFrame = maxVelocity

                // 4. Publish positions
                publishPositions()

                // 5. Update status
                updateSimulationStatus(maxVelocity)

                // 6. Delay
                delay(16)
            }
            isSimulationRunning = false
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        isSimulationRunning = false
        _status.value = SimulationStatus.Stable
    }

    /**
     * NEW: Calculates an adaptive timestep based on the max velocity of the previous frame.
     * Logic adapted from vis.js port.
     */
    private fun calculateAdaptiveTimeStep(): Float {
        val velocity = maxVelocityLastFrame.coerceAtLeast(options.minVelocity)
        val targetVelocity = 15f // Empirical value for "normal" speed
        val scalingFactor = options.adaptiveTimeStepScaling

        // Calculate dynamic timestep
        var timeStep = (targetVelocity / velocity) * options.maxTimeStep * scalingFactor

        // Clamp it
        return timeStep.coerceIn(options.minTimeStep, options.maxTimeStep)
    }

    /**
     * NEW: Checks max velocity and updates the simulation status, handling
     * the robust stabilization counter.
     */
    private fun updateSimulationStatus(maxVelocity: Float) {
        if (maxVelocity < options.minVelocity) {
            stabilizationCounter++
            if (stabilizationCounter >= options.stabilizationIterations) {
                _status.value = SimulationStatus.Stable
            } else {
                _status.value = SimulationStatus.Stabilizing(
                    stabilizationCounter.toFloat() / options.stabilizationIterations
                )
            }
        } else {
            stabilizationCounter = 0
            _status.value = SimulationStatus.Running
        }
    }

    /**
     * This step now *only* applies forces to the forces map.
     * It no longer updates positions or returns a boolean.
     */
    private fun simulateStepInternal(forces: MutableMap<String, Offset>) {
        // --- UPDATED: Prepare data once for all solvers ---
        val (body, physicsBody) = prepareSolverData()

        // --- UPDATED: Select solvers based on options ---
        applyRepulsionForces(body, physicsBody, forces)
        applySpringForces(body, physicsBody, forces)
        applyGravityForces(body, physicsBody, forces)

        // --- UPDATED: Apply solver forces back ---
        applySolverForces(physicsBody.forces, forces)
    }

    // --- UPDATED: Renamed and modified to select correct solver ---
    private fun applyRepulsionForces(
        body: PhysicsSolverBody,
        physicsBody: PhysicsSolverPhysicsBody,
        forces: MutableMap<String, Offset>
    ) {
        if (nodes.isEmpty()) return

        when (options.solver) {
            SolverType.REPEL -> {
                // --- Pass options.repulsion, which now exists ---
                RepulsionSolver(body, physicsBody, options.repulsion).solve()
                // applyRepulsionForces_Default(forces) // Use lightweight default
            }
            SolverType.BARNES_HUT ->
                BarnesHutSolver(body, physicsBody, options.barnesHut).solve()
            SolverType.FORCE_ATLAS_2 ->
                ForceAtlas2BasedRepulsionSolver(body, physicsBody, options.forceAtlas).solve()
            SolverType.HIERARCHICAL ->
                HierarchicalRepulsionSolver(body, physicsBody, options.hierarchicalRepulsion).solve()
        }
    }

    // --- NEW: Selects the correct spring solver ---
    private fun applySpringForces(
        body: PhysicsSolverBody,
        physicsBody: PhysicsSolverPhysicsBody,
        forces: MutableMap<String, Offset>
    ) {
        val solverOptions = when (options.solver) {
            SolverType.HIERARCHICAL -> options.hierarchicalRepulsion
            SolverType.BARNES_HUT -> options.barnesHut
            SolverType.FORCE_ATLAS_2 -> options.forceAtlas
            SolverType.REPEL -> options.repulsion
        }

        // --- UPDATED: Use imported Edge class ---
        val solverEdges = edges.values.mapNotNull {
            val fromNode = body.nodes[it.from]
            val toNode = body.nodes[it.to]
            if (fromNode != null && toNode != null) {
                Edge(
                    id = it.id,
                    fromId = it.from,
                    toId = it.to,
                    options = EdgeOptions(length = it.springLength.toDouble()),
                    // Stubs for data solvers expect
                    from = fromNode,
                    to = toNode,
                    connected = true,
                    edgeType = EdgeType(null)
                )
            } else {
                null // One of the nodes doesn't exist, skip this edge
            }
        }.associateBy { it.id }

        val solverBodyWithEdges = object : PhysicsSolverBody {
            override val nodes = body.nodes
            override val edges = solverEdges // Add edges
        }

        val solverPhysicsBodyWithEdges = object : PhysicsSolverPhysicsBody {
            override val physicsNodeIndices = physicsBody.physicsNodeIndices
            override val forces = physicsBody.forces
            override val physicsEdgeIndices = solverEdges.keys.toList() // Add edge indices
        }

        when (options.solver) {
            SolverType.HIERARCHICAL ->
                HierarchicalSpringSolver(solverBodyWithEdges, solverPhysicsBodyWithEdges, solverOptions).solve()
            else ->
                SpringSolver(solverBodyWithEdges, solverPhysicsBodyWithEdges, solverOptions).solve()
        }
    }

    // --- NEW: Selects the correct gravity solver ---
    private fun applyGravityForces(
        body: PhysicsSolverBody,
        physicsBody: PhysicsSolverPhysicsBody,
        forces: MutableMap<String, Offset>
    ) {
        val solverOptions = when (options.solver) {
            SolverType.FORCE_ATLAS_2 -> options.forceAtlas
            SolverType.HIERARCHICAL -> options.hierarchicalRepulsion
            SolverType.BARNES_HUT -> options.barnesHut
            SolverType.REPEL -> options.repulsion
        }

        when (options.solver) {
            SolverType.FORCE_ATLAS_2 ->
                ForceAtlas2BasedCentralGravitySolver(body, physicsBody, solverOptions).solve()
            else ->
                // barnesHut, repel, hierarchical all use central gravity
                CentralGravitySolver(body, physicsBody, solverOptions).solve()
        }
    }

    // ---

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

    /**
     * This function now takes timestep as a parameter and returns the max velocity.
     */
    private fun updateVelocitiesAndPositions(forces: MutableMap<String, Offset>, timeStep: Float): Float {
        var maxVelocitySq = 0f
        val maxVel = options.maxVelocity

        nodes.values.forEach { node ->
            if (!node.isFixed) {
                val force = forces.getValue(node.id)
                val ax = force.x / node.mass
                val ay = force.y / node.mass

                node.vx = (node.vx + ax * timeStep) * (1f - options.damping)
                node.vy = (node.vy + ay * timeStep) * (1f - options.damping)

                val velocity = sqrt(node.vx * node.vx + node.vy * node.vy)
                if (velocity > maxVel) {
                    val ratio = maxVel / velocity
                    node.vx *= ratio
                    node.vy *= ratio
                }

                node.x += node.vx * timeStep
                node.y += node.vy * timeStep

                val velocitySq = node.vx * node.vx + node.vy * node.vy
                if (velocitySq > maxVelocitySq) {
                    maxVelocitySq = velocitySq
                }
            } else {
                node.vx = 0f
                node.vy = 0f
            }
        }
        return sqrt(maxVelocitySq)
    }

    // --- Data Preparation and Application for Solvers (Unchanged) ---

    // --- UPDATED: Renamed return types, updated PhysicsSolverNode creation ---

    private fun prepareSolverData(): Pair<PhysicsSolverBody, PhysicsSolverPhysicsBody> {
        val physicsForces = mutableMapOf<String, Point>()

        // Create a single dummy node to satisfy the Edge constructor.
        // It won't be used, but it's required by the data class.
        val dummyNode = PhysicsSolverNode(
            id = "dummy", x = 0.0, y = 0.0,
            options = NodeOptions(mass = 1.0, fixed = FixedOptions(false, false)),
            shape = Shape(),
            level = 0,
            edges = emptyList()
        )

        lateinit var body: PhysicsSolverBody

        val solverNodes = nodes.mapValues { (id, node) ->
            physicsForces[id] = Point(0.0, 0.0)
            PhysicsSolverNode(
                id = id,
                x = node.x.toDouble(),
                y = node.y.toDouble(),
                options = NodeOptions(
                    mass = node.mass.toDouble(),
                    fixed = FixedOptions(x = node.isFixed, y = node.isFixed)
                ),
                shape = Shape(radius = nodeRadius.toDouble()),
                level = node.level,
                edges = (edgeLookup[id] ?: emptyList()).map { edge ->
                    // We are creating a *fake* Edge object here just to satisfy
                    // the type system for the node.edges list.
                    // This list is ONLY used for .size by ForceAtlas2.
                    // The `from` and `to` nodes are dummies.
                    Edge(
                        id = edge.id,
                        fromId = edge.from,
                        toId = edge.to,
                        options = EdgeOptions(length = edge.springLength.toDouble()),
                        from = dummyNode, // Use dummy
                        to = dummyNode,   // Use dummy
                        connected = true,
                        edgeType = EdgeType(null)
                    )
                }
            )
        }

        val nodeIndices = nodes.keys.toList()

        body = object : PhysicsSolverBody {
            override val nodes = solverNodes
            // This body's edges are not used by Repulsion or Gravity solvers.
            // SpringSolver creates its own body with edges.
            override val edges = emptyMap<String, Edge>()
        }

        val physicsBody = object : PhysicsSolverPhysicsBody {
            override val physicsNodeIndices = nodeIndices
            override val forces = physicsForces
            // SpringSolver creates its own physicsBody with edge indices.
            override val physicsEdgeIndices = emptyList<String>()
        }

        return Pair(body, physicsBody)
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


fun NodeDisplayItem.id(): String = "${this.label}_${this.displayProperty?.toString()}"
fun EdgeDisplayItem.id(): String = "${this.src.id()}_${this.label}_${this.dst.id()}"


