package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
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
import androidx.compose.ui.text.drawText // FIXED: Correct import
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.tau.cryptic_ui_v0.GraphNode
import com.tau.cryptic_ui_v0.GraphEdge

@OptIn(ExperimentalGraphicsApi::class, ExperimentalTextApi::class, ExperimentalComposeUiApi::class) // ADDED: Opt-in for experimental APIs
@Composable
fun GraphView(viewModel: GraphViewmodel) {
    val nodes by viewModel.graphNodes.collectAsState()
    val edges by viewModel.graphEdges.collectAsState()
    val transform by viewModel.transform.collectAsState()
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val crosshairColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Pan
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    viewModel.onPan(dragAmount)
                }
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
            // The transform origin is the center of the canvas
            withTransform({
                translate(left = center.x, top = center.y)
                scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
                translate(left = transform.pan.x, top = transform.pan.y)
            }) {
                // 1. Draw Edges
                drawEdges(edges, nodes)

                // 2. Draw Nodes
                drawNodes(nodes, textMeasurer, labelColor, transform.zoom)
            }

            // --- Draw UI Elements (outside transform) ---
            // Draw crosshair at the visual center
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
    }
}

private fun DrawScope.drawEdges(edges: List<GraphEdge>, nodes: Map<Long, GraphNode>) {
    for (edge in edges) {
        val nodeA = nodes[edge.sourceId]
        val nodeB = nodes[edge.targetId]

        if (nodeA != null && nodeB != null) {
            drawLine(
                color = edge.colorInfo.composeColor.copy(alpha = 0.5f),
                start = nodeA.pos,
                end = nodeB.pos,
                strokeWidth = 2f
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class) // ADDED: Opt-in for TextMeasurer APIs
private fun DrawScope.drawNodes(
    nodes: Map<Long, GraphNode>,
    textMeasurer: TextMeasurer,
    labelColor: Color,
    zoom: Float
) {
    // Scale font size based on zoom, but clamp it
    // FIXED: Correctly divide float by float, create TextUnit, then coerce
    val fontSize = (12.sp / zoom).coerceIn<TextUnit>(8.sp..14.sp)
    val style = TextStyle(fontSize = fontSize, color = labelColor)

    for (node in nodes.values) {
        // Draw circle
        drawCircle(
            color = node.colorInfo.composeColor,
            radius = node.radius,
            center = node.pos
        )
        // Draw border
        drawCircle(
            color = node.colorInfo.composeFontColor,
            radius = node.radius,
            center = node.pos,
            style = Stroke(width = 1f)
        )

        // Draw label (only if zoom is not too far out)
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

