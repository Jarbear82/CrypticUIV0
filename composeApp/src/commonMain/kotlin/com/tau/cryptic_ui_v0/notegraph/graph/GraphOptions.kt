package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.cryptic_ui_v0.notegraph.graph.physics.BarnesHutOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.ForceAtlas2BasedOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.HierarchicalRepulsionOptions

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

/**
 * Selects which repulsion solver to use in the PhysicsEngine.
 */
enum class SolverType {
    REPEL,
    BARNES_HUT,
    FORCE_ATLAS_2,
    HIERARCHICAL
}

/**
 * Options for the PhysicsEngine.
 */
data class PhysicsOptions(
    val solver: SolverType = SolverType.FORCE_ATLAS_2,

    // General physics properties
    val damping: Float = 0.4f,
    val timeStep: Float = 0.5f,
    val minVelocity: Float = 1.0f,
    val centralGravity: Float = 0.01f,
    val maxVelocity: Float = 50f,

    // Spring properties
    val selfReferenceSpringLength: Float = 100f,
    val defaultSpringLength: Float = 400f,
    val defaultSpringConstant: Float = 0.02f,

    val nodeRadius: Float? = null,

    val repulsionConstant: Float = -1000f,

    // Options for the advanced solvers
    val barnesHut: BarnesHutOptions = BarnesHutOptions(
        gravitationalConstant = -10000.0,
        avoidOverlap = 1.0
    ),
    val forceAtlas: ForceAtlas2BasedOptions = ForceAtlas2BasedOptions(
        gravitationalConstant = -150.0, // (This value will be used now)
        avoidOverlap = 1.0
    ),
    val hierarchicalRepulsion: HierarchicalRepulsionOptions = HierarchicalRepulsionOptions()
)

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
    val runPhysicsAfter: Boolean = true
)

/**
 * Top-level options for the LayoutEngine.
 */
data class LayoutOptions(
    val hierarchical: HierarchicalOptions = HierarchicalOptions(),
    /**
     * On initial load (when hierarchical is false), run this many
     * simulation steps synchronously before displaying the graph.
     * This provides a better initial layout than pure random.
     * Set to 0 to disable.
     */
    val preRunSteps: Int = 150 // A reasonable default
)