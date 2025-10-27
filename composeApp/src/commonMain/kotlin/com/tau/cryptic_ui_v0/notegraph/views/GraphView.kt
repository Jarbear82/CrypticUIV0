package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale // Correct import for scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.views.labelToColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*

// --- Constants ---
const val NODE_RADIUS_DP = 25f
const val EDGE_ARROW_LENGTH = 15f
val EDGE_ARROW_ANGLE = (PI / 6).toFloat() // 30 degrees
const val SELF_LOOP_RADIUS = 40f
val SELF_LOOP_OFFSET_ANGLE = (PI / 4).toFloat() // 45 degrees

// --- Graph Composable ---
@OptIn(ExperimentalTextApi::class) // Needed for TextMeasurer
@Composable
fun GraphView(
    nodes: List<NodeDisplayItem>,
    edges: List<EdgeDisplayItem>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val nodeRadiusPx = remember(density) { with(density) { NODE_RADIUS_DP.dp.toPx() } }
    val strokeWidthPx = remember(density) { with(density) { 2.dp.toPx() } } // Pre-calculate stroke width
    val dragStrokeWidthPx = remember(density) { with(density) { 4.dp.toPx() } } // Pre-calculate drag stroke width

    // Initialize viewOffset later, after size is known
    var viewOffset by remember { mutableStateOf<Offset?>(null) }
    var viewScale by remember { mutableStateOf(1f) } // Basic zoom/pan state

    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    var physicsStabilized by remember { mutableStateOf(false) }
    var restartPhysics by remember { mutableStateOf(0) } // Trigger to restart simulation

    // State map for node positions
    val nodePositions = remember { mutableStateMapOf<String, Offset>() }
    // State map for node drawing sizes (updated when label size changes)
    val nodeSizes = remember { mutableStateMapOf<String, Pair<Float, Float>>() }

    // Text Measurer for labels
    val textMeasurer = rememberTextMeasurer()

    // Initialize Physics Engine
    val physicsEngine = remember { mutableStateOf<PhysicsEngine?>(null) }


    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Set initial viewOffset to center only once when size is first determined
        LaunchedEffect(widthPx, heightPx) {
            if (viewOffset == null && widthPx > 0 && heightPx > 0) {
                viewOffset = Offset(widthPx / 2f, heightPx / 2f)
            }
        }


        // Effect to initialize and update physics engine when data or size changes
        LaunchedEffect(nodes, edges, widthPx, heightPx) {
            if (widthPx > 0 && heightPx > 0) {
                val engine = physicsEngine.value
                if (engine == null) {
                    physicsEngine.value = PhysicsEngine(nodes, edges, widthPx, heightPx).also {
                        // Initialize positions from engine
                        nodes.forEach { node ->
                            it.getNodePosition(node.id())?.let { pos ->
                                nodePositions[node.id()] = pos
                            }
                        }
                        physicsStabilized = false // Start simulation on init
                    }
                } else {
                    engine.updateData(nodes, edges)
                    // Update existing positions from engine (for added nodes)
                    nodes.forEach { node ->
                        val nodeId = node.id()
                        if (!nodePositions.containsKey(nodeId)) {
                            engine.getNodePosition(nodeId)?.let { pos ->
                                nodePositions[nodeId] = pos
                            }
                        }
                    }
                    // Keep existing nodes at their current position unless physics moves them
                    physicsStabilized = false // Restart simulation on data change
                }
                restartPhysics++ // Trigger simulation loop
            }
        }


        // Effect for running physics simulation
        LaunchedEffect(physicsEngine.value, restartPhysics) {
            val engine = physicsEngine.value ?: return@LaunchedEffect
            while (isActive && !physicsStabilized) {
                physicsStabilized = engine.simulateStep()
                // Update node positions based on physics
                engine.getNodePositionMap().forEach { (id, pos) ->
                    if (nodePositions[id] != pos) {
                        nodePositions[id] = pos
                    }
                }
                delay(16) // ~60 FPS
            }
        }

        // --- Canvas for Drawing ---
        // Only draw if viewOffset has been initialized
        if (viewOffset != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val currentViewOffset = viewOffset ?: Offset.Zero // Use current offset
                                val canvasOffset = (offset - currentViewOffset) / viewScale // Apply inverse transform correctly
                                val nodeId = findNodeAt(canvasOffset, nodePositions, nodeRadiusPx)
                                if (nodeId != null) {
                                    draggedNodeId = nodeId
                                    // Optionally temporarily disable physics for dragged node
                                } else {
                                    // Start panning the view - Store initial offset for panning
                                    // dragStartOffset = offset - currentViewOffset
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (draggedNodeId != null) {
                                    val currentPos = nodePositions[draggedNodeId!!] ?: Offset.Zero
                                    val newPos = currentPos + dragAmount / viewScale
                                    nodePositions[draggedNodeId!!] = newPos
                                    physicsEngine.value?.updateNodePosition(draggedNodeId!!, newPos.x, newPos.y)
                                    physicsStabilized = false // Restart physics
                                    restartPhysics++
                                } else {
                                    // Pan the view
                                    viewOffset = (viewOffset ?: Offset.Zero) + dragAmount
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                if (draggedNodeId != null) {
                                    // Optionally re-enable physics for the node
                                    draggedNodeId = null
                                    physicsStabilized = false // Ensure physics runs to stabilize
                                    restartPhysics++
                                } else {
                                    // End panning
                                }
                            }
                        )
                    }
                // Add zoom gestures here if needed using transformable modifier
            ) {
                // Apply view transformations - Use the non-null viewOffset
                translate(left = viewOffset!!.x, top = viewOffset!!.y) {
                    // Correct scale function call
                    scale(scale = viewScale, pivot = Offset.Zero) {
                        val scaledStrokeWidth = strokeWidthPx / viewScale
                        val scaledDragStrokeWidth = dragStrokeWidthPx / viewScale

                        // --- Draw Edges ---
                        edges.forEach { edge ->
                            val fromId = edge.src.id()
                            val toId = edge.dst.id()
                            val fromPos = nodePositions[fromId]
                            val toPos = nodePositions[toId]

                            if (fromPos != null && toPos != null) {
                                val colorInfo = labelToColor(edge.label) // Color based on edge label

                                if (fromId == toId) {
                                    // Draw self-referencing loop
                                    drawSelfLoop(this, fromPos, nodeRadiusPx, colorInfo.composeColor, scaledStrokeWidth)
                                    // Draw label near loop
                                    drawLabelCompose(
                                        drawScope = this,
                                        textMeasurer = textMeasurer,
                                        text = edge.label,
                                        position = calculateSelfLoopLabelPosition(fromPos, nodeRadiusPx),
                                        color = Color.Black // Fixed color for now
                                    )
                                } else {
                                    // Draw straight line edge
                                    val (startPos, endPos) = calculateEdgeEndpoints(fromPos, toPos, nodeRadiusPx, nodeRadiusPx) // Assuming circle nodes for now
                                    drawLine(
                                        color = colorInfo.composeColor, // Use edge color
                                        start = startPos,
                                        end = endPos,
                                        strokeWidth = scaledStrokeWidth
                                    )
                                    // Draw arrow head
                                    drawArrowHead(this, startPos, endPos, colorInfo.composeColor, viewScale)
                                    // Draw label near midpoint
                                    val midPoint = (startPos + endPos) / 2f
                                    drawLabelCompose(
                                        drawScope = this,
                                        textMeasurer = textMeasurer,
                                        text = edge.label,
                                        position = midPoint,
                                        color = Color.Black // Fixed color for now
                                    )
                                }
                            }
                        }

                        // --- Draw Nodes ---
                        nodes.forEach { node ->
                            val nodeId = node.id()
                            val position = nodePositions[nodeId]
                            if (position != null) {
                                val colorInfo = labelToColor(node.label) // Color based on node label
                                val isDragged = nodeId == draggedNodeId

                                // Draw node circle border if dragged
                                if (isDragged) {
                                    drawCircle(
                                        color = Color.Red,
                                        radius = nodeRadiusPx,
                                        center = position,
                                        style = Stroke(width = scaledDragStrokeWidth) // Use scaled width
                                    )
                                }
                                // Draw node circle fill
                                drawCircle(
                                    color = colorInfo.composeColor,
                                    radius = nodeRadiusPx,
                                    center = position
                                )


                                // Draw node label
                                val labelText = "${node.label}\n(${node.primarykeyProperty.value})"
                                drawLabelCompose(
                                    drawScope = this,
                                    textMeasurer = textMeasurer,
                                    text = labelText,
                                    position = position,
                                    color = colorInfo.composeFontColor,
                                    maxTextWidth = nodeRadiusPx * 1.8f // Pass max width constraint
                                ) { width, height ->
                                    nodeSizes[nodeId] = Pair(width, height) // Store measured size
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Helper Functions ---

// Find node at a given canvas position
private fun findNodeAt(
    canvasPos: Offset,
    positions: Map<String, Offset>,
    radius: Float
): String? {
    var closestNode: String? = null
    var minDistSq = radius * radius // Use squared distance

    positions.forEach { (id, pos) ->
        val dx = pos.x - canvasPos.x
        val dy = pos.y - canvasPos.y
        val distSq = dx * dx + dy * dy
        if (distSq <= minDistSq) { // Check if click is inside the circle
            closestNode = id
            // Update minDistSq if you want the absolutely closest node, even if slightly outside the radius.
            // minDistSq = distSq
        }
    }
    return closestNode
}

// Calculate intersection points for edges
private fun calculateEdgeEndpoints(fromPos: Offset, toPos: Offset, fromRadius: Float, toRadius: Float): Pair<Offset, Offset> {
    val dx = toPos.x - fromPos.x
    val dy = toPos.y - fromPos.y
    val distSq = dx * dx + dy * dy

    // Handle potential division by zero or sqrt of negative (although distance should be non-negative)
    if (distSq <= (fromRadius + toRadius).pow(2) || distSq == 0f) { // Added check for zero distance
        return Pair(fromPos, toPos) // Draw line between centers or if nodes are exactly at the same place
    }

    val dist = sqrt(distSq)
    val ratioFrom = fromRadius / dist
    val ratioTo = toRadius / dist

    val startX = fromPos.x + dx * ratioFrom
    val startY = fromPos.y + dy * ratioFrom
    val endX = toPos.x - dx * ratioTo
    val endY = toPos.y - dy * ratioTo

    return Pair(Offset(startX, startY), Offset(endX, endY))
}


// Draw Arrow Head
private fun drawArrowHead(drawScope: DrawScope, startPos: Offset, endPos: Offset, color: Color, viewScale: Float) {
    val dx = endPos.x - startPos.x
    val dy = endPos.y - startPos.y
    val angle = atan2(dy, dx)
    val headLength = EDGE_ARROW_LENGTH / viewScale // Adjust for scale

    val angle1 = angle + EDGE_ARROW_ANGLE
    val angle2 = angle - EDGE_ARROW_ANGLE

    val arrowX1 = endPos.x - headLength * cos(angle1)
    val arrowY1 = endPos.y - headLength * sin(angle1)
    val arrowX2 = endPos.x - headLength * cos(angle2)
    val arrowY2 = endPos.y - headLength * sin(angle2)

    val path = Path().apply {
        moveTo(endPos.x, endPos.y)
        lineTo(arrowX1, arrowY1)
        lineTo(arrowX2, arrowY2)
        close()
    }
    drawScope.drawPath(path, color = color)
}

// Draw Self Loop
private fun drawSelfLoop(drawScope: DrawScope, nodePos: Offset, nodeRadius: Float, color: Color, strokeWidth: Float) {
    val loopRadius = SELF_LOOP_RADIUS
    val controlOffsetAngle = SELF_LOOP_OFFSET_ANGLE

    // Calculate center of the loop circle
    val centerAngle = controlOffsetAngle - (PI / 2f).toFloat() // Angle from node center to loop center
    val centerDist = nodeRadius + loopRadius // Distance from node center to loop center
    val loopCenterX = nodePos.x + centerDist * cos(centerAngle)
    val loopCenterY = nodePos.y + centerDist * sin(centerAngle)

    // Calculate start/end angle on the loop circle (where it meets the node)
    // Using geometry: triangle nodeCenter-loopCenter-touchPoint
    val angleNodeToLoopCenter = atan2(loopCenterY - nodePos.y, loopCenterX - nodePos.x)
    // Ensure acos argument is within [-1, 1] due to potential float inaccuracies
    val acosArg = (nodeRadius / centerDist).coerceIn(-1f, 1f)
    val angleOffsetToTouchPoint = acos(acosArg) // Half angle subtended by node at loop center

    val startAngleRad = angleNodeToLoopCenter - angleOffsetToTouchPoint + PI.toFloat() // Adjust for canvas/math angle difference
    val endAngleRad = angleNodeToLoopCenter + angleOffsetToTouchPoint + PI.toFloat() // Adjust for canvas/math angle difference

    // Correct conversion to degrees and handling angle wrap-around for sweepAngle
    val startAngleDeg = (startAngleRad * (180f / PI.toFloat()))
    var sweepAngleDeg = (endAngleRad - startAngleRad) * (180f / PI.toFloat())

    // Ensure sweep angle is positive and less than 360
    while (sweepAngleDeg < 0) sweepAngleDeg += 360f
    sweepAngleDeg %= 360f
    if (sweepAngleDeg == 0f && startAngleRad != endAngleRad) sweepAngleDeg = 360f // Handle full circle case if needed


    // Draw the arc
    drawScope.drawArc(
        color = color,
        startAngle = startAngleDeg - 90, // Adjust start angle for drawArc
        sweepAngle = sweepAngleDeg,
        useCenter = false,
        topLeft = Offset(loopCenterX - loopRadius, loopCenterY - loopRadius),
        size = Size(loopRadius * 2, loopRadius * 2),
        style = Stroke(width = strokeWidth)
    )

    // Draw arrowhead on the loop (approximate position)
    val arrowPosAngle = endAngleRad - 0.1f // Slightly before end
    val arrowTipX = loopCenterX + loopRadius * cos(arrowPosAngle)
    val arrowTipY = loopCenterY + loopRadius * sin(arrowPosAngle)
    // Tangent angle is approx arrowPosAngle + PI/2
    val arrowTangentAngle = arrowPosAngle + PI.toFloat() / 2f

    drawArrowHeadLoop(drawScope, Offset(arrowTipX, arrowTipY), arrowTangentAngle, color, 1f) // Pass viewScale=1f for now
}

// Helper to draw arrowhead for self-loop
private fun drawArrowHeadLoop(drawScope: DrawScope, tipPos: Offset, tangentAngle: Float, color: Color, viewScale: Float) {
    val headLength = EDGE_ARROW_LENGTH / viewScale // Adjust for scale

    val angle1 = tangentAngle + EDGE_ARROW_ANGLE
    val angle2 = tangentAngle - EDGE_ARROW_ANGLE

    val arrowX1 = tipPos.x - headLength * cos(angle1)
    val arrowY1 = tipPos.y - headLength * sin(angle1)
    val arrowX2 = tipPos.x - headLength * cos(angle2)
    val arrowY2 = tipPos.y - headLength * sin(angle2)

    val path = Path().apply {
        moveTo(tipPos.x, tipPos.y)
        lineTo(arrowX1, arrowY1)
        lineTo(arrowX2, arrowY2)
        close()
    }
    drawScope.drawPath(path, color = color)
}


// Calculate label position for self-loop
private fun calculateSelfLoopLabelPosition(nodePos: Offset, nodeRadius: Float): Offset {
    val loopRadius = SELF_LOOP_RADIUS
    val controlOffsetAngle = SELF_LOOP_OFFSET_ANGLE

    val centerAngle = controlOffsetAngle - (PI / 2f).toFloat() // Angle from node center to loop center
    val centerDist = nodeRadius + loopRadius // Distance from node center to loop center
    val loopCenterX = nodePos.x + centerDist * cos(centerAngle)
    val loopCenterY = nodePos.y + centerDist * sin(centerAngle)

    // Position label slightly further out from the loop center
    val labelDist = loopRadius * 1.5f // Adjust as needed
    val labelAngle = centerAngle // Align with the loop's direction
    return Offset(
        loopCenterX + labelDist * cos(labelAngle),
        loopCenterY + labelDist * sin(labelAngle)
    )
}

// --- Text Drawing using TextMeasurer ---
@OptIn(ExperimentalTextApi::class)
fun drawLabelCompose(
    drawScope: DrawScope,
    textMeasurer: TextMeasurer,
    text: String?,
    position: Offset,
    color: Color = Color.Black,
    fontSize: TextUnit = 12.sp,
    maxTextWidth: Float = Float.POSITIVE_INFINITY,
    onMeasured: (Float, Float) -> Unit = { _, _ -> }
) {
    if (text.isNullOrBlank()) return

    val textStyle = TextStyle(
        color = color,
        fontSize = fontSize,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
        // Add other style attributes like fontWeight if needed
    )

    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(text),
        style = textStyle,
        // Basic wrapping constraint - might need adjustment
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxTextWidth.roundToInt())
    )

    val textSize = textLayoutResult.size
    val topLeft = Offset(
        position.x - textSize.width / 2f,
        position.y - textSize.height / 2f
    )

    drawScope.drawText(
        textLayoutResult = textLayoutResult,
        topLeft = topLeft
    )

    onMeasured(textSize.width.toFloat(), textSize.height.toFloat())
}


// Extension to get node positions map from engine
fun PhysicsEngine.getNodePositionMap(): Map<String, Offset> {
    // Access the internal nodes map safely
    return this.nodes.mapValues { Offset(it.value.x, it.value.y) }
}