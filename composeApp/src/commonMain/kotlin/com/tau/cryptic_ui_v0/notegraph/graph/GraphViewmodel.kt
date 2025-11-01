package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.GraphEdge
import com.tau.cryptic_ui_v0.GraphNode
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.TransformState
import com.tau.cryptic_ui_v0.notegraph.views.labelToColor
import com.tau.cryptic_ui_v0.viewmodels.MetadataViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class GraphViewmodel(
    private val viewModelScope: CoroutineScope,
    private val metadataViewModel: MetadataViewModel
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

        // REMOVED: The physics simulation loop was moved to runSimulationLoop()
        // to be called from a Composable's LaunchedEffect.
    }

    // ADDED: This function will be called from a LaunchedEffect in the UI
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
                    // Cap delta time to prevent physics "explosions" if frame rate drops
                    dt.coerceAtMost(0.032f)
                )
                _graphNodes.value = updatedNodes
            }
        }
    }


    /**
     * Updates the internal graph state based on the latest lists from MetadataViewModel.
     * It preserves existing node positions and velocities.
     */
    private fun updateGraphData(nodeList: List<NodeDisplayItem>, edgeList: List<EdgeDisplayItem>) {
        // Calculate edge counts to determine node size/mass
        val edgeCountByNodeId = mutableMapOf<Long, Int>()
        edgeList.forEach { edge ->
            edgeCountByNodeId[edge.src.id] = (edgeCountByNodeId[edge.src.id] ?: 0) + 1
            edgeCountByNodeId[edge.dst.id] = (edgeCountByNodeId[edge.dst.id] ?: 0) + 1
        }

        _graphNodes.update { currentNodes ->
            val newNodeMap = nodeList.associate { node ->
                val id = node.id
                val edgeCount = edgeCountByNodeId[id] ?: 0

                // Size and mass are proportional to edge count
                val radius = options.nodeBaseRadius + (edgeCount * options.nodeRadiusEdgeFactor)
                val mass = radius

                // Check if node exists, if not, create it with a random position
                val existingNode = currentNodes[id]
                val newNode = if (existingNode != null) {
                    // Update properties but keep position/velocity
                    existingNode.copy(
                        label = node.label,
                        displayProperty = node.displayProperty,
                        mass = mass,
                        radius = radius,
                        colorInfo = labelToColor(node.label)
                    )
                } else {
                    // New node, place it near the center
                    GraphNode(
                        id = id,
                        label = node.label,
                        displayProperty = node.displayProperty,
                        pos = Offset(Random.nextFloat() * 100 - 50, Random.nextFloat() * 100 - 50),
                        vel = Offset.Zero,
                        mass = mass,
                        radius = radius,
                        colorInfo = labelToColor(node.label)
                    )
                }
                id to newNode
            }
            // Prune nodes that are no longer in the list
            newNodeMap.filterKeys { it in nodeList.map { n -> n.id }.toSet() }
        }

        _graphEdges.value = edgeList.map { edge ->
            GraphEdge(
                id = edge.id,
                sourceId = edge.src.id,
                targetId = edge.dst.id,
                label = edge.label,
                strength = 1.0f, // You could customize this later
                colorInfo = labelToColor(edge.label)
            )
        }
    }

    fun onPan(delta: Offset) {
        _transform.update {
            // Pan is relative to the current zoom level
            it.copy(pan = it.pan + (delta / it.zoom))
        }
    }

    fun onZoom(zoomFactor: Float, zoomCenterScreen: Offset) {
        _transform.update { state ->
            val oldZoom = state.zoom
            val newZoom = (oldZoom * zoomFactor).coerceIn(0.1f, 10.0f)

            // FIXED: Get center of size via Offset(width/2, height/2)
            val sizeCenter = Offset(size.width / 2f, size.height / 2f)

            // Calculate the world position under the cursor before zoom
            val worldPos = (zoomCenterScreen - state.pan * oldZoom - sizeCenter) / oldZoom

            // Calculate the new pan to keep the world position under the cursor
            // FIXED: Get center of size via Offset(width/2, height/2)
            val newPan = (zoomCenterScreen - worldPos * newZoom - sizeCenter) / newZoom

            state.copy(pan = newPan, zoom = newZoom)
        }
    }

    // We need the canvas size to correctly calculate zoom center
    private var size = androidx.compose.ui.geometry.Size.Zero
    fun onResize(newSize: androidx.compose.ui.unit.IntSize) {
        size = androidx.compose.ui.geometry.Size(newSize.width.toFloat(), newSize.height.toFloat())
    }

    fun onCleared() {
        _simulationRunning.value = false
    }
}
