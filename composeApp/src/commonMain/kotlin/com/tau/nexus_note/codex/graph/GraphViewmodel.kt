package com.tau.nexus_note.codex.graph

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.datamodels.GraphNode
import com.tau.nexus_note.datamodels.InternalGraph
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.TransformState
import com.tau.nexus_note.codex.graph.physics.PhysicsEngine
import com.tau.nexus_note.codex.graph.physics.PhysicsOptions
import com.tau.nexus_note.codex.graph.physics.runFRLayout
import com.tau.nexus_note.utils.labelToColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class GraphViewmodel(
    private val viewModelScope: CoroutineScope
) {
    // A separate physics engine for the main graph
    private val physicsEngine = PhysicsEngine()

    // --- ADDED: A map to hold a unique physics engine for each Supernode ---
    private val internalPhysicsEngines = mutableMapOf<Long, PhysicsEngine>()

    private val _physicsOptions = MutableStateFlow(PhysicsOptions(gravity = 0.5f))
    val physicsOptions = _physicsOptions.asStateFlow()

    private val _graphNodes = MutableStateFlow<Map<Long, GraphNode>>(emptyMap())
    val graphNodes = _graphNodes.asStateFlow()

    private val _graphEdges = MutableStateFlow<List<GraphEdge>>(emptyList())
    val graphEdges = _graphEdges.asStateFlow()

    private val _transform = MutableStateFlow(TransformState())
    val transform = _transform.asStateFlow()

    private val _simulationRunning = MutableStateFlow(true)
    val simulationRunning = _simulationRunning.asStateFlow()

    private val _draggedNodeId = MutableStateFlow<Long?>(null)
    private val _dragVelocity = MutableStateFlow(Offset.Zero)

    private val _showFabMenu = MutableStateFlow(false)
    val showFabMenu = _showFabMenu.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    private val _isDetangling = MutableStateFlow(false)
    val isDetangling = _isDetangling.asStateFlow()

    private val _showDetangleDialog = MutableStateFlow(false)
    val showDetangleDialog = _showDetangleDialog.asStateFlow()

    private var size = Size.Zero

    suspend fun runSimulationLoop() {
        if (!_simulationRunning.value) return

        var lastTimeNanos = withFrameNanos { it }
        while (_simulationRunning.value) {
            val currentTimeNanos = withFrameNanos { it }
            val dt = (currentTimeNanos - lastTimeNanos) / 1_000_000_000.0f
            lastTimeNanos = currentTimeNanos

            if (_graphNodes.value.isNotEmpty()) {
                val currentNodes = _graphNodes.value

                // 1. Run MAIN simulation
                val updatedMainNodes = physicsEngine.update(
                    currentNodes,
                    _graphEdges.value,
                    _physicsOptions.value,
                    dt.coerceAtMost(0.032f)
                )

                val finalNodes = updatedMainNodes.toMutableMap()

                // 2. Run INTERNAL simulations for each Supernode
                for ((supernodeId, internalEngine) in internalPhysicsEngines) {
                    val supernode = finalNodes[supernodeId]
                    val internalGraph = supernode?.internalGraph

                    if (supernode == null || internalGraph == null) {
                        // Supernode was deleted, clean up its engine
                        internalPhysicsEngines.remove(supernodeId)
                        continue
                    }

                    // Use strong gravity to keep internal nodes clustered
                    // around their local (0,0) center.
                    val internalOptions = _physicsOptions.value.copy(gravity = 5.0f)

                    val updatedInternalNodes = internalEngine.update(
                        internalGraph.nodes,
                        internalGraph.edges,
                        internalOptions,
                        dt.coerceAtMost(0.032f)
                    )

                    // 3. Store updated internal nodes back into the supernode
                    val updatedInternalGraph = internalGraph.copy(nodes = updatedInternalNodes)
                    finalNodes[supernodeId] = supernode.copy(
                        internalGraph = updatedInternalGraph
                    )
                }

                // 4. Commit all changes for this frame
                _graphNodes.value = finalNodes
            }
        }
    }

    /**
     * Public method for CodexViewModel to push new data into the graph.
     * This logic is now responsible for partitioning the graph.
     */
    fun updateGraphData(nodeList: List<NodeDisplayItem>, edgeList: List<EdgeDisplayItem>) {
        val currentNodes = _graphNodes.value

        // --- Step 1: Create GraphNode/GraphEdge for ALL items ---
        // This map contains every node, including ones that will be encapsulated
        val edgeCountByNodeId = mutableMapOf<Long, Int>()
        edgeList.forEach { edge ->
            edgeCountByNodeId[edge.src.id] = (edgeCountByNodeId[edge.src.id] ?: 0) + 1
            edgeCountByNodeId[edge.dst.id] = (edgeCountByNodeId[edge.dst.id] ?: 0) + 1
        }

        val fullNodeMap = nodeList.associate { node ->
            val id = node.id
            val edgeCount = edgeCountByNodeId[id] ?: 0
            val radius = _physicsOptions.value.nodeBaseRadius + (edgeCount * _physicsOptions.value.nodeRadiusEdgeFactor)
            val mass = (edgeCount + 1).toFloat()
            val existingNode = currentNodes[id]

            val newNode = if (existingNode != null && !existingNode.isSupernode) {
                // Preserve position/velocity for existing non-supernodes
                existingNode.copy(
                    label = node.label,
                    displayProperty = node.displayProperty,
                    mass = mass,
                    radius = radius,
                    colorInfo = labelToColor(node.label)
                )
            } else {
                // Create new node or overwrite existing supernode
                GraphNode(
                    id = id,
                    label = node.label,
                    displayProperty = node.displayProperty,
                    pos = existingNode?.pos ?: Offset(Random.nextFloat() * 100 - 50, Random.nextFloat() * 100 - 50),
                    vel = existingNode?.vel ?: Offset.Zero,
                    mass = mass,
                    radius = radius,
                    colorInfo = labelToColor(node.label),
                    isFixed = existingNode?.isFixed ?: false
                )
            }
            id to newNode
        }

        val fullEdgeList = edgeList.map { edge ->
            GraphEdge(
                id = edge.id,
                sourceId = edge.src.id,
                targetId = edge.dst.id,
                label = edge.label,
                strength = 1.0f,
                colorInfo = labelToColor(edge.label)
            )
        }
        val fullEdgeMap = fullEdgeList.associateBy { it.id }

        // --- Step 2: Identify Supernodes and Internal Components ---
        val internalNodeIds = mutableSetOf<Long>()
        val internalEdgeIds = mutableSetOf<Long>()
        val internalGraphMap = mutableMapOf<Long, InternalGraph>()
        val nodeToSupernodeMap = mutableMapOf<Long, Long>() // <InternalNodeID, SupernodeID>

        val supernodeDisplayItems = nodeList.filter { it.encapsulatedEdgeIds.isNotEmpty() }

        for (supernodeItem in supernodeDisplayItems) {
            val supernodeId = supernodeItem.id

            // Find all internal edges
            val internalEdges = supernodeItem.encapsulatedEdgeIds.mapNotNull { fullEdgeMap[it] }
            internalEdgeIds.addAll(supernodeItem.encapsulatedEdgeIds)

            // Find all unique internal nodes from those edges
            val internalNodesFromEdges = internalEdges
                .flatMap { listOf(it.sourceId, it.targetId) }
                .toSet()

            internalNodeIds.addAll(internalNodesFromEdges)

            val internalNodeMap = internalNodesFromEdges.mapNotNull { fullNodeMap[it] }
                .associateBy { it.id }
                .mapValues { (id, node) ->
                    // If this node is *already* in an internal engine, keep its relative pos
                    val existingInternalPos = currentNodes[supernodeId]?.internalGraph?.nodes?.get(id)?.pos
                    node.copy(pos = existingInternalPos ?: Offset(Random.nextFloat() * 10 - 5, Random.nextFloat() * 10 - 5))
                }

            // Map internal nodes to this supernode for edge retargeting
            internalNodesFromEdges.forEach { nodeId ->
                // Basic implementation: first supernode to claim a node wins
                if (!nodeToSupernodeMap.containsKey(nodeId)) {
                    nodeToSupernodeMap[nodeId] = supernodeId
                }
            }

            internalGraphMap[supernodeId] = InternalGraph(internalNodeMap, internalEdges)
        }

        // --- Step 3: Create Final Graph for Main Simulation ---

        // Filter out internal edges and retarget external edges
        val finalEdges = fullEdgeList
            .filter { it.id !in internalEdgeIds }
            .map { edge ->
                val sourceId = nodeToSupernodeMap[edge.sourceId] ?: edge.sourceId
                val targetId = nodeToSupernodeMap[edge.targetId] ?: edge.targetId
                edge.copy(sourceId = sourceId, targetId = targetId)
            }
            .filter { it.sourceId != it.targetId } // Remove self-loops created by retargeting

        // Filter out internal nodes and flag supernodes
        val finalNodeMap = fullNodeMap
            .filterKeys { it !in internalNodeIds }
            .mapValues { (id, node) ->
                val internalGraph = internalGraphMap[id]
                if (internalGraph != null) {
                    // This is a Supernode
                    node.copy(
                        isSupernode = true,
                        internalGraph = internalGraph
                    )
                } else {
                    // This is a regular node
                    node
                }
            }

        // --- Step 4: Manage Physics Engines ---
        val newEngineMap = internalGraphMap.keys.associateWith {
            internalPhysicsEngines[it] ?: PhysicsEngine()
        }
        internalPhysicsEngines.clear()
        internalPhysicsEngines.putAll(newEngineMap)

        // --- Step 5: Set State ---
        _graphNodes.value = finalNodeMap
        _graphEdges.value = finalEdges
    }

    // --- Coordinate Conversion ---

    private fun screenToWorld(screenPos: Offset): Offset {
        val pan = _transform.value.pan
        val zoom = _transform.value.zoom
        val center = Offset(size.width / 2f, size.height / 2f)
        return (screenPos - center - pan * zoom) / zoom
    }

    private fun screenDeltaToWorldDelta(screenDelta: Offset): Offset {
        return screenDelta / _transform.value.zoom
    }

    // --- Gesture Handlers ---

    fun onPan(delta: Offset) {
        _transform.update {
            it.copy(pan = it.pan + (delta / it.zoom))
        }
    }

    fun onZoom(zoomFactor: Float, zoomCenterScreen: Offset) {
        _transform.update { state ->
            val oldZoom = state.zoom
            val newZoom = (oldZoom * zoomFactor).coerceIn(0.1f, 10.0f)
            val sizeCenter = Offset(size.width / 2f, size.height / 2f)
            val worldPos = (zoomCenterScreen - state.pan * oldZoom - sizeCenter) / oldZoom
            val newPan = (zoomCenterScreen - worldPos * newZoom - sizeCenter) / newZoom
            state.copy(pan = newPan, zoom = newZoom)
        }
    }

    private fun findNodeAt(worldPos: Offset): GraphNode? {
        return _graphNodes.value.values.reversed().find { node ->
            val distance = (worldPos - node.pos).getDistance()
            distance < node.radius
        }
    }

    fun onDragStart(screenPos: Offset): Boolean {
        val worldPos = screenToWorld(screenPos)
        val tappedNode = findNodeAt(worldPos)

        return if (tappedNode != null) {
            _draggedNodeId.value = tappedNode.id
            _dragVelocity.value = Offset.Zero
            _graphNodes.update { allNodes ->
                val newNodes = allNodes.toMutableMap()
                val node = newNodes[tappedNode.id]
                if (node != null) {
                    newNodes[tappedNode.id] = node.copy(isFixed = true)
                }
                newNodes
            }
            true
        } else {
            false
        }
    }

    fun onDrag(screenDelta: Offset) {
        val nodeId = _draggedNodeId.value ?: return
        val worldDelta = screenDeltaToWorldDelta(screenDelta)
        _dragVelocity.value = worldDelta

        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            val node = newNodes[nodeId]
            if (node != null) {
                newNodes[nodeId] = node.copy(pos = node.pos + worldDelta)
            }
            newNodes
        }
    }

    fun onDragEnd() {
        val nodeId = _draggedNodeId.value ?: return
        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            val node = newNodes[nodeId]
            if (node != null) {
                newNodes[nodeId] = node.copy(
                    isFixed = false,
                    vel = _dragVelocity.value / (1f / 60f)
                )
            }
            newNodes
        }
        _draggedNodeId.value = null
        _dragVelocity.value = Offset.Zero
    }

    /**
     * Called when the canvas is tapped.
     * Invokes the callback with the tapped node's ID if found.
     */
    fun onTap(screenPos: Offset, onNodeTapped: (Long) -> Unit) {
        val worldPos = screenToWorld(screenPos)
        val tappedNode = findNodeAt(worldPos)

        if (tappedNode != null) {
            onNodeTapped(tappedNode.id)
        }
    }

    // --- UI Handlers ---

    fun onResize(newSize: androidx.compose.ui.unit.IntSize) {
        size = Size(newSize.width.toFloat(), newSize.height.toFloat())
    }

    fun onFabClick() {
        _showFabMenu.update { !it }
    }

    fun toggleSettings() {
        _showSettings.update { !it }
    }

    fun setGravity(value: Float) {
        _physicsOptions.update { it.copy(gravity = value) }
    }

    fun setRepulsion(value: Float) {
        _physicsOptions.update { it.copy(repulsion = value) }
    }

    fun setSpring(value: Float) {
        _physicsOptions.update { it.copy(spring = value) }
    }

    fun setDamping(value: Float) {
        _physicsOptions.update { it.copy(damping = value) }
    }

    fun setBarnesHutTheta(value: Float) {
        _physicsOptions.update { it.copy(barnesHutTheta = value) }
    }

    fun setTolerance(value: Float) {
        _physicsOptions.update { it.copy(tolerance = value) }
    }

    fun stopSimulation() {
        _simulationRunning.value = false
    }

    fun onCleared() {
        stopSimulation()
    }

    // --- ADDED: Detangle Functions ---

    /**
     * Shows the detangle settings dialog.
     */
    fun onShowDetangleDialog() {
        _showDetangleDialog.value = true
        _showSettings.value = false // Close settings panel
    }

    /**
     * Hides the detangle settings dialog.
     */
    fun onDismissDetangleDialog() {
        _showDetangleDialog.value = false
    }

    /**
     * Starts the detangling process.
     *
     * @param algorithm The selected static layout algorithm.
     * @param params The parameters for the algorithm.
     */
    fun startDetangle(algorithm: DetangleAlgorithm, params: Map<String, Any>) {
        // 1. Stop the current physics simulation
        stopSimulation() // This sets _simulationRunning.value = false, stopping the loop in GraphView

        // 2. Trigger lockout
        _isDetangling.value = true
        _showDetangleDialog.value = false // Close dialog

        viewModelScope.launch(Dispatchers.Default) { // Run static layout on background thread
            val layoutFlow = when (algorithm) {
                DetangleAlgorithm.FRUCHTERMAN_REINGOLD -> runFRLayout(_graphNodes.value, _graphEdges.value, params)
                // Default to FR for unimplemented algorithms
                else -> runFRLayout(_graphNodes.value, _graphEdges.value, params)
            }

            // 3. "Step-by-Step Visualization"
            layoutFlow.collect { tickedNodes ->
                // Update the graph nodes, which the UI is observing
                _graphNodes.value = tickedNodes
            }

            // 4. "Completion"
            withContext(Dispatchers.Main) {
                _isDetangling.value = false // Remove lockout

                // 5. "Resume Simulation"
                _simulationRunning.value = true
            }
        }
    }
}