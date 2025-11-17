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
import com.tau.nexus_note.codex.graph.physics.PhysicsOptions
import com.tau.nexus_note.codex.graph.physics.runFRLayout
import com.tau.nexus_note.settings.GraphRenderingSettings
import com.tau.nexus_note.settings.SettingsData
import com.tau.nexus_note.utils.labelToColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// --- UPDATED ---
// Constructor now accepts settingsFlow
class GraphViewmodel(
    private val viewModelScope: CoroutineScope,
    private val settingsFlow: StateFlow<SettingsData>
) {
// --- END UPDATE ---

    private val physicsEngine = PhysicsEngine()

    // --- UPDATED ---
    // Physics and Rendering options are now initialized from the settings flow
    private val _physicsOptions = MutableStateFlow(settingsFlow.value.graphPhysics.options)
    val physicsOptions = _physicsOptions.asStateFlow()

    private val _renderingSettings = MutableStateFlow(settingsFlow.value.graphRendering)
    val renderingSettings = _renderingSettings.asStateFlow()

    // --- FIX: Initialize to false. CodexView will be responsible for starting. ---
    private val _simulationRunning = MutableStateFlow(false)
    val simulationRunning = _simulationRunning.asStateFlow()
    // --- END UPDATE ---

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

    // --- UPDATED ---
    // Logic moved from GraphView.kt to here.
    // Listens for settings changes and simulation state.
    init {
        // Collector for settings
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                _physicsOptions.value = settings.graphPhysics.options
                _renderingSettings.value = settings.graphRendering
                // --- REMOVED ---
                // The init block no longer controls the simulation state.
                // _simulationRunning.value = settings.graphRendering.startSimulationOnLoad
            }
        }

        // --- DELETED ---
        // The viewModelScope.launch block that collected simulationRunning
        // and called runSimulationLoop() has been removed.
        // --- END DELETED ---
    }

    suspend fun runSimulationLoop() {
        if (!_simulationRunning.value) return

        var lastTimeNanos = withFrameNanos { it }
        while (_simulationRunning.value) { // This flag is now critical
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
    // --- END UPDATE ---


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
                // --- UPDATED ---
                // Use physics options from the state flow
                val radius = _physicsOptions.value.nodeBaseRadius + (edgeCount * _physicsOptions.value.nodeRadiusEdgeFactor)
                // --- END UPDATE ---
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
        // --- UPDATED ---
        // Use zoom sensitivity from settings
        val newZoomFactor = 1.0f + (zoomFactor - 1.0f) * settingsFlow.value.graphInteraction.zoomSensitivity
        // --- END UPDATE ---

        _transform.update { state ->
            val oldZoom = state.zoom
            // --- UPDATED ---
            // Use newZoomFactor
            val newZoom = (oldZoom * newZoomFactor).coerceIn(0.1f, 10.0f)
            // --- END UPDATE ---
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

    // --- UPDATED ---
    // These settings are now handled by the SettingsViewModel and flow in.
    // These functions can be removed if you only want settings to be set
    // from the SettingsView. I will leave them commented out
    // in case you want to re-enable direct manipulation.
    /*
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
    */
    // --- END UPDATE ---

    // --- ADDED: Public method to start simulation, respecting settings ---
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
                // --- FIX: Call startSimulation, not just set the value ---
                startSimulation()
            }
        }
    }
}