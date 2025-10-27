package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.foundation.Canvas
// Remove detectDragGestures if handled by InteractionHandler directly
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.views.labelToColor

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
    interactionOptions: InteractionOptions = InteractionOptions()
) {
    val density = LocalDensity.current
    val nodeRadiusPx = remember(density, nodeStyleOptions.radiusDp) {
        with(density) { nodeStyleOptions.radiusDp.toPx() }
    }
    val strokeWidthPx = remember(density, nodeStyleOptions.defaultStrokeWidthDp) {
        with(density) { nodeStyleOptions.defaultStrokeWidthDp.toPx() }
    }
    val dragStrokeWidthPx = remember(density, strokeWidthPx, nodeStyleOptions.draggedStrokeWidthMultiplier) {
        strokeWidthPx * nodeStyleOptions.draggedStrokeWidthMultiplier
    }
    val edgeStrokeWidthPx = remember(density, edgeStyleOptions.defaultStrokeWidthDp) {
        with(density) { edgeStyleOptions.defaultStrokeWidthDp.toPx() }
    }


    // Hoisted State (could be moved to ViewModel)
    var viewOffset by remember { mutableStateOf<Offset?>(null) } // Initialize later
    var viewScale by remember { mutableStateOf(1f) }

    // State for node positions, sourced from Physics Engine
    val nodePositions = remember { mutableStateMapOf<String, Offset>() }
    // State for node drawing sizes (updated when label size changes)
    // val nodeSizes = remember { mutableStateMapOf<String, Pair<Float, Float>>() } // Keep if needed for layout adjustments

    // Text Measurer for labels
    val textMeasurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope() // Scope for physics engine

    // Initialize Physics Engine
    val physicsEngine = remember { mutableStateOf<PhysicsEngine?>(null) }

    // State to track dragged node (managed by InteractionHandler)
    var draggedNodeId by remember { mutableStateOf<String?>(null) }


    // Initialize Interaction Handler
    val interactionHandler = remember(nodeRadiusPx, physicsEngine.value) {
        GraphInteractionHandler(
            nodePositionsProvider = { nodePositions },
            nodeRadiusProvider = { nodeRadiusPx },
            viewOffsetProvider = { viewOffset ?: Offset.Zero },
            viewScaleProvider = { viewScale },
            updateViewOffset = { newOffset -> viewOffset = newOffset },
            updateViewScale = { newScale, _ -> viewScale = newScale }, // Ignoring pivot for simplicity now
            physicsEngineNotifier = { nodeId, newPos, isDragging ->
                physicsEngine.value?.notifyNodePositionUpdate(nodeId, newPos.x, newPos.y, isDragging)
                draggedNodeId = if (isDragging) nodeId else null // Update dragged state here
            },
            options = interactionOptions
        )
    }

    // Effect to collect position updates from Physics Engine
    LaunchedEffect(physicsEngine.value) {
        physicsEngine.value?.nodePositionsState?.collect { positions ->
            // Update the local state map, triggering recomposition
            nodePositions.clear() // Clear and add to ensure recomposition detects change
            nodePositions.putAll(positions)
        }
    }


    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Set initial viewOffset and initialize/update physics engine
        LaunchedEffect(widthPx, heightPx, nodes, edges) {
            if (widthPx > 0 && heightPx > 0) {
                // Initialize offset only once
                if (viewOffset == null) {
                    viewOffset = Offset(widthPx / 2f, heightPx / 2f)
                }

                val engine = physicsEngine.value
                if (engine == null) {
                    physicsEngine.value = PhysicsEngine(nodes, edges, widthPx, heightPx, coroutineScope, physicsOptions)
                } else {
                    engine.updateData(nodes, edges) // Use engine's update method
                }
            }
        }


        // --- Canvas for Drawing ---
        if (viewOffset != null) { // Only draw if viewOffset has been initialized
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(interactionHandler) { // Pass handler instance
                        interactionHandler.handleGestures(this)
                    }
            ) {
                // Apply view transformations
                translate(left = viewOffset!!.x, top = viewOffset!!.y) {
                    scale(scale = viewScale, pivot = Offset.Zero) {
                        val scaledNodeStrokeWidth = strokeWidthPx / viewScale
                        val scaledDragStrokeWidth = dragStrokeWidthPx / viewScale
                        val scaledEdgeStrokeWidth = edgeStrokeWidthPx / viewScale

                        // --- Draw Edges ---
                        edges.forEach { edge ->
                            val fromId = edge.src.id()
                            val toId = edge.dst.id()
                            val fromPos = nodePositions[fromId]
                            val toPos = nodePositions[toId]

                            if (fromPos != null && toPos != null) {
                                val colorInfo = labelToColor(edge.label)

                                if (fromId == toId) {
                                    // Use drawing util
                                    drawSelfLoop(this, fromPos, nodeRadiusPx, colorInfo.composeColor, scaledEdgeStrokeWidth)
                                    drawLabelCompose(
                                        drawScope = this,
                                        textMeasurer = textMeasurer,
                                        text = edge.label,
                                        position = calculateSelfLoopLabelPosition(fromPos, nodeRadiusPx),
                                        color = edgeStyleOptions.labelColor,
                                        fontSize = edgeStyleOptions.labelFontSizeSp.sp
                                    )
                                } else {
                                    // Use drawing util
                                    val (startPos, endPos) = calculateEdgeEndpoints(fromPos, toPos, nodeRadiusPx, nodeRadiusPx)
                                    drawLine(
                                        color = colorInfo.composeColor,
                                        start = startPos,
                                        end = endPos,
                                        strokeWidth = scaledEdgeStrokeWidth
                                    )
                                    // Use drawing util
                                    drawArrowHead(this, startPos, endPos, colorInfo.composeColor, viewScale)
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

                                // Draw node circle border if dragged
                                if (isDragged) {
                                    drawCircle(
                                        color = nodeStyleOptions.draggedBorderColor,
                                        radius = nodeRadiusPx,
                                        center = position,
                                        style = Stroke(width = scaledDragStrokeWidth) // Use scaled width
                                    )
                                } else {
                                    // Optional: Draw standard border
                                    drawCircle(
                                        color = colorInfo.composeFontColor.copy(alpha = 0.5f), // Example border color
                                        radius = nodeRadiusPx,
                                        center = position,
                                        style = Stroke(width = scaledNodeStrokeWidth) // Use scaled width
                                    )
                                }

                                // Draw node circle fill
                                drawCircle(
                                    color = colorInfo.composeColor,
                                    radius = nodeRadiusPx,
                                    center = position
                                )

                                // Draw node label - Use drawing util
                                val labelText = "${node.label}\n(${node.primarykeyProperty.value})"
                                drawLabelCompose(
                                    drawScope = this,
                                    textMeasurer = textMeasurer,
                                    text = labelText,
                                    position = position,
                                    color = colorInfo.composeFontColor, // Use color from theme/data
                                    fontSize = nodeStyleOptions.labelFontSizeSp.sp, // Use options
                                    maxTextWidth = nodeRadiusPx * 1.8f // Keep constraint
                                    // onMeasured callback removed for simplicity, add back if needed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}