package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange

// Options are now in GraphOptions.kt

class GraphInteractionHandler(
    private val nodePositionsProvider: () -> Map<String, Offset>, // Lambda to get current positions
    private val nodeRadiusProvider: () -> Float,               // Lambda to get node radius
    private val viewOffsetProvider: () -> Offset,               // Lambda to get view offset
    private val viewScaleProvider: () -> Float,                 // Lambda to get view scale
    private val physicsEngineNotifier: (String, Offset, Boolean) -> Unit, // Lambda to notify physics engine
    private val options: InteractionOptions = InteractionOptions() // Use options class
) {
    // This function is now *specifically* for node dragging.
    // Panning, zooming, and tapping will be handled by other detectors in GraphView.
    suspend fun handleNodeDragGestures(pointerInputScope: PointerInputScope, onDragStart: (String) -> Unit, onDragEnd: () -> Unit) {
        pointerInputScope.awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false) // Consume the down event

            // Check for node drag start
            val downPosition = down.position
            val viewOffset = viewOffsetProvider()
            val viewScale = viewScaleProvider()
            val canvasDownPosition = (downPosition - viewOffset) / viewScale
            val nodeId = findNodeAt(canvasDownPosition, nodePositionsProvider(), nodeRadiusProvider())

            if (nodeId != null && options.dragNodes) {
                onDragStart(nodeId) // Notify GraphView that a drag has started
                // Notify physics engine that drag has started
                physicsEngineNotifier(nodeId, canvasDownPosition, true)

                // Node Dragging Logic
                drag(down.id) { change ->
                    val currentPositions = nodePositionsProvider()
                    val currentPhysicsPos = currentPositions[nodeId] ?: canvasDownPosition // Fallback
                    val dragAmountCanvas = change.positionChange() / viewScale
                    val newPhysicsPos = currentPhysicsPos + dragAmountCanvas

                    // Notify physics engine about the new position during drag
                    physicsEngineNotifier(nodeId, newPhysicsPos, true)
                    change.consume()
                }

                // Drag finished or cancelled
                val finalPositions = nodePositionsProvider()
                val finalPhysicsPos = finalPositions[nodeId] ?: canvasDownPosition // Use last known pos
                // Notify physics engine that drag has ended
                physicsEngineNotifier(nodeId, finalPhysicsPos, false)
                onDragEnd() // Notify GraphView that drag has ended
            }
        }
    }

    // Find node function (public for use by tap gestures)
    fun findNodeAt(
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
                if (distSq < minDistSq) { // Prioritize closer nodes
                    minDistSq = distSq
                    closestNode = id
                } else if (closestNode == null) { // Handle exact overlap
                    closestNode = id
                }
            }
        }
        return closestNode
    }
}