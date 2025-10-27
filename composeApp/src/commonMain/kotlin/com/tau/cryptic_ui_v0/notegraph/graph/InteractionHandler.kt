package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.sqrt

// Options for interaction
data class InteractionOptions(
    val dragNodes: Boolean = true,
    val dragView: Boolean = true,
    val zoomView: Boolean = true,
    // Add other interaction options here (hover, selection etc.)
)

class GraphInteractionHandler(
    private val nodePositionsProvider: () -> Map<String, Offset>, // Lambda to get current positions
    private val nodeRadiusProvider: () -> Float,               // Lambda to get node radius
    private val viewOffsetProvider: () -> Offset,               // Lambda to get view offset
    private val viewScaleProvider: () -> Float,                 // Lambda to get view scale
    private val updateViewOffset: (Offset) -> Unit,             // Lambda to update view offset
    private val updateViewScale: (Float, Offset) -> Unit,       // Lambda to update view scale (with pivot)
    private val physicsEngineNotifier: (String, Offset, Boolean) -> Unit, // Lambda to notify physics engine
    private val options: InteractionOptions = InteractionOptions() // Use options class
) {
    private var draggedNodeId: String? = null
    private var dragStartOffset: Offset = Offset.Zero // For panning

    // Function to be used with pointerInput modifier
    suspend fun handleGestures(pointerInputScope: PointerInputScope) {
        pointerInputScope.awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false) // Consume the down event

            // Check for node drag start
            val downPosition = down.position
            val viewOffset = viewOffsetProvider()
            val viewScale = viewScaleProvider()
            val canvasDownPosition = (downPosition - viewOffset) / viewScale
            val nodeId = findNodeAt(canvasDownPosition, nodePositionsProvider(), nodeRadiusProvider())

            if (nodeId != null && options.dragNodes) {
                draggedNodeId = nodeId
                // Notify physics engine that drag has started
                physicsEngineNotifier(nodeId, canvasDownPosition, true)

                // Node Dragging Logic
                drag(down.id) { change ->
                    if (draggedNodeId != null) {
                        val currentPositions = nodePositionsProvider()
                        val currentPhysicsPos = currentPositions[draggedNodeId!!] ?: canvasDownPosition // Fallback
                        val dragAmountCanvas = change.positionChange() / viewScale
                        val newPhysicsPos = currentPhysicsPos + dragAmountCanvas

                        // Notify physics engine about the new position during drag
                        physicsEngineNotifier(draggedNodeId!!, newPhysicsPos, true)

                        if (change.pressed != change.previousPressed) {
                            change.consume() // Consume final up if necessary
                        } else {
                            change.consume()
                        }
                    }
                }

                // Drag finished or cancelled
                if (draggedNodeId != null) {
                    val finalPositions = nodePositionsProvider()
                    val finalPhysicsPos = finalPositions[draggedNodeId!!] ?: canvasDownPosition // Use last known pos
                    // Notify physics engine that drag has ended
                    physicsEngineNotifier(draggedNodeId!!, finalPhysicsPos, false)
                    draggedNodeId = null
                }

            } else if (options.dragView) {
                // View Panning Logic
                dragStartOffset = downPosition - viewOffset // Store initial offset for smooth panning

                drag(down.id) { change ->
                    val newOffset = change.position - dragStartOffset
                    updateViewOffset(newOffset)
                    if (change.pressed != change.previousPressed) {
                        change.consume()
                    } else {
                        change.consume()
                    }
                }
                // Panning ends implicitly when drag scope finishes
            }
            // Add zoom/pinch handling here using detectTransformGestures if needed
            // Note: detectTransformGestures might need to be outside detectDragGestures
            // or handled carefully to avoid conflicts.
        }
    }

    // Find node function (can remain here or move to DrawingUtils)
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
                // If multiple nodes overlap, this might need refinement (e.g., check z-index if available)
                if (distSq < minDistSq) { // Prioritize closer nodes if overlapping exactly at border
                    minDistSq = distSq
                    closestNode = id
                } else if (closestNode == null) { // Handle exact overlap case
                    closestNode = id
                }
            }
        }
        return closestNode
    }
}