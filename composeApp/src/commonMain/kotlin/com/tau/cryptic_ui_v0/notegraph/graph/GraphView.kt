package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.ClusterDisplayItem
import com.tau.cryptic_ui_v0.GraphCluster
import com.tau.cryptic_ui_v0.GraphNode
import com.tau.cryptic_ui_v0.GraphEdge
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.notegraph.graph.normalized
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Level of Detail threshold
private const val LOD_ZOOM_THRESHOLD = 0.4f

@OptIn(ExperimentalGraphicsApi::class, ExperimentalTextApi::class, ExperimentalComposeUiApi::class)
@Composable
fun GraphView(viewModel: GraphViewmodel) {
    val nodes by viewModel.graphNodes.collectAsState()
    val macroEdges by viewModel.macroEdges.collectAsState()
    val microEdges by viewModel.microEdges.collectAsState()
    val clusters by viewModel.graphClusters.collectAsState()
    val transform by viewModel.transform.collectAsState()
    val showFabMenu by viewModel.showFabMenu.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()
    val physicsOptions by viewModel.physicsOptions.collectAsState()
    val primarySelectedItem by viewModel.metadataViewModel.primarySelectedItem.collectAsState()

    val textMeasurer = rememberTextMeasurer()

    val labelColor = MaterialTheme.colorScheme.onSurface
    val crosshairColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val selectionColor = MaterialTheme.colorScheme.primary
    val clusterLabelColor = MaterialTheme.colorScheme.onSurface

    var isDraggingNode by remember { mutableStateOf(false) } // Handles nodes OR clusters

    LaunchedEffect(Unit) {
        viewModel.runSimulationLoop()
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
                        viewModel.onTap(offset)
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
            // --- Draw Graph Elements (inside transform) ---
            withTransform({
                translate(left = center.x, top = center.y)
                scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                translate(left = transform.pan.x, top = transform.pan.y)
            }) {

                val isZoomedIn = transform.zoom >= LOD_ZOOM_THRESHOLD
                val isZoomedOut = !isZoomedIn

                // --- 1. Draw Cluster Hulls (BEFORE edges) ---
                drawClusterHulls(
                    clusters = clusters,
                    allNodes = nodes,
                    textMeasurer = textMeasurer,
                    zoom = transform.zoom,
                    isZoomedIn = isZoomedIn,
                    primarySelectedItem = primarySelectedItem,
                    labelColor = clusterLabelColor,
                    selectionColor = selectionColor
                )

                // --- 2. Draw Edges ---
                val edgeLabelStyle = TextStyle(
                    fontSize = (10.sp.value / transform.zoom.coerceAtLeast(0.1f)).coerceIn(8.sp.value, 14.sp.value).sp,
                    color = labelColor.copy(alpha = 0.8f)
                )

                // --- Create a combined map of all entities ---
                val allEntities: Map<Long, IGraphEntity> =
                    nodes.mapValues { it.value.asGraphEntity() } +
                            clusters.mapValues { it.value.asGraphEntity() }

                // --- 2a. Draw MACRO edges (always visible) ---
                val macroEdgesByPair = macroEdges.groupBy { Pair(it.sourceId, it.targetId) }
                val macroLinkCounts = mutableMapOf<Pair<Long, Long>, Int>()
                val macroUniquePairs = mutableSetOf<Pair<Long, Long>>()

                for (edge in macroEdges) {
                    if (edge.sourceId == edge.targetId) continue
                    val pair = if (edge.sourceId < edge.targetId) Pair(edge.sourceId, edge.targetId) else Pair(edge.targetId, edge.sourceId)
                    macroUniquePairs.add(pair)
                }

                for (pair in macroUniquePairs) {
                    val count = (macroEdgesByPair[pair]?.size ?: 0) + (macroEdgesByPair[Pair(pair.second, pair.first)]?.size ?: 0)
                    macroLinkCounts[pair] = count
                }

                val macroPairDrawIndex = mutableMapOf<Pair<Long, Long>, Int>()
                val selfLoopDrawIndex = mutableMapOf<Long, Int>() // Re-used for both

                for (edge in macroEdges) {
                    // --- Get entity from combined map ---
                    val entityA = allEntities[edge.sourceId]
                    val entityB = allEntities[edge.targetId]
                    if (entityA == null || entityB == null) continue

                    if (entityA.id == entityB.id) {
                        // Self-loop
                        val index = selfLoopDrawIndex.getOrPut(entityA.id) { 0 }
                        drawSelfLoop(entityA, edge, index, textMeasurer, edgeLabelStyle)
                        selfLoopDrawIndex[entityA.id] = index + 1
                    } else {
                        // Standard edge
                        val pair = Pair(entityA.id, entityB.id)
                        val undirectedPair = if (entityA.id < entityB.id) Pair(entityA.id, entityB.id) else Pair(entityB.id, entityA.id)

                        val total = macroLinkCounts[undirectedPair] ?: 1
                        val index = macroPairDrawIndex.getOrPut(pair) { 0 }

                        drawCurvedEdge(entityA, entityB, edge, index, total, textMeasurer, edgeLabelStyle)

                        macroPairDrawIndex[pair] = index + 1
                    }
                }

                // --- 2b. Draw MICRO edges (only when zoomed in) ---
                if (isZoomedIn) {
                    val microEdgesByPair = microEdges.groupBy { Pair(it.sourceId, it.targetId) }
                    val microLinkCounts = mutableMapOf<Pair<Long, Long>, Int>()
                    val microUniquePairs = mutableSetOf<Pair<Long, Long>>()

                    for (edge in microEdges) {
                        if (edge.sourceId == edge.targetId) continue
                        val pair = if (edge.sourceId < edge.targetId) Pair(edge.sourceId, edge.targetId) else Pair(edge.targetId, edge.sourceId)
                        microUniquePairs.add(pair)
                    }

                    for (pair in microUniquePairs) {
                        val count = (microEdgesByPair[pair]?.size ?: 0) + (microEdgesByPair[Pair(pair.second, pair.first)]?.size ?: 0)
                        microLinkCounts[pair] = count
                    }
                    val microPairDrawIndex = mutableMapOf<Pair<Long, Long>, Int>()

                    for (edge in microEdges) {
                        // Micro edges only connect nodes, so no cluster check needed
                        val nodeA = nodes[edge.sourceId]
                        val nodeB = nodes[edge.targetId]
                        if (nodeA == null || nodeB == null) continue

                        if (nodeA.id == nodeB.id) {
                            // Self-loop
                            val index = selfLoopDrawIndex.getOrPut(nodeA.id) { 0 }
                            drawSelfLoop(nodeA.asGraphEntity(), edge, index, textMeasurer, edgeLabelStyle)
                            selfLoopDrawIndex[nodeA.id] = index + 1
                        } else {
                            // Standard edge
                            val pair = Pair(nodeA.id, nodeB.id)
                            val undirectedPair = if (nodeA.id < nodeB.id) Pair(nodeA.id, nodeB.id) else Pair(nodeB.id, nodeA.id)

                            val total = microLinkCounts[undirectedPair] ?: 1
                            val index = microPairDrawIndex.getOrPut(pair) { 0 }

                            drawCurvedEdge(nodeA.asGraphEntity(), nodeB.asGraphEntity(), edge, index, total, textMeasurer, edgeLabelStyle)

                            microPairDrawIndex[pair] = index + 1
                        }
                    }
                }


                // --- 3. Draw Nodes ---
                drawNodes(
                    nodes = nodes,
                    textMeasurer = textMeasurer,
                    labelColor = labelColor,
                    selectionColor = selectionColor,
                    zoom = transform.zoom,
                    isZoomedIn = isZoomedIn,
                    viewModel = viewModel
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

        // --- Graph Settings Panel ---
        AnimatedVisibility(
            visible = showSettings,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            GraphSettingsView(
                options = physicsOptions,
                onGravityChange = viewModel::setGravity,
                onRepulsionChange = viewModel::setRepulsion,
                onSpringChange = viewModel::setSpring,
                onDampingChange = viewModel::setDamping,
                onBarnesHutThetaChange = viewModel::setBarnesHutTheta,
                onToleranceChange = viewModel::setTolerance,
                onInternalGravityChange = viewModel::setInternalGravity
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
            // Animated menu items
            AnimatedVisibility(visible = showFabMenu) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // --- Settings Button ---
                    SmallFloatingActionButton(
                        onClick = { viewModel.toggleSettings() },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Toggle Settings"
                        )
                    }
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
 * A shared interface for nodes and clusters,
 * allowing them to be treated interchangeably for edge drawing.
 */
private interface IGraphEntity {
    val id: Long
    val pos: Offset
    val radius: Float
}

private fun GraphNode.asGraphEntity() = object : IGraphEntity {
    override val id = this@asGraphEntity.id
    override val pos = this@asGraphEntity.pos
    override val radius = this@asGraphEntity.radius
}
private fun GraphCluster.asGraphEntity() = object : IGraphEntity {
    override val id = this@asGraphEntity.id
    override val pos = this@asGraphEntity.pos
    override val radius = this@asGraphEntity.radius
}

/**
 * Draws a self-referencing loop (edge from a node to itself).
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawSelfLoop(
    entity: IGraphEntity,
    edge: GraphEdge,
    index: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle
) {
    val color = edge.colorInfo.composeColor.copy(alpha = 0.7f)
    val strokeWidth = 2f
    val arrowSize = 6f

    val loopRadius = 25f + (index * 12f)
    val loopSeparation = entity.radius + 5f

    val startAngle = (45f).degToRad()
    val endAngle = (-30f).degToRad()

    val p1 = entity.pos + Offset(cos(startAngle) * entity.radius, sin(startAngle) * entity.radius)
    val p4 = entity.pos + Offset(cos(endAngle) * entity.radius, sin(endAngle) * entity.radius)

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

/**
 * Draws a directed edge between two different nodes.
 * Handles curving for multiple edges.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawCurvedEdge(
    from: IGraphEntity,
    to: IGraphEntity,
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
    if (delta == Offset.Zero) return

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

        // Adjust start/end points to touch node radius, pointing towards control point
        val startDelta = (controlPoint - from.pos)
        val endDelta = (controlPoint - to.pos)
        if (startDelta == Offset.Zero || endDelta == Offset.Zero) return

        val startWithRadius = from.pos + startDelta.normalized() * from.radius
        val endWithRadius = to.pos + endDelta.normalized() * to.radius

        val path = Path().apply {
            moveTo(startWithRadius.x, startWithRadius.y)
            quadraticTo(controlPoint.x, controlPoint.y, endWithRadius.x, endWithRadius.y)
        }
        drawPath(path, color, style = Stroke(strokeWidth))

        val tangent = (endWithRadius - controlPoint).normalized()
        if (tangent == Offset.Zero) return
        drawArrowhead(endWithRadius - (tangent * arrowSize * 2f), endWithRadius, color, arrowSize)

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

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNodes(
    nodes: Map<Long, GraphNode>,
    textMeasurer: TextMeasurer,
    labelColor: Color,
    selectionColor: Color,
    zoom: Float,
    isZoomedIn: Boolean,
    viewModel: GraphViewmodel
) {
    val minSize = 8.sp
    val maxSize = 14.sp
    val fontSize = ((12.sp.value / zoom.coerceAtLeast(0.1f)).coerceIn(minSize.value, maxSize.value)).sp
    val style = TextStyle(fontSize = fontSize, color = labelColor)

    val primaryId = (viewModel.metadataViewModel.primarySelectedItem.value as? NodeDisplayItem)?.id
    val secondaryId = (viewModel.metadataViewModel.secondarySelectedItem.value as? NodeDisplayItem)?.id

    for (node in nodes.values) {
        if (!isZoomedIn && node.clusterId != null) {
            continue // Don't draw clustered nodes when zoomed out
        }

        val isSelected = node.id == primaryId || node.id == secondaryId
        val alpha = if (isZoomedIn) 1.0f else 0.8f // Nodes are slightly faded when zoomed in with clusters

        drawCircle(
            color = node.colorInfo.composeColor,
            radius = node.radius,
            center = node.pos,
            alpha = alpha
        )
        drawCircle(
            color = if (isSelected) selectionColor else node.colorInfo.composeFontColor,
            radius = node.radius,
            center = node.pos,
            style = Stroke(width = if (isSelected) 3f else 1f),
            alpha = alpha
        )

        // Draw label (only if zoomed in enough)
        if (zoom > 0.5f) {
            val textLayoutResult = textMeasurer.measure(
                text = AnnotatedString(node.displayProperty),
                style = style.copy(color = labelColor.copy(alpha = alpha))
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

// --- Cluster Hull Drawing Function ---
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawClusterHulls(
    clusters: Map<Long, GraphCluster>,
    allNodes: Map<Long, GraphNode>,
    textMeasurer: TextMeasurer,
    zoom: Float,
    isZoomedIn: Boolean,
    primarySelectedItem: Any?,
    labelColor: Color,
    selectionColor: Color

) {
    val style = TextStyle(
        fontSize = (16.sp.value / zoom.coerceAtLeast(0.1f)).coerceIn(12.sp.value, 24.sp.value).sp,
        color = labelColor
    )

    // --- Get selected cluster from parameter ---
    val selectedClusterId = (primarySelectedItem as? ClusterDisplayItem)?.id


    for (cluster in clusters.values) {
        val microNodes = allNodes.values.filter { it.clusterId == cluster.id }
        val isSelected = cluster.id == selectedClusterId

        // --- Use !isZoomedIn ---
        val (color, alpha, strokeWidth) = if (isZoomedIn) {
            // Zoomed IN: Semi-transparent bubble
            Triple(cluster.colorInfo.composeColor, 0.2f, if (isSelected) 3f else 1.5f)
        } else {
            // Zoomed OUT: Opaque super-node
            Triple(cluster.colorInfo.composeColor, 1.0f, if (isSelected) 3f else 1.0f)
        }

        if (microNodes.size < 3 || !isZoomedIn) {
            // Draw a simple circle if < 3 nodes OR if zoomed out
            drawCircle(
                color = color,
                radius = cluster.radius,
                center = cluster.pos,
                alpha = alpha
            )
            drawCircle(
                color = if (isSelected) selectionColor else cluster.colorInfo.composeFontColor, // MODIFIED
                radius = cluster.radius,
                center = cluster.pos,
                style = Stroke(width = strokeWidth),
                alpha = if (isZoomedIn) 0.5f else 1.0f
            )
        } else {
            // Draw Convex Hull if zoomed IN and >= 3 nodes
            val points = microNodes.flatMap {
                val nodeCenter = it.pos
                listOf(
                    nodeCenter + Offset(it.radius, 0f),
                    nodeCenter + Offset(-it.radius, 0f),
                    nodeCenter + Offset(0f, it.radius),
                    nodeCenter + Offset(0f, -it.radius)
                )
            }
            val hullPoints = ConvexHull.compute(points)

            if (hullPoints.isNotEmpty()) {
                val path = Path()
                hullPoints.forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(point.x, point.y)
                    else path.lineTo(point.x, point.y)
                }
                path.close()

                drawPath(path, color, alpha = alpha)
                drawPath(
                    path,
                    if (isSelected) selectionColor else cluster.colorInfo.composeFontColor, // MODIFIED
                    alpha = 0.5f,
                    style = Stroke(width = strokeWidth)
                )
            }
        }

        // Draw cluster label
        val textLayoutResult = textMeasurer.measure(
            text = AnnotatedString(cluster.displayProperty),
            style = style.copy(color = style.color.copy(alpha = if (isZoomedIn) 0.7f else 1.0f))
        )
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = cluster.pos - Offset(
                x = textLayoutResult.size.width / 2f,
                y = textLayoutResult.size.height / 2f
            )
        )
    }
}

// --- Math Helpers ---

private fun Float.degToRad(): Float {
    return this * (PI.toFloat() / 180f)
}
