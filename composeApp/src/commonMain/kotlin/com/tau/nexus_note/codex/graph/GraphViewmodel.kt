package com.tau.nexus_note.codex.graph

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.datamodels.GraphNode
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.TransformState
import com.tau.nexus_note.codex.graph.physics.PhysicsEngine
import com.tau.nexus_note.codex.graph.physics.runFRLayout
import com.tau.nexus_note.settings.SettingsData
import com.tau.nexus_note.utils.labelToColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class GraphViewmodel(
    private val viewModelScope: CoroutineScope,
    private val settingsFlow: StateFlow<SettingsData>
) {

    private val physicsEngine = PhysicsEngine()

    private val _physicsOptions = MutableStateFlow(settingsFlow.value.graphPhysics.options)
    val physicsOptions = _physicsOptions.asStateFlow()

    private val _renderingSettings = MutableStateFlow(settingsFlow.value.graphRendering)
    val renderingSettings = _renderingSettings.asStateFlow()

    private val _simulationRunning = MutableStateFlow(false)
    val simulationRunning = _simulationRunning.asStateFlow()

    private val _graphNodes = MutableStateFlow<Map<Long, GraphNode>>(emptyMap())
    val graphNodes = _graphNodes.asStateFlow()

    private val _graphEdges = MutableStateFlow<List<GraphEdge>>(emptyList())
    val graphEdges = _graphEdges.asStateFlow()

    private val _transform = MutableStateFlow(TransformState())
    val transform = _transform.asStateFlow()

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

    // Listens for settings changes and simulation state.
    init {
        // Collector for settings
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                _physicsOptions.value = settings.graphPhysics.options
                _renderingSettings.value = settings.graphRendering
            }
        }
    }

    suspend fun runSimulationLoop() {
        if (!_simulationRunning.value) return

        var lastTimeNanos = withFrameNanos { it }
        while (_simulationRunning.value) {
            val currentTimeNanos = withFrameNanos { it }
            val dt = (currentTimeNanos - lastTimeNanos) / 1_000_000_000.0f
            lastTimeNanos = currentTimeNanos

            if (_graphNodes.value.isNotEmpty()) {
                val updatedNodes = physicsEngine.update(
                    _graphNodes.value,
                    _graphEdges.value,
                    _physicsOptions.value,
                    dt.coerceAtMost(0.032f)
                )
                _graphNodes.value = updatedNodes
            }
        }
    }

    /**
     * Public method for CodexViewModel to push new data into the graph.
     */
    fun updateGraphData(nodeList: List<NodeDisplayItem>, edgeList: List<EdgeDisplayItem>) {
        val edgeCountByNodeId = mutableMapOf<Long, Int>()
        edgeList.forEach { edge ->
            edgeCountByNodeId[edge.src.id] = (edgeCountByNodeId[edge.src.id] ?: 0) + 1
            edgeCountByNodeId[edge.dst.id] = (edgeCountByNodeId[edge.dst.id] ?: 0) + 1
        }

        _graphNodes.update { currentNodes ->
            val newNodeMap = nodeList.associate { node ->
                val id = node.id
                val edgeCount = edgeCountByNodeId[id] ?: 0
                val radius = _physicsOptions.value.nodeBaseRadius + (edgeCount * _physicsOptions.value.nodeRadiusEdgeFactor)
                val mass = (edgeCount + 1).toFloat()
                val existingNode = currentNodes[id]

                val newNode = if (existingNode != null) {
                    existingNode.copy(
                        label = node.label,
                        displayProperty = node.displayProperty,
                        mass = mass,
                        radius = radius,
                        colorInfo = labelToColor(node.label)
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
                        isFixed = false
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

    fun screenToWorld(screenPos: Offset): Offset {
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
        val newZoomFactor = 1.0f + (zoomFactor - 1.0f) * settingsFlow.value.graphInteraction.zoomSensitivity

        _transform.update { state ->
            val oldZoom = state.zoom
            val newZoom = (oldZoom * newZoomFactor).coerceIn(0.1f, 10.0f)
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

    fun startSimulation() {
        // Only start if the user has it enabled in settings.
        _simulationRunning.value = settingsFlow.value.graphRendering.startSimulationOnLoad
    }

    fun stopSimulation() {
        _simulationRunning.value = false
    }

    fun onCleared() {
        stopSimulation()
    }

    // --- Detangle Functions ---

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
                // --- FIX: Call startSimulation, not just set the value ---

                // 5. "Resume Simulation"
                startSimulation()
            }
        }
    }
}