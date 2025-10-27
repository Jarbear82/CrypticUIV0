package com.tau.cryptic_ui_v0.notegraph.graph.physics

// --- Helper Data Structures (from user prompt) ---
data class Point(var x: Double, var y: Double)
data class Range(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)
data class BranchChildren(
    var NW: Branch? = null,
    var NE: Branch? = null,
    var SW: Branch? = null,
    var SE: Branch? = null,
    var data: Node? = null // Used when childrenCount is 1
)

data class Branch(
    var centerOfMass: Point = Point(0.0, 0.0),
    var mass: Double = 0.0,
    var range: Range,
    var size: Double,
    var calcSize: Double, // 1 / size
    var children: BranchChildren = BranchChildren(),
    var maxWidth: Double = 0.0,
    var level: Int = 0,
    var childrenCount: Int = 0 // 0, 1 (data), or 4 (child branches)
)

data class BarnesHutTree(var root: Branch)

// --- Interfaces for Physics Solvers ---
interface Body {
    val nodes: Map<String, Node>
}

interface PhysicsBody {
    val physicsNodeIndices: List<String>
    val forces: MutableMap<String, Point>
}

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
    val radius: Double? = null,
    val width: Double? = null,
    val height: Double? = null
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
    val edges: List<Any> = emptyList() // For ForceAtlas2 degree calculation
)