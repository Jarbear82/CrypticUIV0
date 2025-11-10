package com.tau.nexus_note.codex.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
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
import com.tau.nexus_note.datamodels.GraphEdge
import com.tau.nexus_note.datamodels.GraphNode
import com.tau.nexus_note.datamodels.InternalGraph
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalGraphicsApi::class, ExperimentalTextApi::class, ExperimentalComposeUiApi::class)
@Composable
fun GraphView(
    viewModel: GraphViewmodel,
    nodes: Map<Long, GraphNode>,
    edges: List<GraphEdge>,
    primarySelectedId: Long?,
    secondarySelectedId: Long?,
    onNodeTap: (Long) -> Unit,
    onAddNodeClick: () -> Unit,
    onAddEdgeClick: () -> Unit,
    onDetangleClick: () -> Unit
) {
    val transform by viewModel.transform.collectAsState()
    val showFabMenu by viewModel.showFabMenu.collectAsState()
    val isDetangling by viewModel.isDetangling.collectAsState()
    val physicsOptions by viewModel.physicsOptions.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val isSimulationRunning by viewModel.simulationRunning.collectAsState()

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val crosshairColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val selectionColor = MaterialTheme.colorScheme.primary

    var isDraggingNode by remember { mutableStateOf(false) }

    LaunchedEffect(isSimulationRunning) {
        if (isSimulationRunning) {
            viewModel.runSimulationLoop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopSimulation()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
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
                detectTapGestures(
                    onTap = { offset ->
                        viewModel.onTap(offset, onNodeTap)
                    }
                )
            }
            .onPointerEvent(PointerEventType.Scroll) {
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
            withTransform({
                translate(left = center.x, top = center.y)
                scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                translate(left = transform.pan.x, top = transform.pan.y)
            }) {

                // --- 1. Draw Edges ---
                // (Edge drawing logic remains unchanged)
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

                val edgeLabelStyle = TextStyle(
                    fontSize = (10.sp.value / transform.zoom.coerceAtLeast(0.1f)).coerceIn(8.sp.value, 14.sp.value).sp,
                    color = labelColor.copy(alpha = 0.8f)
                )

                for (edge in edges) {
                    val nodeA = nodes[edge.sourceId]
                    val nodeB = nodes[edge.targetId]
                    if (nodeA == null || nodeB == null) continue

                    if (nodeA.id == nodeB.id) {
                        val index = selfLoopDrawIndex.getOrPut(nodeA.id) { 0 }
                        drawSelfLoop(nodeA, edge, index, textMeasurer, edgeLabelStyle)
                        selfLoopDrawIndex[nodeA.id] = index + 1
                    } else {
                        val pair = Pair(nodeA.id, nodeB.id)
                        val undirectedPair = if (nodeA.id < nodeB.id) Pair(nodeA.id, nodeB.id) else Pair(nodeB.id, nodeA.id)

                        val total = linkCounts[undirectedPair] ?: 1
                        val index = pairDrawIndex.getOrPut(pair) { 0 }

                        drawCurvedEdge(nodeA, nodeB, edge, index, total, textMeasurer, edgeLabelStyle)

                        pairDrawIndex[pair] = index + 1
                    }
                }

                // --- 2. Draw Nodes (MODIFIED) ---
                drawNodes(
                    nodes,
                    textMeasurer,
                    labelColor,
                    selectionColor,
                    transform.zoom,
                    primarySelectedId,
                    secondarySelectedId
                )
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

        // --- Detangling Lockout Overlay ---
        AnimatedVisibility(
            visible = isDetangling,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .pointerInput(Unit) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Detangling Graph... (Interaction Disabled)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // --- Settings Toggle and Panel ---
        SmallFloatingActionButton(
            onClick = { viewModel.toggleSettings() },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(Icons.Default.Settings, "Graph Settings")
        }

        AnimatedVisibility(
            visible = showSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)
        ) {
            GraphSettingsView(
                options = physicsOptions,
                onGravityChange = { viewModel.setGravity(it) },
                onRepulsionChange = { viewModel.setRepulsion(it) },
                onSpringChange = { viewModel.setSpring(it) },
                onDampingChange = { viewModel.setDamping(it) },
                onBarnesHutThetaChange = { viewModel.setBarnesHutTheta(it) },
                onToleranceChange = { viewModel.setTolerance(it) },
                onDetangleClick = onDetangleClick
            )
        }


        // --- Floating Action Button Menu ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(visible = showFabMenu) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { onAddNodeClick() },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = "Create Node")
                    }
                    SmallFloatingActionButton(
                        onClick = { onAddEdgeClick() },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Create Edge")
                    }
                }
            }
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

// (drawSelfLoop and drawCurvedEdge remain unchanged)
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
    val loopRadius = 25f + (index * 12f)
    val loopSeparation = node.radius + 5f
    val startAngle = (45f).degToRad()
    val endAngle = (-30f).degToRad()

    val p1 = node.pos + Offset(cos(startAngle) * node.radius, sin(startAngle) * node.radius)
    val p4 = node.pos + Offset(cos(endAngle) * node.radius, sin(endAngle) * node.radius)

    val controlOffset = loopSeparation + loopRadius
    val p2 = p1 + Offset(cos(startAngle) * controlOffset, sin(startAngle) * controlOffset)
    val p3 = p4 + Offset(cos(endAngle) * controlOffset, sin(endAngle) * controlOffset)

    val path = Path().apply {
        moveTo(p1.x, p1.y)
        cubicTo(p2.x, p2.y, p3.x, p3.y, p4.x, p4.y)
    }
    drawPath(path, color, style = Stroke(strokeWidth))

    drawArrowhead(p3, p4, color, arrowSize)

    val labelPos = (p2 + p3) / 2f
    val textLayoutResult = textMeasurer.measure(AnnotatedString(edge.label), style)
    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = labelPos - Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f),
        color = color
    )
}

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
        val startWithRadius = from.pos + delta.normalized() * from.radius
        val endWithRadius = to.pos - delta.normalized() * to.radius

        drawLine(color, startWithRadius, endWithRadius, strokeWidth)
        drawArrowhead(startWithRadius, endWithRadius, color, arrowSize)

        val labelOffset = Offset(0f, -10f)
        val textLayoutResult = textMeasurer.measure(AnnotatedString(edge.label), style)
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = midPoint + labelOffset - Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f),
            color = color
        )
    } else {
        val normal = Offset(-delta.y, delta.x).normalized()
        val baseCurvature = 30f
        val curveSign = if (index % 2 == 0) -1 else 1
        val curveMagnitude = (index + 1) / 2
        val curveOffset = curveSign * curveMagnitude * (baseCurvature * 0.75f)
        val controlPoint = midPoint + normal * (baseCurvature + curveOffset)
        val startWithRadius = from.pos + (controlPoint - from.pos).normalized() * from.radius
        val endWithRadius = to.pos + (controlPoint - to.pos).normalized() * to.radius

        val path = Path().apply {
            moveTo(startWithRadius.x, startWithRadius.y)
            quadraticTo(controlPoint.x, controlPoint.y, endWithRadius.x, endWithRadius.y)
        }
        drawPath(path, color, style = Stroke(strokeWidth))

        val tangent = (endWithRadius - controlPoint).normalized()
        drawArrowhead(endWithRadius - (tangent * arrowSize * 2f), endWithRadius, color, arrowSize)

        val textLayoutResult = textMeasurer.measure(AnnotatedString(edge.label), style)
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = controlPoint - Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f),
            color = color
        )
    }
}

private fun DrawScope.drawArrowhead(from: Offset, to: Offset, color: Color, size: Float) {
    val delta = to - from
    if (delta == Offset.Zero) return

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

/**
 * Main node drawing function.
 * This now acts as a router, calling drawSupernode or drawRegularNode.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNodes(
    nodes: Map<Long, GraphNode>,
    textMeasurer: TextMeasurer,
    labelColor: Color,
    selectionColor: Color,
    zoom: Float,
    primarySelectedId: Long?,
    secondarySelectedId: Long?
) {
    val minSize = 8.sp
    val maxSize = 14.sp
    val fontSize = ((12.sp.value / zoom.coerceAtLeast(0.1f)).coerceIn(minSize.value, maxSize.value)).sp
    val style = TextStyle(fontSize = fontSize, color = labelColor)

    for (node in nodes.values) {
        val isSelected = node.id == primarySelectedId || node.id == secondarySelectedId

        if (node.isSupernode && node.internalGraph != null) {
            drawSupernode(
                node = node,
                internalGraph = node.internalGraph,
                isSelected = isSelected,
                textMeasurer = textMeasurer,
                labelStyle = style,
                selectionColor = selectionColor
            )
        } else {
            drawRegularNode(
                node = node,
                isSelected = isSelected,
                textMeasurer = textMeasurer,
                labelStyle = style,
                selectionColor = selectionColor,
                zoom = zoom
            )
        }
    }
}

/**
 * NEW: Draws a Supernode container and its internal graph.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawSupernode(
    node: GraphNode,
    internalGraph: InternalGraph,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    selectionColor: Color
) {
    // 1. Draw Container
    drawCircle(
        color = node.colorInfo.composeColor.copy(alpha = 0.5f), // Make container semi-transparent
        radius = node.radius,
        center = node.pos
    )
    drawCircle(
        color = if (isSelected) selectionColor else node.colorInfo.composeFontColor,
        radius = node.radius,
        center = node.pos,
        style = Stroke(width = if (isSelected) 3f else 1f)
    )

    // 2. Draw Supernode's main label (outside)
    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(node.displayProperty),
        style = labelStyle
    )
    val textPadding = 3f
    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(
            x = node.pos.x - (textLayoutResult.size.width / 2f),
            y = node.pos.y + node.radius + textPadding
        )
    )

    // 3. Draw Internal Graph (clipped and translated)
    clipRect(
        left = node.pos.x - node.radius,
        top = node.pos.y - node.radius,
        right = node.pos.x + node.radius,
        bottom = node.pos.y + node.radius
    ) {
        // Translate the coordinate system's (0,0) to the supernode's center
        translate(left = node.pos.x, top = node.pos.y) {

            // Draw internal edges
            for (edge in internalGraph.edges) {
                val nodeA = internalGraph.nodes[edge.sourceId]
                val nodeB = internalGraph.nodes[edge.targetId]
                if (nodeA == null || nodeB == null) continue

                // nodeA.pos and nodeB.pos are already relative to (0,0)
                drawLine(
                    color = edge.colorInfo.composeColor,
                    start = nodeA.pos,
                    end = nodeB.pos,
                    strokeWidth = 1f
                )
            }

            // Draw internal nodes
            for (internalNode in internalGraph.nodes.values) {
                // Scale internal radius to be smaller
                val internalRadius = (internalNode.radius / 3f).coerceAtLeast(2f)
                drawCircle(
                    color = internalNode.colorInfo.composeColor,
                    radius = internalRadius,
                    center = internalNode.pos // Already relative to (0,0)
                )
            }
        }
    }
}

/**
 * The original logic for drawing a single node.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawRegularNode(
    node: GraphNode,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    selectionColor: Color,
    zoom: Float
) {
    drawCircle(
        color = node.colorInfo.composeColor,
        radius = node.radius,
        center = node.pos
    )
    drawCircle(
        color = if (isSelected) selectionColor else node.colorInfo.composeFontColor,
        radius = node.radius,
        center = node.pos,
        style = Stroke(width = if (isSelected) 3f else 1f)
    )

    if (zoom > 0.5f) {
        val textLayoutResult = textMeasurer.measure(
            text = AnnotatedString(node.displayProperty),
            style = labelStyle
        )
        val textPadding = 3f
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                x = node.pos.x - (textLayoutResult.size.width / 2f),
                y = node.pos.y + node.radius + textPadding
            )
        )
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