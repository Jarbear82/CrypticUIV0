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
import com.tau.cryptic_ui_v0.GraphNode
import com.tau.cryptic_ui_v0.GraphEdge
import com.tau.cryptic_ui_v0.NodeDisplayItem

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
                // 1. Draw Edges
                drawEdges(edges, nodes)

                // 2. Draw Nodes
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
    val fontSize = ((12.sp.value / zoom).coerceIn(minSize.value, maxSize.value)).sp
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
