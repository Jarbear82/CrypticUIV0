package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.views.labelToColor
import kotlin.math.max
import kotlin.math.min

// --- Graph Composable ---
@OptIn(ExperimentalTextApi::class) // Needed for TextMeasurer
@Composable
fun GraphView(
    nodes: List<NodeDisplayItem>,
    edges: List<EdgeDisplayItem>,
    modifier: Modifier = Modifier,
    // Pass Options
    nodeStyleOptions: NodeStyleOptions = NodeStyleOptions(),
    edgeStyleOptions: EdgeStyleOptions = EdgeStyleOptions(),
    physicsOptions: PhysicsOptions = PhysicsOptions(),
    interactionOptions: InteractionOptions = InteractionOptions(),
    selectionOptions: SelectionStyleOptions = SelectionStyleOptions(),
    tooltipOptions: TooltipOptions = TooltipOptions(),
    navigationOptions: NavigationOptions = NavigationOptions()
) {
    val density = LocalDensity.current

    // --- Node Style Values ---
    val nodeRadiusPx = remember(density, nodeStyleOptions.radiusDp) {
        with(density) { nodeStyleOptions.radiusDp.toPx() }
    }
    val nodeStrokeWidthPx = remember(density, nodeStyleOptions.defaultStrokeWidthDp) {
        with(density) { nodeStyleOptions.defaultStrokeWidthDp.toPx() }
    }
    val nodeDragStrokeWidthPx = remember(density, nodeStrokeWidthPx, nodeStyleOptions.draggedStrokeWidthMultiplier) {
        nodeStrokeWidthPx * nodeStyleOptions.draggedStrokeWidthMultiplier
    }
    val nodeSelectedStrokeWidthPx = remember(density, nodeStrokeWidthPx, selectionOptions.nodeSelectedStrokeWidthMultiplier) {
        nodeStrokeWidthPx * selectionOptions.nodeSelectedStrokeWidthMultiplier
    }

    // --- Edge Style Values ---
    val edgeStrokeWidthPx = remember(density, edgeStyleOptions.defaultStrokeWidthDp) {
        with(density) { edgeStyleOptions.defaultStrokeWidthDp.toPx() }
    }
    val edgeSelectedStrokeWidthPx = remember(density, edgeStrokeWidthPx, selectionOptions.edgeSelectedStrokeWidthMultiplier) {
        edgeStrokeWidthPx * selectionOptions.edgeSelectedStrokeWidthMultiplier
    }


    // --- Hoisted State ---
    var viewOffset by remember { mutableStateOf<Offset?>(null) } // Center offset
    var viewScale by remember { mutableStateOf(1f) }
    val focusRequester = remember { FocusRequester() }

    // --- State for node positions, sourced from Physics Engine ---
    val nodePositions = remember { mutableStateMapOf<String, Offset>() }

    // --- Selection and Interaction State ---
    var selectedNodeIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedEdgeIds by remember { mutableStateOf(emptySet<String>()) }
    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    var hoveredNodeInfo by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    var isShiftPressed by remember { mutableStateOf(false) }

    // --- Text and Physics ---
    val textMeasurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope() // Scope for physics engine
    val physicsEngine = remember { mutableStateOf<PhysicsEngine?>(null) }

    // --- Initialize Interaction Handler (Now only for node drag) ---
    val interactionHandler = remember(nodeRadiusPx, physicsEngine.value, viewOffset, viewScale) {
        GraphInteractionHandler(
            nodePositionsProvider = { nodePositions },
            nodeRadiusProvider = { nodeRadiusPx },
            viewOffsetProvider = { viewOffset ?: Offset.Zero },
            viewScaleProvider = { viewScale },
            physicsEngineNotifier = { nodeId, newPos, isDragging ->
                physicsEngine.value?.notifyNodePositionUpdate(nodeId, newPos.x, newPos.y, isDragging)
            },
            options = interactionOptions
        )
    }

    // Effect to collect position updates from Physics Engine
    LaunchedEffect(physicsEngine.value) {
        physicsEngine.value?.nodePositionsState?.collect { positions ->
            nodePositions.clear()
            nodePositions.putAll(positions)
        }
    }

    // Helper to convert screen coords to canvas coords
    fun screenToCanvas(screenPos: Offset): Offset {
        return (screenPos - (viewOffset ?: Offset.Zero)) / viewScale
    }

    // --- Fit graph to view ---
    fun fitGraphToView(widthPx: Float, heightPx: Float) {
        if (nodePositions.isEmpty()) return
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        nodePositions.values.forEach {
            minX = min(minX, it.x)
            minY = min(minY, it.y)
            maxX = max(maxX, it.x)
            maxY = max(maxY, it.y)
        }
        val graphWidth = (maxX - minX) + nodeRadiusPx * 2
        val graphHeight = (maxY - minY) + nodeRadiusPx * 2
        val graphCenterX = minX + (maxX - minX) / 2f
        val graphCenterY = minY + (maxY - minY) / 2f

        if (graphWidth == 0f || graphHeight == 0f) return

        val scaleX = widthPx / graphWidth
        val scaleY = heightPx / graphHeight
        viewScale = min(scaleX, scaleY) * 0.9f // Add 10% padding

        viewOffset = Offset(
            (widthPx / 2f) - (graphCenterX * viewScale),
            (heightPx / 2f) - (graphCenterY * viewScale)
        )
    }


    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // --- Initialize/Update Physics Engine ---
        LaunchedEffect(widthPx, heightPx, nodes, edges) {
            if (widthPx > 0 && heightPx > 0) {
                if (viewOffset == null) {
                    viewOffset = Offset(widthPx / 2f, heightPx / 2f)
                }

                val engine = physicsEngine.value
                if (engine == null) {
                    physicsEngine.value = PhysicsEngine(nodes, edges, widthPx, heightPx, coroutineScope, physicsOptions)
                    // Request focus for keyboard input
                    focusRequester.requestFocus()
                } else {
                    engine.updateData(nodes, edges)
                }
            }
        }

        // --- Canvas for Drawing ---
        if (viewOffset != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(interactionHandler, interactionOptions.selectionEnabled, interactionOptions.tooltipsEnabled) {
                        // --- Handle Taps (Selection, Tooltips) ---
                        detectTapGestures(
                            onTap = { screenPos ->
                                if (!interactionOptions.selectionEnabled) return@detectTapGestures
                                val canvasPos = screenToCanvas(screenPos)
                                val nodeId = interactionHandler.findNodeAt(canvasPos, nodePositions, nodeRadiusPx)

                                val multiSelect = isShiftPressed && interactionOptions.multiSelectEnabled
                                if (nodeId != null) {
                                    selectedNodeIds = if (selectedNodeIds.contains(nodeId)) {
                                        if (multiSelect) selectedNodeIds - nodeId else selectedNodeIds
                                    } else {
                                        if (multiSelect) selectedNodeIds + nodeId else setOf(nodeId)
                                    }
                                    selectedEdgeIds = emptySet() // Clear edge selection
                                } else {
                                    // Tapped on canvas
                                    selectedNodeIds = emptySet()
                                    selectedEdgeIds = emptySet()
                                }
                                hoveredNodeInfo = null // Hide tooltip on tap
                            },
                            onDoubleTap = { /* TODO: e.g., fit graph on node */ },
                            onLongPress = { screenPos ->
                                if (!interactionOptions.tooltipsEnabled) return@detectTapGestures
                                val canvasPos = screenToCanvas(screenPos)
                                val nodeId = interactionHandler.findNodeAt(canvasPos, nodePositions, nodeRadiusPx)
                                if (nodeId != null) {
                                    hoveredNodeInfo = nodeId to screenPos // Show tooltip at screen pos
                                }
                            }
                        )
                    }
                    .pointerInput(interactionHandler, draggedNodeId, interactionOptions.dragView, interactionOptions.zoomView) {
                        // --- Handle Pan and Zoom (if not dragging a node) ---
                        if (draggedNodeId == null) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                if (interactionOptions.dragView) {
                                    viewOffset = viewOffset!! + pan
                                }
                                if (interactionOptions.zoomView) {
                                    val newScale = (viewScale * zoom).coerceIn(0.1f, 10f)
                                    val newOffset = (viewOffset!! - centroid) * zoom + centroid
                                    viewOffset = newOffset
                                    viewScale = newScale
                                }
                            }
                        }
                    }
                    .pointerInput(interactionHandler) {
                        // --- Handle Node Drag (Highest Priority) ---
                        interactionHandler.handleNodeDragGestures(
                            pointerInputScope = this,
                            onDragStart = { nodeId -> draggedNodeId = nodeId; hoveredNodeInfo = null },
                            onDragEnd = { draggedNodeId = null }
                        )
                    }
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (!interactionOptions.keyboardNavigationEnabled) return@onKeyEvent false
                        isShiftPressed = event.isShiftPressed
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionUp -> { viewOffset = viewOffset!! + Offset(0f, 100f); true }
                                Key.DirectionDown -> { viewOffset = viewOffset!! + Offset(0f, -100f); true }
                                Key.DirectionLeft -> { viewOffset = viewOffset!! + Offset(100f, 0f); true }
                                Key.DirectionRight -> { viewOffset = viewOffset!! + Offset(-100f, 0f); true }
                                Key.Plus, Key.Equals -> { viewScale *= 1.2f; true }
                                Key.Minus -> { viewScale *= 0.8f; true }
                                else -> false
                            }
                        } else if (event.type == KeyEventType.KeyUp) {
                            if (event.key == Key.ShiftLeft || event.key == Key.ShiftRight) {
                                isShiftPressed = false
                            }
                        }
                        true
                    }
            ) {
                // --- Apply View Transformations ---
                translate(left = viewOffset!!.x, top = viewOffset!!.y) {
                    scale(scale = viewScale, pivot = Offset.Zero) {
                        val scaledNodeStrokeWidth = nodeStrokeWidthPx / viewScale
                        val scaledDragStrokeWidth = nodeDragStrokeWidthPx / viewScale
                        val scaledNodeSelectedStrokeWidth = nodeSelectedStrokeWidthPx / viewScale
                        val scaledEdgeStrokeWidth = edgeStrokeWidthPx / viewScale
                        val scaledEdgeSelectedStrokeWidth = edgeSelectedStrokeWidthPx / viewScale

                        // --- Draw Edges ---
                        edges.forEach { edge ->
                            val fromId = edge.src.id()
                            val toId = edge.dst.id()
                            val fromPos = nodePositions[fromId]
                            val toPos = nodePositions[toId]

                            if (fromPos != null && toPos != null) {
                                val colorInfo = labelToColor(edge.label)
                                val isSelected = selectedEdgeIds.contains(edge.id())
                                val edgeColor = if (isSelected) selectionOptions.edgeSelectedColor else colorInfo.composeColor
                                val edgeWidth = if (isSelected) scaledEdgeSelectedStrokeWidth else scaledEdgeStrokeWidth

                                if (fromId == toId) {
                                    drawSelfLoop(this, fromPos, nodeRadiusPx, edgeColor, edgeWidth)
                                    drawLabelCompose(
                                        drawScope = this,
                                        textMeasurer = textMeasurer,
                                        text = edge.label,
                                        position = calculateSelfLoopLabelPosition(fromPos, nodeRadiusPx),
                                        color = edgeStyleOptions.labelColor,
                                        fontSize = edgeStyleOptions.labelFontSizeSp.sp
                                    )
                                } else {
                                    val (startPos, endPos) = calculateEdgeEndpoints(fromPos, toPos, nodeRadiusPx, nodeRadiusPx)
                                    drawLine(
                                        color = edgeColor,
                                        start = startPos,
                                        end = endPos,
                                        strokeWidth = edgeWidth
                                    )
                                    drawArrowHead(this, startPos, endPos, edgeColor, viewScale)
                                    val midPoint = (startPos + endPos) / 2f
                                    drawLabelCompose(
                                        drawScope = this,
                                        textMeasurer = textMeasurer,
                                        text = edge.label,
                                        position = midPoint,
                                        color = edgeStyleOptions.labelColor,
                                        fontSize = edgeStyleOptions.labelFontSizeSp.sp
                                    )
                                }
                            }
                        }

                        // --- Draw Nodes ---
                        nodes.forEach { node ->
                            val nodeId = node.id()
                            val position = nodePositions[nodeId]
                            if (position != null) {
                                val colorInfo = labelToColor(node.label)
                                val isDragged = nodeId == draggedNodeId
                                val isSelected = selectedNodeIds.contains(nodeId)

                                // --- Draw Node Border (Selection/Drag) ---
                                if (isDragged) {
                                    drawCircle(
                                        color = nodeStyleOptions.draggedBorderColor,
                                        radius = nodeRadiusPx,
                                        center = position,
                                        style = Stroke(width = scaledDragStrokeWidth)
                                    )
                                } else if (isSelected) {
                                    drawCircle(
                                        color = selectionOptions.nodeSelectedBorderColor,
                                        radius = nodeRadiusPx,
                                        center = position,
                                        style = Stroke(width = scaledNodeSelectedStrokeWidth)
                                    )
                                } else {
                                    drawCircle(
                                        color = colorInfo.composeFontColor.copy(alpha = 0.5f),
                                        radius = nodeRadiusPx,
                                        center = position,
                                        style = Stroke(width = scaledNodeStrokeWidth)
                                    )
                                }

                                // --- Draw Node Fill ---
                                drawCircle(
                                    color = colorInfo.composeColor,
                                    radius = nodeRadiusPx,
                                    center = position
                                )

                                // --- Draw Node Label ---
                                val labelText = "${node.label}\n(${node.primarykeyProperty.value})"
                                drawLabelCompose(
                                    drawScope = this,
                                    textMeasurer = textMeasurer,
                                    text = labelText,
                                    position = position,
                                    color = colorInfo.composeFontColor,
                                    fontSize = nodeStyleOptions.labelFontSizeSp.sp,
                                    maxTextWidth = nodeRadiusPx * 1.8f
                                )
                            }
                        }
                    }
                }
            }

            // --- Draw Navigation UI ---
            if (navigationOptions.showNavigationUI) {
                GraphNavigationUI(
                    modifier = Modifier,
                    options = navigationOptions,
                    onZoomIn = { viewScale *= 1.2f },
                    onZoomOut = { viewScale *= 0.8f },
                    onFit = { fitGraphToView(widthPx, heightPx) }
                )
            }

            // --- Draw Tooltip ---
            val (hoveredNodeId, tooltipPos) = hoveredNodeInfo ?: (null to null)
            if (hoveredNodeId != null && tooltipPos != null) {
                val node = nodes.find { it.id() == hoveredNodeId }
                if (node != null) {
                    GraphTooltip(
                        node = node,
                        position = tooltipPos,
                        options = tooltipOptions,
                        // Provide bounds to keep tooltip on screen
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = widthPx.toInt(), maxHeight = heightPx.toInt())
                    )
                }
            }
        }
    }
}