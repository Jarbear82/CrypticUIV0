package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
// --- ADD THIS IMPORT ---
import androidx.compose.material3.CircularProgressIndicator
// ---
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
// FIX: Add necessary import
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.views.labelToColor
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalTextApi::class)
@Composable
fun GraphView(
    nodes: List<NodeDisplayItem>,
    edges: List<EdgeDisplayItem>,
    modifier: Modifier = Modifier,
    // Pass Options
    nodeStyleOptions: NodeStyleOptions = NodeStyleOptions(),
    edgeStyleOptions: EdgeStyleOptions = EdgeStyleOptions(),
    layoutOptions: LayoutOptions = LayoutOptions(),
    physicsOptions: PhysicsOptions = PhysicsOptions(), // This is now a default
    interactionOptions: InteractionOptions = InteractionOptions(),
    selectionOptions: SelectionStyleOptions = SelectionStyleOptions(),
    tooltipOptions: TooltipOptions = TooltipOptions(),
    navigationOptions: NavigationOptions = NavigationOptions(),
    // New parameter to show/hide settings
    showSettingsUI: Boolean = true
) {
    val density = LocalDensity.current

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
    val edgeStrokeWidthPx = remember(density, edgeStyleOptions.defaultStrokeWidthDp) {
        with(density) { edgeStyleOptions.defaultStrokeWidthDp.toPx() }
    }
    val edgeSelectedStrokeWidthPx = remember(density, edgeStrokeWidthPx, selectionOptions.edgeSelectedStrokeWidthMultiplier) {
        edgeStrokeWidthPx * selectionOptions.edgeSelectedStrokeWidthMultiplier
    }

    var viewOffset by remember { mutableStateOf<Offset?>(null) }
    var viewScale by remember { mutableStateOf(1f) }
    val focusRequester = remember { FocusRequester() }
    // FIX: State to track if focus has been requested once
    var focusRequested by remember { mutableStateOf(false) }

    val nodePositions = remember { mutableStateMapOf<String, Offset>() }

    var selectedNodeIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedEdgeIds by remember { mutableStateOf(emptySet<String>()) }
    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    var hoveredNodeInfo by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    var isShiftPressed by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope()

    // --- STATE FOR CONFIGURABLE OPTIONS ---
    // Hold the physics options in an internal state, initialized by the parameter.
    // This allows the settings UI to modify this state.
    var internalPhysicsOptions by remember(physicsOptions) { mutableStateOf(physicsOptions) }
    // ---

    // Update derived physics options when the *internal* state changes
    val updatedPhysicsOptions = remember(internalPhysicsOptions, nodeRadiusPx) {
        internalPhysicsOptions.copy(nodeRadius = nodeRadiusPx)
    }

    fun screenToCanvas(screenPos: Offset): Offset {
        return (screenPos - (viewOffset ?: Offset.Zero)) / viewScale
    }

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
        viewScale = min(scaleX, scaleY) * 0.9f

        viewOffset = Offset(
            (widthPx / 2f) - (graphCenterX * viewScale),
            (heightPx / 2f) - (graphCenterY * viewScale)
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Create/recreate the LayoutEngine when size or *updated* options change
        // `updatedPhysicsOptions` now depends on `internalPhysicsOptions`
        val layoutEngine = remember(widthPx, heightPx, layoutOptions, updatedPhysicsOptions) {
            if (widthPx > 0 && heightPx > 0) {
                LayoutEngine(
                    nodes, // Pass initial nodes
                    edges, // Pass initial edges
                    widthPx,
                    heightPx,
                    coroutineScope,
                    layoutOptions,
                    updatedPhysicsOptions
                )
            } else {
                null
            }
        }

        // --- ADD THIS ---
        // Collect the stability state from the engine
        val isStable by layoutEngine?.isStable?.collectAsState() ?: remember { mutableStateOf(true) }
        // ---

        // Update interaction handler with the current layout engine
        val interactionHandler = remember(nodeRadiusPx, layoutEngine, viewOffset, viewScale) {
            GraphInteractionHandler(
                nodePositionsProvider = { nodePositions },
                nodeRadiusProvider = { nodeRadiusPx },
                viewOffsetProvider = { viewOffset ?: Offset.Zero },
                viewScaleProvider = { viewScale },
                physicsEngineNotifier = { nodeId, newPos, isDragging ->
                    layoutEngine?.notifyNodePositionUpdate(nodeId, newPos.x, newPos.y, isDragging)
                },
                options = interactionOptions
            )
        }

        // Collect positions from the current engine
        LaunchedEffect(layoutEngine) {
            if (layoutEngine == null) {
                nodePositions.clear()
            } else {
                layoutEngine.nodePositionsState.collect { positions ->
                    nodePositions.clear()
                    nodePositions.putAll(positions)
                }
            }
        }

        // Update engine with new data when nodes/edges change
        LaunchedEffect(layoutEngine, nodes, edges) {
            layoutEngine?.updateData(nodes, edges)
        }

        // Set initial view offset once
        LaunchedEffect(widthPx, heightPx) {
            if (widthPx > 0 && heightPx > 0 && viewOffset == null) {
                viewOffset = Offset(widthPx / 2f, heightPx / 2f)
            }
        }

        if (viewOffset != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    // (Pointer input modifiers unchanged)
                    .pointerInput(interactionHandler, interactionOptions.selectionEnabled, interactionOptions.tooltipsEnabled) {
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
                                    selectedEdgeIds = emptySet()
                                } else {
                                    selectedNodeIds = emptySet()
                                    selectedEdgeIds = emptySet()
                                }
                                hoveredNodeInfo = null
                            },
                            onLongPress = { screenPos ->
                                if (!interactionOptions.tooltipsEnabled) return@detectTapGestures
                                val canvasPos = screenToCanvas(screenPos)
                                val nodeId = interactionHandler.findNodeAt(canvasPos, nodePositions, nodeRadiusPx)
                                if (nodeId != null) {
                                    hoveredNodeInfo = nodeId to screenPos
                                }
                            }
                        )
                    }
                    .pointerInput(interactionHandler, draggedNodeId, interactionOptions.dragView, interactionOptions.zoomView) {
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
                        interactionHandler.handleNodeDragGestures(
                            pointerInputScope = this,
                            onDragStart = { nodeId -> draggedNodeId = nodeId; hoveredNodeInfo = null },
                            onDragEnd = { draggedNodeId = null }
                        )
                    }
                    .focusRequester(focusRequester)
                    .focusable()
                    // FIX: Request focus once the Canvas is positioned
                    .onGloballyPositioned {
                        if (!focusRequested && interactionOptions.keyboardNavigationEnabled) {
                            // Only request if keyboard nav is enabled
                            focusRequester.requestFocus()
                            focusRequested = true
                        }
                    }
                    .onKeyEvent { event ->
                        // Only handle if keyboard nav is enabled
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
                            false // Don't consume Up events generally
                        } else {
                            false // Don't consume other event types
                        }
                    }
            ) {
                translate(left = viewOffset!!.x, top = viewOffset!!.y) {
                    scale(scale = viewScale, pivot = Offset.Zero) {
                        val scaledNodeStrokeWidth = nodeStrokeWidthPx / viewScale
                        val scaledDragStrokeWidth = nodeDragStrokeWidthPx / viewScale
                        val scaledNodeSelectedStrokeWidth = nodeSelectedStrokeWidthPx / viewScale
                        val scaledEdgeStrokeWidth = edgeStrokeWidthPx / viewScale
                        val scaledEdgeSelectedStrokeWidth = edgeSelectedStrokeWidthPx / viewScale

                        // (Drawing logic unchanged)
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

                        nodes.forEach { node ->
                            val nodeId = node.id()
                            val position = nodePositions[nodeId]
                            if (position != null) {
                                val colorInfo = labelToColor(node.label)
                                val isDragged = nodeId == draggedNodeId
                                val isSelected = selectedNodeIds.contains(nodeId)

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

                                drawCircle(
                                    color = colorInfo.composeColor,
                                    radius = nodeRadiusPx,
                                    center = position
                                )

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

            // --- NAVIGATION AND SETTINGS UI ---
            if (navigationOptions.showNavigationUI) {
                GraphNavigationUI(
                    modifier = Modifier.align(Alignment.BottomStart), // Aligned to BottomStart
                    options = navigationOptions,
                    onZoomIn = { viewScale *= 1.2f },
                    onZoomOut = { viewScale *= 0.8f },
                    onFit = { fitGraphToView(widthPx, heightPx) }
                )
            }

            if (showSettingsUI) {
                GraphSettingsUI(
                    modifier = Modifier.align(Alignment.TopEnd), // Aligned to TopEnd
                    options = internalPhysicsOptions,
                    onOptionsChange = { newOptions ->
                        internalPhysicsOptions = newOptions
                    }
                )
            }
            // ---

            val (hoveredNodeId, tooltipPos) = hoveredNodeInfo ?: (null to null)
            if (hoveredNodeId != null && tooltipPos != null) {
                val node = nodes.find { it.id() == hoveredNodeId }
                if (node != null) {
                    GraphTooltip(
                        node = node,
                        position = tooltipPos,
                        options = tooltipOptions,
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = widthPx.toInt(), maxHeight = heightPx.toInt())
                    )
                }
            }
        }

        // --- ADD THIS ---
        // Show a loading spinner in the center if the layout is not stable
        if (!isStable) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
        // ---
    }
}