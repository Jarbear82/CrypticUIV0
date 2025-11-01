package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.GraphEdge
import com.tau.cryptic_ui_v0.GraphNode
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.TransformState
import com.tau.cryptic_ui_v0.notegraph.views.labelToColor
import com.tau.cryptic_ui_v0.viewmodels.EditCreateViewModel
import com.tau.cryptic_ui_v0.viewmodels.MetadataViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

class GraphViewmodel(
    private val viewModelScope: CoroutineScope,
    // UPDATED: Made metadataViewModel public
    val metadataViewModel: MetadataViewModel,
    // ADDED: ViewModels for handling creation
    private val editCreateViewModel: EditCreateViewModel,
    private val onSwitchToEditTab: () -> Unit
) {
    private val options = PhysicsOptions()
    private val physicsEngine = PhysicsEngine(options)

    private val _graphNodes = MutableStateFlow<Map<Long, GraphNode>>(emptyMap())
    val graphNodes = _graphNodes.asStateFlow()

    private val _graphEdges = MutableStateFlow<List<GraphEdge>>(emptyList())
    val graphEdges = _graphEdges.asStateFlow()

    private val _transform = MutableStateFlow(TransformState())
    val transform = _transform.asStateFlow()

    // Ticker for the physics simulation
    private val _simulationRunning = MutableStateFlow(true)

    // ADDED: State for node dragging
    private val _draggedNodeId = MutableStateFlow<Long?>(null)
    private val _dragVelocity = MutableStateFlow(Offset.Zero)

    // ADDED: State for FAB menu
    private val _showFabMenu = MutableStateFlow(false)
    val showFabMenu = _showFabMenu.asStateFlow()

    // We need the canvas size to correctly calculate zoom center
    private var size = Size.Zero

    init {
        // Observe changes in the metadata view model
        viewModelScope.launch {
            combine(
                metadataViewModel.nodeList,
                metadataViewModel.edgeList
            ) { nodes, edges ->
                nodes to edges
            }.collectLatest { (nodeList, edgeList) ->
                updateGraphData(nodeList, edgeList)
            }
        }
    }

    suspend fun runSimulationLoop() {
        var lastTimeNanos = withFrameNanos { it }
        while (_simulationRunning.value) {
            val currentTimeNanos = withFrameNanos { it }
            // delta time in seconds
            val dt = (currentTimeNanos - lastTimeNanos) / 1_000_000_000.0f
            lastTimeNanos = currentTimeNanos

            // Run one physics step
            if (_graphNodes.value.isNotEmpty()) {
                val updatedNodes = physicsEngine.update(
                    _graphNodes.value,
                    _graphEdges.value,
                    dt.coerceAtMost(0.032f) // Cap delta time
                )
                _graphNodes.value = updatedNodes
            }
        }
    }

    private fun updateGraphData(nodeList: List<NodeDisplayItem>, edgeList: List<EdgeDisplayItem>) {
        val edgeCountByNodeId = mutableMapOf<Long, Int>()
        edgeList.forEach { edge ->
            edgeCountByNodeId[edge.src.id] = (edgeCountByNodeId[edge.src.id] ?: 0) + 1
            edgeCountByNodeId[edge.dst.id] = (edgeCountByNodeId[edge.dst.id] ?: 0) + 1
        }

        _graphNodes.update { currentNodes ->
            val newNodeMap = nodeList.associate { node ->
                val id = node.id
                val edgeCount = edgeCountByNodeId[id] ?: 0
                val radius = options.nodeBaseRadius + (edgeCount * options.nodeRadiusEdgeFactor)

                // --- MODIFIED: ForceAtlas2 mass is (degree + 1) ---
                val mass = (edgeCount + 1).toFloat()
                // --- END MODIFICATION ---

                val existingNode = currentNodes[id]

                val newNode = if (existingNode != null) {
                    existingNode.copy(
                        label = node.label,
                        displayProperty = node.displayProperty,
                        mass = mass,
                        radius = radius,
                        colorInfo = labelToColor(node.label)
                        // Note: We preserve existing pos, vel, isFixed, and physics state
                    )
                } else {
                    GraphNode(
                        id = id,
                        label = node.label,
                        displayProperty = node.displayProperty,
                        pos = Offset(Random.nextFloat() * 100 - 50, Random.nextFloat() * 100 - 50),
                        vel = Offset.Zero,
                        mass = mass,
                        radius = radius,
                        colorInfo = labelToColor(node.label),
                        isFixed = false // New nodes are not fixed
                        // oldForce, swinging, traction default to 0/Zero
                    )
                }
                id to newNode
            }
            newNodeMap.filterKeys { it in nodeList.map { n -> n.id }.toSet() }
        }

        _graphEdges.value = edgeList.map { edge ->
            GraphEdge(
                id = edge.id,
                sourceId = edge.src.id,
                targetId = edge.dst.id,
                label = edge.label,
                strength = 1.0f,
                colorInfo = labelToColor(edge.label)
            )
        }
    }

    // --- Coordinate Conversion ---

    /** Converts screen coordinates (e.g., from a tap) to world coordinates */
    private fun screenToWorld(screenPos: Offset): Offset {
        val pan = _transform.value.pan
        val zoom = _transform.value.zoom
        val center = Offset(size.width / 2f, size.height / 2f)
        return (screenPos - center - pan * zoom) / zoom
    }

    /** Converts a screen *delta* (e.g., from a drag) to a world *delta* */
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

    /** Finds the node at a given world position, if any */
    private fun findNodeAt(worldPos: Offset): GraphNode? {
        // Iterate in reverse so nodes drawn on top are found first
        return _graphNodes.value.values.reversed().find { node ->
            val distance = (worldPos - node.pos).getDistance()
            distance < node.radius
        }
    }

    /**
     * Called when a drag gesture starts.
     * Checks if it's on a node or the canvas.
     * @return true if a node was grabbed, false if it's a pan gesture.
     */
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
            true // It's a node drag
        } else {
            false // It's a pan
        }
    }

    /** Called when dragging a node */
    fun onDrag(screenDelta: Offset) {
        val nodeId = _draggedNodeId.value ?: return
        val worldDelta = screenDeltaToWorldDelta(screenDelta)
        _dragVelocity.value = worldDelta // Store velocity for when we release

        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            val node = newNodes[nodeId]
            if (node != null) {
                newNodes[nodeId] = node.copy(pos = node.pos + worldDelta)
            }
            newNodes
        }
    }

    /** Called when the drag gesture ends */
    fun onDragEnd() {
        val nodeId = _draggedNodeId.value ?: return
        _graphNodes.update { allNodes ->
            val newNodes = allNodes.toMutableMap()
            val node = newNodes[nodeId]
            if (node != null) {
                newNodes[nodeId] = node.copy(
                    isFixed = false,
                    vel = _dragVelocity.value / (1f / 60f) // Apply velocity
                )
            }
            newNodes
        }
        _draggedNodeId.value = null
        _dragVelocity.value = Offset.Zero
    }

    /** Called when the canvas is tapped */
    fun onTap(screenPos: Offset) {
        val worldPos = screenToWorld(screenPos)
        val tappedNode = findNodeAt(worldPos)

        if (tappedNode != null) {
            // Find the corresponding NodeDisplayItem to pass to the metadataViewModel
            val displayItem = metadataViewModel.nodeList.value.find { it.id == tappedNode.id }
            if (displayItem != null) {
                metadataViewModel.selectItem(displayItem)
            }
        }
    }

    // --- UI Handlers ---

    fun onResize(newSize: androidx.compose.ui.unit.IntSize) {
        size = Size(newSize.width.toFloat(), newSize.height.toFloat())
    }

    fun onFabClick() {
        _showFabMenu.update { !it }
    }

    fun onFabCreateNodeClick() {
        _showFabMenu.value = false
        editCreateViewModel.initiateNodeCreation()
        onSwitchToEditTab()
    }

    fun onFabCreateEdgeClick() {
        _showFabMenu.value = false
        editCreateViewModel.initiateEdgeCreation()
        onSwitchToEditTab()
    }

    fun onCleared() {
        _simulationRunning.value = false
    }
}
