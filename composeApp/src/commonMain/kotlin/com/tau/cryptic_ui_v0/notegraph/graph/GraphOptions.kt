package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Default values (could also be constants in DrawingUtils)
val DEFAULT_NODE_RADIUS_DP = 25.dp
val DEFAULT_EDGE_ARROW_LENGTH = 15f
val DEFAULT_EDGE_ARROW_ANGLE_RAD = (Math.PI / 6).toFloat() // 30 degrees
val SELF_LOOP_RADIUS: Float = 20.0f
val SELF_LOOP_OFFSET_ANGLE: Float = (Math.PI / 6).toFloat()

data class NodeStyleOptions(
    val radiusDp: Dp = DEFAULT_NODE_RADIUS_DP,
    val draggedBorderColor: Color = Color.Red,
    val draggedStrokeWidthMultiplier: Float = 2f, // Multiplier for stroke width when dragged
    val defaultStrokeWidthDp: Dp = 2.dp,
    val labelColor: Color = Color.Black, // Default label color (can be overridden by theme/data)
    val labelFontSizeSp: Float = 12f // Default font size
)

data class EdgeStyleOptions(
    val arrowLength: Float = DEFAULT_EDGE_ARROW_LENGTH,
    val arrowAngleRad: Float = DEFAULT_EDGE_ARROW_ANGLE_RAD,
    val defaultStrokeWidthDp: Dp = 2.dp,
    val selfLoopRadius: Float = SELF_LOOP_RADIUS,
    val selfLoopOffsetAngleRad: Float = SELF_LOOP_OFFSET_ANGLE,
    val labelColor: Color = Color.Black, // Default label color
    val labelFontSizeSp: Float = 12f // Default font size
)

// InteractionOptions is already defined in InteractionHandler.kt

// PhysicsOptions is already defined in PhysicsEngine.kt