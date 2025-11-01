package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// --- ADDED IMPORTS ---
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.SolverType
import com.tau.cryptic_ui_v0.notegraph.graph.physics.BarnesHutOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.ForceAtlas2Options
import com.tau.cryptic_ui_v0.notegraph.graph.physics.HierarchicalRepulsionOptions
// ---

// --- Default values ---
val DEFAULT_NODE_RADIUS_DP = 25.dp
val DEFAULT_EDGE_ARROW_LENGTH = 15f
val DEFAULT_EDGE_ARROW_ANGLE_RAD = (Math.PI / 6).toFloat() // 30 degrees
val SELF_LOOP_RADIUS: Float = 20.0f
val SELF_LOOP_OFFSET_ANGLE: Float = (Math.PI / 4).toFloat()

// --- Style Options ---
data class NodeStyleOptions(
    val radiusDp: Dp = DEFAULT_NODE_RADIUS_DP,
    val draggedBorderColor: Color = Color.Red,
    val draggedStrokeWidthMultiplier: Float = 2f,
    val defaultStrokeWidthDp: Dp = 1.dp,
    val labelColor: Color = Color.Black,
    val labelFontSizeSp: Float = 14f
)

data class EdgeStyleOptions(
    val arrowLength: Float = DEFAULT_EDGE_ARROW_LENGTH,
    val arrowAngleRad: Float = DEFAULT_EDGE_ARROW_ANGLE_RAD,
    val defaultStrokeWidthDp: Dp = 0.5.dp,
    val selfLoopRadius: Float = SELF_LOOP_RADIUS,
    val selfLoopOffsetAngleRad: Float = SELF_LOOP_OFFSET_ANGLE,
    val labelColor: Color = Color.Black,
    val labelFontSizeSp: Float = 12f
)

// --- Interaction / UI Options ---
data class SelectionStyleOptions(
    val nodeSelectedBorderColor: Color = Color.Blue,
    val nodeSelectedStrokeWidthMultiplier: Float = 2.5f,
    val edgeSelectedColor: Color = Color.Blue,
    val edgeSelectedStrokeWidthMultiplier: Float = 2.0f
)

data class TooltipOptions(
    val backgroundColor: Color = Color.DarkGray.copy(alpha = 0.8f),
    val textColor: Color = Color.White,
    val fontSizeSp: TextUnit = 12.sp,
    val padding: Dp = 8.dp
)

data class NavigationOptions(
    val showNavigationUI: Boolean = true,
    val showZoomButtons: Boolean = true,
    val showPanButtons: Boolean = false,
    val showFitButton: Boolean = true
)

data class InteractionOptions(
    val dragNodes: Boolean = true,
    val dragView: Boolean = true,
    val zoomView: Boolean = true,
    val selectionEnabled: Boolean = true,
    val multiSelectEnabled: Boolean = true,
    val tooltipsEnabled: Boolean = true,
    val keyboardNavigationEnabled: Boolean = true
)

// --- Layout and Physics Options ---

// --- REMOVED: SolverType, PhysicsOptions, BarnesHutOptions, ForceAtlas2BasedOptions, HierarchicalRepulsionOptions ---
// --- They are now imported from ...graph.physics ---

/**
 * Defines the direction for hierarchical layout.
 */
enum class HierarchicalDirection {
    UD, // Up-Down
    DU, // Down-Up
    LR, // Left-Right
    RL  // Right-Left
}

/**
 * Options for the static hierarchical layout.
 * (Defaults already match vis.js)
 */
data class HierarchicalOptions(
    val enabled: Boolean = false,
    val levelSeparation: Float = 150f,
    val nodeSeparation: Float = 100f,
    val direction: HierarchicalDirection = HierarchicalDirection.UD,
    val runPhysicsAfter: Boolean = true,
    // --- ADDED: Options from vis-network.js ---
    val parentCentralization: Boolean = true,
    val blockShifting: Boolean = true,
    val edgeMinimization: Boolean = true,
    val sortMethod: String = "directed" // "hubsize" or "directed"
)

/**
 * Top-level options for the LayoutEngine.
 */
data class LayoutOptions(
    val hierarchical: HierarchicalOptions = HierarchicalOptions(),

    /**
     * --- ADDED: Flag to use KamadaKawai or similar improved layout ---
     * If true, and hierarchical is false, the engine will try to
     * run an improved layout algorithm before starting physics.
     * Note: The provided KamadaKawai.kt is from an incompatible project,
     * so this currently falls back to preRunSteps.
     */
    val improvedLayout: Boolean = true,

    /**
     * On initial load (when hierarchical is false), run this many
     * simulation steps synchronously before displaying the graph.
     * This provides a better initial layout than pure random.
     * Set to 0 to disable.
     */
    val preRunSteps: Int = 150 // A reasonable default
)
