package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.tau.cryptic_ui_v0.ClusterDisplayItem
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.GraphCluster
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
    // UPDATED: No longer passing options here
    private val physicsEngine = PhysicsEngine()

    // --- ADDED: State for physics options ---
    private val _physicsOptions = MutableStateFlow(PhysicsOptions(gravity = 0.5f, internalGravity = 0.5f)) // Use stronger gravity
    val physicsOptions = _physicsOptions.asStateFlow()

    // This is the single source of truth for all node states, for the renderer
    private val _graphNodes = MutableStateFlow<Map<Long, GraphNode>>(emptyMap())
    val graphNodes = _graphNodes.asStateFlow()

    // This is the single source of truth for all *original* edges, for the renderer
    private val _graphEdges = MutableStateFlow<List<GraphEdge>>(emptyList())
    val graphEdges = _graphEdges.asStateFlow()

    // This is the single source of truth for all cluster physics states
    private val _graphClusters = MutableStateFlow<Map<Long, GraphCluster>>(emptyMap())

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

    // --- ADDED: State for settings UI ---
    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    // We need the canvas size to correctly calculate zoom center
    private var size = Size.Zero

    init {
        // Observe changes in the metadata view model
        viewModelScope.launch {
            // UPDATED: Combine all three lists
            combine(
                metadataViewModel.nodeList,
                metadataViewModel.edgeList,
                metadataViewModel.clusterList // ADDED
            ) { nodes, edges, clusters ->
                Triple(nodes, edges, clusters)
            }.collectLatest { (nodeList, edgeList, clusterList) ->
                updateGraphData(nodeList, edgeList, clusterList)
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

            val options = _physicsOptions.value
            val currentNodes = _graphNodes.value
            val currentClusters = _graphClusters.value
            val allEdges = _graphEdges.value // Original edges

            if (currentNodes.isEmpty() && currentClusters.isEmpty()) continue

            // --- 1. DATA PRE-PROCESSING ---

            // 1a. Partition nodes
            val (clusteredNodes, unClusteredNodes) = currentNodes.values.partition { it.clusterId != null }
            val microNodeGroups = clusteredNodes.groupBy { it.clusterId!! }
            val nodeClusterIdLookup = currentNodes.mapValues { it.value.clusterId }

            // 1b. Partition edges
            val microEdgeGroups = mutableMapOf<Long, MutableList<GraphEdge>>()
            val macroEdges = mutableListOf<GraphEdge>()
            for (edge in allEdges) {
                val srcClusterId = nodeClusterIdLookup[edge.sourceId]
                val dstClusterId = nodeClusterIdLookup[edge.targetId]

                if (srcClusterId != null && srcClusterId == dstClusterId) {
                    // This is an edge *within* a cluster
                    microEdgeGroups.getOrPut(srcClusterId) { mutableListOf() }.add(edge)
                } else {
                    // This is a "macro" edge (free->free, free->cluster, cluster->free, cluster->cluster)
                    // We must remap the IDs to point to the cluster if it exists
                    val macroSourceId = srcClusterId ?: edge.sourceId
                    val macroTargetId = dstClusterId ?: edge.targetId
                    macroEdges.add(edge.copy(sourceId = macroSourceId, targetId = macroTargetId))
                }
            }

            // 1c. Update clusters and create proxy nodes for macro sim
            val updatedClusters = mutableMapOf<Long, GraphCluster>()
            val macroProxyNodes = mutableMapOf<Long, GraphNode>()

            for ((id, cluster) in currentClusters) {
                val microNodes = microNodeGroups[id] ?: emptyList()
                // Calculate new center of mass. If cluster is empty, use its last known pos.
                val (newMass, newPos) = if (microNodes.isEmpty()) {
                    (1f to cluster.pos) // Empty cluster has mass 1
                } else {
                    val mass = microNodes.sumOf { it.mass.toDouble() }.toFloat().coerceAtLeast(1f)
                    // mass-weighted average position
                    val pos = microNodes.fold(Offset.Zero) { acc, node -> acc + node.pos * node.mass } / mass
                    (mass to pos)
                }

                // Update the cluster state
                val newCluster = cluster.copy(mass = newMass, pos = newPos)
                updatedClusters[id] = newCluster

                // Create the proxy GraphNode for the physics engine
                macroProxyNodes[id] = GraphNode(
                    id = id,
                    label = cluster.label,
                    displayProperty = cluster.displayProperty,
                    pos = newPos, // Use new center of mass
                    vel = cluster.vel,
                    mass = newMass, // Use new mass
                    radius = cluster.radius, // TODO: This should be convex hull
                    colorInfo = cluster.colorInfo,
                    isFixed = cluster.isFixed,
                    oldForce = cluster.oldForce,
                    swinging = cluster.swinging,
                    traction = cluster.traction,
                    clusterId = null // This is a macro-body
                )
            }

            // --- 2. PASS 1: MACRO SIMULATION ---

            // Combine free nodes and cluster proxy-nodes
            val macroPhysicsBodiesMap = unClusteredNodes.associateBy { it.id } + macroProxyNodes
            val updatedMacroBodiesMap = physicsEngine.update(
                macroPhysicsBodiesMap,
                macroEdges,
                options,
                dt.coerceAtMost(0.032f)
            )

            // --- 3. PASS 2: MICRO SIMULATIONS ---

            val allUpdatedMicroNodes = mutableMapOf<Long, GraphNode>()
            val finalUpdatedClusters = mutableMapOf<Long, GraphCluster>()

            for ((id, cluster) in updatedClusters) { // Loop over the *real* cluster objects
                val updatedMacroBody = updatedMacroBodiesMap[id]
                if (updatedMacroBody == null) { // Cluster might have been deleted
                    continue
                }

                val clusterCenter = updatedMacroBody.pos // Get new center from macro sim
                val microNodes = microNodeGroups[id] ?: emptyList()
                val microEdges = microEdgeGroups[id] ?: emptyList()

                // Run internal simulation
                val updatedMicroNodes = physicsEngine.updateInternal(
                    microNodes,
                    microEdges,
                    clusterCenter,
                    options,
                    dt.coerceAtMost(0.032f)
                )

                // Collect results
                updatedMicroNodes.forEach { allUpdatedMicroNodes[it.id] = it }

                // Save the final updated cluster state from the macro sim
                finalUpdatedClusters[id] = cluster.copy(
                    pos = updatedMacroBody.pos,
                    vel = updatedMacroBody.vel,
                    isFixed = updatedMacroBody.isFixed,
                    oldForce = updatedMacroBody.oldForce,
                    swinging = updatedMacroBody.swinging,
                    traction = updatedMacroBody.traction
                )
            }

            // --- 4. DATA POST-PROCESSING ---

            // Get the updated free nodes from the macro sim
            val updatedUnClusteredNodes = updatedMacroBodiesMap.filterKeys { it !in finalUpdatedClusters }

            // Combine all nodes for the renderer
            _graphNodes.value = allUpdatedMicroNodes + updatedUnClusteredNodes
            // Update the source-of-truth cluster state
            _graphClusters.value = finalUpdatedClusters
        }
    }

    /**
     * Called when the data from the database changes.
     * This function updates the source-of-truth node, edge, and cluster states.
     * It preserves the physics state (pos, vel) of existing entities.
     */
    private fun updateGraphData(
        nodeList: List<NodeDisplayItem>,
        edgeList: List<EdgeDisplayItem>,
        clusterList: List<ClusterDisplayItem>
    ) {
        val currentNodes = _graphNodes.value
        val currentClusters = _graphClusters.value

        // --- 1. Update Nodes ---
        val edgeCountByNodeId = mutableMapOf<Long, Int>()
        edgeList.forEach { edge ->
            val srcId = (edge.src as? NodeDisplayItem)?.id
            val dstId = (edge.dst as? NodeDisplayItem)?.id
            if (srcId != null) edgeCountByNodeId[srcId] = (edgeCountByNodeId[srcId] ?: 0) + 1
            if (dstId != null) edgeCountByNodeId[dstId] = (edgeCountByNodeId[dstId] ?: 0) + 1
        }

        _graphNodes.value = nodeList.associate { node ->
            val id = node.id
            val existingNode = currentNodes[id]
            val edgeCount = edgeCountByNodeId[id] ?: 0
            val radius = _physicsOptions.value.nodeBaseRadius + (edgeCount * _physicsOptions.value.nodeRadiusEdgeFactor)
            val mass = (edgeCount + 1).toFloat()

            val newNode = if (existingNode != null) {
                existingNode.copy(
                    label = node.label,
                    displayProperty = node.displayProperty,
                    mass = mass,
                    radius = radius,
                    colorInfo = labelToColor(node.label),
                    clusterId = node.clusterId // Update cluster ID
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
                    clusterId = node.clusterId
                )
            }
            id to newNode
        }

        // --- 2. Update Clusters ---
        _graphClusters.value = clusterList.associate { cluster ->
            val id = cluster.id
            val existingCluster = currentClusters[id]
            // Mass and Pos will be calculated by the sim loop.
            // We just need to preserve the physics state if it exists.
            val newCluster = if (existingCluster != null) {
                existingCluster.copy(
                    label = cluster.label,
                    displayProperty = cluster.displayProperty,
                    colorInfo = labelToColor(cluster.label)
                    // Preserve pos, vel, mass, radius, etc.
                )
            } else {
                GraphCluster(
                    id = id,
                    label = cluster.label,
                    displayProperty = cluster.displayProperty,
                    pos = Offset(Random.nextFloat() * 100 - 50, Random.nextFloat() * 100 - 50),
                    vel = Offset.Zero,
                    mass = 1f, // Initial mass
                    radius = 50f, // TODO: Initial radius, will be replaced
                    colorInfo = labelToColor(cluster.label)
                )
            }
            id to newCluster
        }

        // --- 3. Update Edges (for renderer) ---
        // We use the raw edge data, the sim loop handles remapping
        _graphEdges.value = edgeList.mapNotNull { edge ->
            // Ensure src and dst are valid GraphEntityDisplayItems
            val srcId = edge.src.id
            val dstId = edge.dst.id

            // This logic might need adjustment if edge.src can be a cluster
            // For now, assume edgeList provides edges between nodes
            // The sim loop will handle remapping node->cluster
            GraphEdge(
                id = edge.id,
                sourceId = srcId,
                targetId = dstId,
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
        // TODO: Also check for dragging clusters

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
        // TODO: Handle dragging clusters
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
        // TODO: Handle dragging clusters
    }

    /** Called when the canvas is tapped */
    fun onTap(screenPos: Offset) {
        val worldPos = screenToWorld(screenPos)
        val tappedNode = findNodeAt(worldPos)
        // TODO: Also check for tapping clusters

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

    // --- ADDED: Physics Settings Handlers ---

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

    // ADDED: Handler for new internal gravity
    fun setInternalGravity(value: Float) {
        _physicsOptions.update { it.copy(internalGravity = value) }
    }


    fun onCleared() {
        _simulationRunning.value = false
    }
}
