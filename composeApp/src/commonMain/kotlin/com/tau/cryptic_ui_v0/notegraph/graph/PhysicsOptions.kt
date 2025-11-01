package com.tau.cryptic_ui_v0.notegraph.graph.physics

// --- UPDATED: Removed solver instances ---
// import com.tau.kt_vis_network.network.physics.solvers.CentralGravitySolver
// import com.tau.kt_vis_network.network.physics.solvers.SpringSolver
// ---

// Re-defined here, assuming this is the central options/config file
enum class SolverType {
    REPEL,
    BARNES_HUT,
    FORCE_ATLAS_2,
    HIERARCHICAL
}

/**
 * --- NEW: Common interface for all solver options ---
 * This allows solvers to generically accept an options object
 * and access the properties they need.
 */
interface SolverOptions {
    val centralGravity: Double
    val springLength: Double
    val springConstant: Double
    val damping: Double
    val avoidOverlap: Double
    val nodeDistance: Double
}

data class PhysicsOptions(
    // --- Solver Selection ---
    val solver: SolverType = SolverType.BARNES_HUT,
    // --- UPDATED: Removed hardcoded solver instances ---
    // val springSolver: SpringSolver = SpringSolver(),
    // val gravitySolver: GravitySolver = CentralGravitySolver(),
    // ---

    // --- General Physics ---
    val timeStep: Float = 0.5f,
    val damping: Float = 0.09f,
    val centralGravity: Float = 0.01f,
    val repulsionConstant: Float = -1000f,
    val defaultSpringConstant: Float = 0.04f,
    val defaultSpringLength: Float = 150f,
    val selfReferenceSpringLength: Float = 100f,

    // --- Simulation Control ---
    val maxVelocity: Float = 50f,
    val minVelocity: Float = 0.1f,

    // --- Adaptive Timestep (NEW) ---
    val adaptiveTimeStep: Boolean = true,
    val adaptiveTimeStepScaling: Float = 1.0f,
    val minTimeStep: Float = 0.01f,
    val maxTimeStep: Float = 0.5f, // Note: timeStep property is used as max if adaptive

    // --- Robust Stabilization (NEW) ---
    /** Number of simulation frames velocity must be below minVelocity to be considered stable. */
    val stabilizationIterations: Int = 15,

    // --- Solver-Specific Options ---
    val barnesHut: BarnesHutOptions = BarnesHutOptions(),
    val forceAtlas: ForceAtlas2Options = ForceAtlas2Options(),
    val hierarchicalRepulsion: HierarchicalRepulsionOptions = HierarchicalRepulsionOptions(),
    // --- NEW: Added RepulsionOptions ---
    val repulsion: RepulsionOptions = RepulsionOptions(),

    // --- ADDED ---
    /** The radius of the node, used for solver calculations. */
    val nodeRadius: Float? = null
)

// --- STUBS for solver options ---
// (Assuming these are defined elsewhere, providing stubs to compile)

/**
 * --- UPDATED: Implements SolverOptions ---
 */
data class BarnesHutOptions(
    val theta: Double = 0.5,
    val gravitationalConstant: Double = -2000.0,
    override val centralGravity: Double = 0.3,
    override val springLength: Double = 95.0,
    override val springConstant: Double = 0.04,
    override val damping: Double = 0.09,
    override val avoidOverlap: Double = 0.0,
    // --- ADDED: Fulfills SolverOptions interface ---
    override val nodeDistance: Double = springLength // Default to springLength
) : SolverOptions

/**
 * --- UPDATED: Implements SolverOptions and includes fields from BarnesHut ---
 * This is necessary because ForceAtlas2BasedRepulsionSolver inherits from BarnesHutSolver.
 */
data class ForceAtlas2Options(
    val scaling: Double = 1.0,
    val strongGravity: Boolean = false,
    val gravity: Double = 1.0, // Note: This is specific to FA2, solvers use centralGravity
    // --- ADDED: Fields from BarnesHutOptions for compatibility ---
    val theta: Double = 0.5,
    val gravitationalConstant: Double = -2000.0,
    override val centralGravity: Double = 0.3,
    override val springLength: Double = 95.0,
    override val springConstant: Double = 0.04,
    override val damping: Double = 0.09,
    override val avoidOverlap: Double = 0.0,
    // --- ADDED: Fulfills SolverOptions interface ---
    override val nodeDistance: Double = springLength // Default to springLength
) : SolverOptions

/**
 * --- UPDATED: Implements SolverOptions ---
 */
data class HierarchicalRepulsionOptions(
    override val nodeDistance: Double = 120.0,
    override val damping: Double = 0.09,
    // --- ADDED: Fulfills SolverOptions interface ---
    override val centralGravity: Double = 0.3,
    override val springLength: Double = 100.0, // Default value
    override val springConstant: Double = 0.04, // Default value
    override val avoidOverlap: Double = 0.0 // Default value
) : SolverOptions

/**
 * --- NEW: Options for the basic Repulsion solver ---
 */
data class RepulsionOptions(
    override val nodeDistance: Double = 100.0,
    // --- ADDED: Fulfills SolverOptions interface ---
    override val centralGravity: Double = 0.3,
    override val springLength: Double = 95.0,
    override val springConstant: Double = 0.04,
    override val damping: Double = 0.09,
    override val avoidOverlap: Double = 0.0
) : SolverOptions
