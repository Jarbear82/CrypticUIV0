package com.tau.cryptic_ui_v0.notegraph.graph.physics

// --- Helper Data Structures (from user prompt) ---
data class Point(var x: Double, var y: Double)
data class Range(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)

// --- UPDTED: Simplified BranchChildren representation ---
sealed class BranchChildren {
    data class SubBranches(
        var NW: Branch,
        var NE: Branch,
        var SW: Branch,
        var SE: Branch
    ) : BranchChildren()

    data class Data(var node: Node?) : BranchChildren()
}


data class Branch(
    var centerOfMass: Point = Point(0.0, 0.0),
    var mass: Double = 0.0,
    var range: Range,
    var size: Double,
    var calcSize: Double = 1.0 / size, // 1 / size
    var children: BranchChildren = BranchChildren.Data(null),
    var maxWidth: Double = 0.0,
    var level: Int = 0,
    var childrenCount: Int = 0 // 0, 1 (data), or 4 (child branches)
)

data class BarnesHutTree(var root: Branch)

// --- Interfaces for Physics Solvers ---
/**
 * Represents the current state of the physics simulation.
 */
sealed interface SimulationStatus {
    /** The simulation is not running and node positions are stable. */
    data object Stable : SimulationStatus

    /** The simulation is actively calculating new positions. */
    data object Running : SimulationStatus

    /** The simulation is running, but velocity is low; checking for stability. */
    data class Stabilizing(val progress: Float) : SimulationStatus // Progress 0.0 to 1.0
}

interface Body {
    val nodes: Map<String, Node>
    // --- ADDED: Edges property for SpringSolvers ---
    val edges: Map<String, Edge>
}

interface PhysicsBody {
    val physicsNodeIndices: List<String>
    // --- ADDED: Edge indices property for SpringSolvers ---
    val physicsEdgeIndices: List<String>
    val forces: MutableMap<String, Point>
}

data class PhysicsNode(
    val id: String,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var isFixed: Boolean = false,
    val mass: Float = 1f,
    var level: Int = 0
)

data class PhysicsEdge(
    val id: String,
    val from: String,
    val to: String,
    val springLength: Float = 150f,
    val springConstant: Float = 0.04f
)

// --- Data classes for Solvers ---
data class NodeOptions(
    var mass: Double,
    val fixed: FixedOptions
)

data class FixedOptions(
    var x: Boolean,
    var y: Boolean
)

data class Shape(
    val radius: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0
)

/**
 * This is the unified Node data class that the solvers will use.
 * The PhysicsEngine will be responsible for creating these.
 */
data class Node(
    val id: String,
    var x: Double,
    var y: Double,
    val options: NodeOptions,
    val shape: Shape,
    val level: Int = 0,       // For HierarchicalRepulsionSolver
    val edges: List<Edge> = emptyList() // For ForceAtlas2 degree calculation
)

/**
 * --- ADDED: Stub for Edge data class required by SpringSolvers ---
 */
data class Edge(
    val id: String,
    val fromId: String,
    val toId: String,
    val options: EdgeOptions,
    // --- ADDED: Stubs for data solvers expect ---
    val from: Node,
    val to: Node,
    val connected: Boolean,
    val edgeType: EdgeType
)

data class EdgeOptions(
    val length: Double?
)

data class EdgeType(
    val via: Node?
)

data class EnergyNodeInfo(val id: String, val energy: Double, val dx: Double, val dy: Double)