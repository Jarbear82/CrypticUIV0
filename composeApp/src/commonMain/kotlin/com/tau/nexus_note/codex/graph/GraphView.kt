package com.tau.nexus_note.codex.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.GraphNode
import com.tau.nexus_note.GraphEdge
import com.tau.nexus_note.NodeDisplayItem
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalGraphicsApi::class, ExperimentalTextApi::class, ExperimentalComposeUiApi::class)
@Composable
fun GraphView(viewModel: GraphViewmodel) {
    val nodes by viewModel.graphNodes.collectAsState()
    val edges by viewModel.graphEdges.collectAsState()
    val transform by viewModel.transform.collectAsState()
    val showFabMenu by viewModel.showFabMenu.collectAsState()

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val crosshairColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val selectionColor = MaterialTheme.colorScheme.primary

    // ADDED: State to distinguish node drag from pan
    var isDraggingNode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.runSimulationLoop()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Combined gesture detector for drag and tap
                detectDragGestures(
                    onDragStart = { offset ->
                        // Check if we started on a node
                        isDraggingNode = viewModel.onDragStart(offset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (isDraggingNode) {
                            viewModel.onDrag(dragAmount)
                        } else {
                            viewModel.onPan(dragAmount)
                        }
                    },
                    onDragEnd = {
                        if (isDraggingNode) {
                            viewModel.onDragEnd()
                        }
                        isDraggingNode = false
                    }
                )
            }
            .pointerInput(Unit) {
                // Tap detector for selection
                detectTapGestures(
                    onTap = { offset ->
                        viewModel.onTap(offset)
                    }
                )
            }
            .onPointerEvent(PointerEventType.Scroll) {
                // Zoom
                it.changes.firstOrNull()?.let { change ->
                    val zoomDelta = if (change.scrollDelta.y < 0) 1.2f else 0.8f
                    viewModel.onZoom(zoomDelta, change.position)
                    change.consume()
                }
            }
            .onSizeChanged {
                viewModel.onResize(it)
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // --- Draw Graph Elements (inside transform) ---
            withTransform({
                translate(left = center.x, top = center.y)
                scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                translate(left = transform.pan.x, top = transform.pan.y)
            }) {

                // --- 1. Draw Edges ---

                // Pre-process edges to find counts for curving
                val edgesByPair = edges.groupBy { Pair(it.sourceId, it.targetId) }
                val linkCounts = mutableMapOf<Pair<Long, Long>, Int>()
                val uniquePairs = mutableSetOf<Pair<Long, Long>>()

                for (edge in edges) {
                    if (edge.sourceId == edge.targetId) continue
                    val pair = if (edge.sourceId < edge.targetId) Pair(edge.sourceId, edge.targetId) else Pair(edge.targetId, edge.sourceId)
                    uniquePairs.add(pair)
                }

                for (pair in uniquePairs) {
                    val count = (edgesByPair[pair]?.size ?: 0) + (edgesByPair[Pair(pair.second, pair.first)]?.size ?: 0)
                    linkCounts[pair] = count
                }

                val pairDrawIndex = mutableMapOf<Pair<Long, Long>, Int>()
                val selfLoopDrawIndex = mutableMapOf<Long, Int>()

                // Style for edge labels
                val edgeLabelStyle = TextStyle(
                    fontSize = (10.sp.value / transform.zoom.coerceAtLeast(0.1f)).coerceIn(8.sp.value, 14.sp.value).sp,
                    color = labelColor.copy(alpha = 0.8f)
                )

                for (edge in edges) {
                    val nodeA = nodes[edge.sourceId]
                    val nodeB = nodes[edge.targetId]
                    if (nodeA == null || nodeB == null) continue

                    if (nodeA.id == nodeB.id) {
                        // Self-loop
                        val index = selfLoopDrawIndex.getOrPut(nodeA.id) { 0 }
                        drawSelfLoop(nodeA, edge, index, textMeasurer, edgeLabelStyle)
                        selfLoopDrawIndex[nodeA.id] = index + 1
                    } else {
                        // Standard edge
                        val pair = Pair(nodeA.id, nodeB.id)
                        val undirectedPair = if (nodeA.id < nodeB.id) Pair(nodeA.id, nodeB.id) else Pair(nodeB.id, nodeA.id)

                        val total = linkCounts[undirectedPair] ?: 1
                        val index = pairDrawIndex.getOrPut(pair) { 0 }

                        drawCurvedEdge(nodeA, nodeB, edge, index, total, textMeasurer, edgeLabelStyle)

                        pairDrawIndex[pair] = index + 1
                    }
                }

                // --- 2. Draw Nodes ---
                // (Nodes are drawn *after* edges)
                drawNodes(nodes, textMeasurer, labelColor, selectionColor, transform.zoom, viewModel)
            }

            // --- Draw UI Elements (outside transform) ---
            val crosshairSize = 10f
            drawLine(
                color = crosshairColor,
                start = Offset(center.x - crosshairSize, center.y),
                end = Offset(center.x + crosshairSize, center.y),
                strokeWidth = 2f
            )
            drawLine(
                color = crosshairColor,
                start = Offset(center.x, center.y - crosshairSize),
                end = Offset(center.x, center.y + crosshairSize),
                strokeWidth = 2f
            )
        }

        // --- ADDED: Floating Action Button Menu ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Animated menu items
            AnimatedVisibility(visible = showFabMenu) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Create Node
                    SmallFloatingActionButton(
                        onClick = { viewModel.onFabCreateNodeClick() },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = "Create Node")
                    }
                    // Create Edge
                    SmallFloatingActionButton(
                        onClick = { viewModel.onFabCreateEdgeClick() },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Create Edge")
                    }
                }
            }
            // Main FAB
            FloatingActionButton(
                onClick = { viewModel.onFabClick() }
            ) {
                Icon(
                    imageVector = if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Toggle Create Menu"
                )
            }
        }
    }
}

/**
 * Draws a self-referencing loop (edge from a node to itself).
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawSelfLoop(
    node: GraphNode,
    edge: GraphEdge,
    index: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle
) {
    val color = edge.colorInfo.composeColor.copy(alpha = 0.7f)
    val strokeWidth = 2f
    val arrowSize = 6f

    // Constants for loop shape
    val loopRadius = 25f + (index * 12f)
    val loopSeparation = node.radius + 5f

    // Calculate attachment points on the node's circumference
    // We'll attach to the top-right quadrant
    val startAngle = (45f).degToRad()
    val endAngle = (-30f).degToRad()

    val p1 = node.pos + Offset(cos(startAngle) * node.radius, sin(startAngle) * node.radius)
    val p4 = node.pos + Offset(cos(endAngle) * node.radius, sin(endAngle) * node.radius)

    // Calculate control points to make a nice loop
    val controlOffset = loopSeparation + loopRadius
    val p2 = p1 + Offset(cos(startAngle) * controlOffset, sin(startAngle) * controlOffset)
    val p3 = p4 + Offset(cos(endAngle) * controlOffset, sin(endAngle) * controlOffset)

    // Draw the cubic Bezier curve
    val path = Path().apply {
        moveTo(p1.x, p1.y)
        cubicTo(p2.x, p2.y, p3.x, p3.y, p4.x, p4.y)
    }
    drawPath(path, color, style = Stroke(strokeWidth))

    // Draw arrowhead
    drawArrowhead(p3, p4, color, arrowSize)

    // Draw label
    val labelPos = (p2 + p3) / 2f // Position label at the apex of the loop
    val textLayoutResult = textMeasurer.measure(AnnotatedString(edge.label), style)
    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = labelPos - Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f),
        color = color
    )
}

/**
 * Draws a directed edge between two different nodes.
 * Handles curving for multiple edges.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawCurvedEdge(
    from: GraphNode,
    to: GraphNode,
    edge: GraphEdge,
    index: Int,
    total: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle
) {
    val color = edge.colorInfo.composeColor.copy(alpha = 0.7f)
    val strokeWidth = 2f
    val arrowSize = 6f

    val start = from.pos
    val end = to.pos
    val delta = end - start
    val midPoint = (start + end) / 2f

    val isStraight = (total == 1)

    if (isStraight) {
        // --- Draw Straight Line ---
        val startWithRadius = from.pos + delta.normalized() * from.radius
        val endWithRadius = to.pos - delta.normalized() * to.radius

        drawLine(color, startWithRadius, endWithRadius, strokeWidth)
        drawArrowhead(startWithRadius, endWithRadius, color, arrowSize)

        // Draw label
        val labelOffset = Offset(0f, -10f) // Just above the line
        val textLayoutResult = textMeasurer.measure(AnnotatedString(edge.label), style)
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = midPoint + labelOffset - Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f),
            color = color
        )
    } else {
        // --- Draw Curved Line (Quadratic Bezier) ---
        val normal = Offset(-delta.y, delta.x).normalized()
        val baseCurvature = 30f // Base height of the curve

        // This maps index 0, 1, 2... to offsets 0, 1, -1, 2, -2...
        val curveSign = if (index % 2 == 0) -1 else 1
        val curveMagnitude = (index + 1) / 2
        val curveOffset = curveSign * curveMagnitude * (baseCurvature * 0.75f)

        // Control point is offset from the midpoint along the normal
        val controlPoint = midPoint + normal * (baseCurvature + curveOffset)

        // Adjust start/end points to touch node radius, pointing towards control point
        val startWithRadius = from.pos + (controlPoint - from.pos).normalized() * from.radius
        val endWithRadius = to.pos + (controlPoint - to.pos).normalized() * to.radius

        val path = Path().apply {
            moveTo(startWithRadius.x, startWithRadius.y)
            quadraticTo(controlPoint.x, controlPoint.y, endWithRadius.x, endWithRadius.y)
        }
        drawPath(path, color, style = Stroke(strokeWidth))

        // Draw arrowhead (approximating tangent)
        val tangent = (endWithRadius - controlPoint).normalized()
        drawArrowhead(endWithRadius - (tangent * arrowSize * 2f), endWithRadius, color, arrowSize)

        // Draw label at the control point
        val textLayoutResult = textMeasurer.measure(AnnotatedString(edge.label), style)
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = controlPoint - Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f),
            color = color
        )
    }
}

/**
 * Draws a small arrowhead at the 'to' point, oriented from 'from'.
 */
private fun DrawScope.drawArrowhead(from: Offset, to: Offset, color: Color, size: Float) {
    val delta = to - from
    if (delta == Offset.Zero) return // Cannot draw arrowhead

    val angle = atan2(delta.y, delta.x)
    val angleRad = angle.toFloat()

    val p1 = to + Offset(cos(angleRad + 150f.degToRad()) * size, sin(angleRad + 150f.degToRad()) * size)
    val p2 = to + Offset(cos(angleRad - 150f.degToRad()) * size, sin(angleRad - 150f.degToRad()) * size)

    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, color)
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNodes(
    nodes: Map<Long, GraphNode>,
    textMeasurer: TextMeasurer,
    labelColor: Color,
    selectionColor: Color,
    zoom: Float,
    viewModel: GraphViewmodel // ADDED: To check selection state
) {
    val minSize = 8.sp
    val maxSize = 14.sp
    val fontSize = ((12.sp.value / zoom.coerceAtLeast(0.1f)).coerceIn(minSize.value, maxSize.value)).sp
    val style = TextStyle(fontSize = fontSize, color = labelColor)

    // Get selected items from the viewmodel
    val primaryId = (viewModel.metadataViewModel.primarySelectedItem.value as? NodeDisplayItem)?.id
    val secondaryId = (viewModel.metadataViewModel.secondarySelectedItem.value as? NodeDisplayItem)?.id

    for (node in nodes.values) {
        val isSelected = node.id == primaryId || node.id == secondaryId

        // Draw circle
        drawCircle(
            color = node.colorInfo.composeColor,
            radius = node.radius,
            center = node.pos
        )
        // Draw border
        drawCircle(
            color = if (isSelected) selectionColor else node.colorInfo.composeFontColor,
            radius = node.radius,
            center = node.pos,
            style = Stroke(width = if (isSelected) 3f else 1f) // Thicker border if selected
        )

        // Draw label
        if (zoom > 0.5f) {
            val textLayoutResult = textMeasurer.measure(
                text = AnnotatedString(node.displayProperty),
                style = style
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = node.pos - Offset(
                    x = textLayoutResult.size.width / 2f,
                    y = textLayoutResult.size.height / 2f
                )
            )
        }
    }
}

// --- Math Helpers ---

private fun Offset.normalized(): Offset {
    val mag = this.getDistance()
    return if (mag == 0f) Offset.Zero else this / mag
}

private fun Float.degToRad(): Float {
    return this * (PI.toFloat() / 180f)
}
