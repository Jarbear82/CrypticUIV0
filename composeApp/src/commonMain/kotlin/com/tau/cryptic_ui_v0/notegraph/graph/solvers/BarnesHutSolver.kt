package com.tau.kt_vis_network.network.physics.solvers

import kotlin.random.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Debug Imports
import com.tau.cryptic_ui_v0.notegraph.graph.physics.BarnesHutOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Body
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Branch
import com.tau.cryptic_ui_v0.notegraph.graph.physics.BranchChildren
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Node
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsBody
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Range

/**
 * Barnes Hut Solver
 *
 * This class calculates the forces the nodes apply on each other based on a gravitational model.
 * The Barnes-Hut method is used to speed up this N-body simulation.
 *
 * @param body The main graph body, containing all nodes.
 * @param physicsBody The physics state, including node indices and force map.
 * @param options Configuration for the solver.
 */
open class BarnesHutSolver(
    protected val body: Body,
    protected val physicsBody: PhysicsBody,
    options: BarnesHutOptions
) {

    protected lateinit var options: BarnesHutOptions
        protected get
        private set
    private var thetaInversed: Double = 0.0
    protected var overlapAvoidanceFactor: Double = 0.0

    /**
     * Seeded random number generator, replacing `Alea.js`.
     * The seed is derived from the string's hashcode for deterministic behavior.
     */
    private val _rng: Random = Random("BARNES HUT SOLVER".hashCode())

    /**
     * The root of the generated Barnes-Hut tree.
     * Publicly readable for debugging, but only settable by the solver.
     */
    var barnesHutTree: Branch? = null
        private set

    companion object {
        /**
         * The minimum size a tree quad can be.
         */
        private const val MINIMUM_TREE_SIZE = 1e-5
    }

    init {
        setOptions(options)
        // The JS debug emitter `this.body.emitter.on("afterDrawing", ...)` is omitted.
        // You can add a Compose-specific debug draw function if needed.
    }

    /**
     * Updates the solver's options.
     * @param options New options to apply.
     */
    fun setOptions(options: BarnesHutOptions) {
        this.options = options
        this.thetaInversed = 1.0 / this.options.theta
        this.overlapAvoidanceFactor = 1.0 - max(0.0, min(1.0, this.options.avoidOverlap))
    }

    /**
     * This function calculates the forces the nodes apply on each other.
     */
    fun solve() {
        if (options.gravitationalConstant != 0.0 && physicsBody.physicsNodeIndices.isNotEmpty()) {
            val nodes = this.body.nodes
            val nodeIds = this.physicsBody.physicsNodeIndices
            val nodeCount = nodeIds.size

            // Create the tree
            val treeRoot = _formBarnesHutTree(nodes, nodeIds)
            this.barnesHutTree = treeRoot // for debugging

            // Calculate forces for each node
            for (i in 0 until nodeCount) {
                val node = nodes[nodeIds[i]]
                if (node != null && node.options.mass > 0) {
                    // Starting with root is irrelevant, it never passes the BarnesHutSolver condition
                    _getForceContributions(treeRoot, node)
                }
            }
        }
    }

    /**
     * Helper function to recursively get forces from all 4 sub-quadrants.
     * This is only called on a branch with 4 children.
     */
    private fun _getForceContributions(parentBranch: Branch, node: Node) {
        // We know this is a SubBranches node because it's only called
        // by _getForceContribution when childrenCount == 4.
        val children = parentBranch.children as BranchChildren.SubBranches
        _getForceContribution(children.NW, node)
        _getForceContribution(children.NE, node)
        _getForceContribution(children.SW, node)
        _getForceContribution(children.SE, node)
    }

    /**
     * This function traverses the barnesHutTree. It checks when it can approximate
     * distant nodes with their center of mass.
     * If a region contains a single node, we check if it is not itself, then we apply the force.
     */
    private fun _getForceContribution(parentBranch: Branch, node: Node) {
        // We get no force contribution from an empty region
        if (parentBranch.childrenCount > 0) {
            // Get the distance from the center of mass to the node.
            val dx = parentBranch.centerOfMass.x - node.x
            val dy = parentBranch.centerOfMass.y - node.y
            val distance = sqrt(dx * dx + dy * dy)

            // BarnesHutSolver condition: s/d < theta  ===  d/s > 1/theta
            if (distance * parentBranch.calcSize > this.thetaInversed) {
                _calculateForces(distance, dx, dy, node, parentBranch)
            } else {
                // Did not pass the condition, go into children if available
                if (parentBranch.childrenCount == 4) {
                    _getForceContributions(parentBranch, node)
                } else {
                    // parentBranch must have only one node (childrenCount == 1)
                    val childNode = (parentBranch.children as BranchChildren.Data).node
                    if (childNode != null && childNode.id != node.id) {
                        // if it is not self
                        _calculateForces(distance, dx, dy, node, parentBranch)
                    }
                }
            }
        }
    }

    /**
     * Calculate the forces based on the distance.
     */
    protected open fun _calculateForces(
        distanceIn: Double,
        dxIn: Double,
        dyIn: Double,
        node: Node,
        parentBranch: Branch
    ) {
        var distance = distanceIn
        var dx = dxIn
        val dy = dyIn // dy is never reassigned in the original JS

        if (distance == 0.0) {
            distance = 0.1
            dx = distance // Replicate JS behavior to apply a small push
        }

        // Apply overlap avoidance
        if (this.overlapAvoidanceFactor < 1.0 && node.shape.radius > 0.0) {
            distance = max(
                0.1 + this.overlapAvoidanceFactor * node.shape.radius,
                distance - node.shape.radius
            )
        }

        // The dividing by the distance cubed instead of squared allows us to get the
        // fx and fy components without sines and cosines.
        val gravityForce = (options.gravitationalConstant *
                parentBranch.mass *
                node.options.mass) / distance.pow(3)

        val fx = dx * gravityForce
        val fy = dy * gravityForce

        // Safely update the forces map
        physicsBody.forces[node.id]?.let {
            it.x += fx
            it.y += fy
        }
    }

    /**
     * This function constructs the barnesHut tree recursively.
     * @param nodes A map of all nodes.
     * @param nodeIds A list of node IDs to include in the tree.
     * @return The root [Branch] of the tree.
     */
    private fun _formBarnesHutTree(
        nodes: Map<String, Node>,
        nodeIds: List<String>
    ): Branch {
        val nodeCount = nodeIds.size
        // We assume nodeIds is not empty based on the check in solve()
        val firstNode = nodes[nodeIds[0]]!!

        var minX = firstNode.x
        var minY = firstNode.y
        var maxX = firstNode.x
        var maxY = firstNode.y

        // Get the range of the nodes
        for (i in 1 until nodeCount) {
            val node = nodes[nodeIds[i]]
            if (node != null && node.options.mass > 0) {
                val x = node.x
                val y = node.y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }

        // Make the range a square
        val sizeDiff = abs(maxX - minX) - abs(maxY - minY)
        if (sizeDiff > 0) { // xSize > ySize
            minY -= 0.5 * sizeDiff
            maxY += 0.5 * sizeDiff
        } else { // xSize < ySize
            minX += 0.5 * sizeDiff
            maxX -= 0.5 * sizeDiff
        }

        val rootSize = max(MINIMUM_TREE_SIZE, abs(maxX - minX))
        val halfRootSize = 0.5 * rootSize
        val centerX = 0.5 * (minX + maxX)
        val centerY = 0.5 * (minY + maxY)

        val rootRange = Range(
            minX = centerX - halfRootSize,
            maxX = centerX + halfRootSize,
            minY = centerY - halfRootSize,
            maxY = centerY + halfRootSize
        )

        // Construct the barnesHutTree root
        // The JS logic initializes with childrenCount 4 and then immediately splits.
        val barnesHutTree = Branch(
            range = rootRange,
            size = rootSize,
            level = 0,
            children = BranchChildren.Data(null), // Dummy, will be replaced by _splitBranch
            childrenCount = 4 // This tells _splitBranch it's not a leaf
        )

        this._splitBranch(barnesHutTree) // Create the 4 quadrants

        // Place the nodes one by one recursively
        for (i in 0 until nodeCount) {
            val node = nodes[nodeIds[i]]
            if (node != null && node.options.mass > 0) {
                this._placeInTree(barnesHutTree, node)
            }
        }

        return barnesHutTree
    }

    /**
     * Updates the mass and center of mass of a branch.
     */
    private fun _updateBranchMass(parentBranch: Branch, node: Node) {
        val centerOfMass = parentBranch.centerOfMass
        val totalMass = parentBranch.mass + node.options.mass

        if (totalMass == 0.0) {
            // Avoid division by zero if mass is 0 (though it shouldn't be here)
            return
        }
        val totalMassInv = 1.0 / totalMass

        centerOfMass.x = centerOfMass.x * parentBranch.mass + node.x * node.options.mass
        centerOfMass.x *= totalMassInv
        centerOfMass.y = centerOfMass.y * parentBranch.mass + node.y * node.options.mass
        centerOfMass.y *= totalMassInv

        parentBranch.mass = totalMass

        val biggestSize = max(max(node.shape.height, node.shape.radius), node.shape.width)
        if (parentBranch.maxWidth < biggestSize) {
            parentBranch.maxWidth = biggestSize
        }
    }

    /**
     * Determines in which branch the node will be placed.
     */
    private fun _placeInTree(
        parentBranch: Branch,
        node: Node,
        skipMassUpdate: Boolean = false
    ) {
        if (!skipMassUpdate) {
            _updateBranchMass(parentBranch, node)
        }

        // We assume parentBranch is split (childrenCount == 4)
        val children = parentBranch.children as BranchChildren.SubBranches
        val rangeNW = children.NW.range // Use NW range to determine quadrant

        val region = if (rangeNW.maxX > node.x) { // in NW or SW
            if (rangeNW.maxY > node.y) "NW" else "SW"
        } else { // in NE or SE
            if (rangeNW.maxY > node.y) "NE" else "SE"
        }

        _placeInRegion(parentBranch, node, region)
    }

    /**
     * Actually places the node in a region (or branch).
     */
    private fun _placeInRegion(parentBranch: Branch, node: Node, region: String) {
        val children = parentBranch.children as BranchChildren.SubBranches

        // Get the specific child branch
        val childBranch = when (region) {
            "NW" -> children.NW
            "NE" -> children.NE
            "SW" -> children.SW
            "SE" -> children.SE
            else -> throw IllegalStateException("Invalid region")
        }

        when (childBranch.childrenCount) {
            0 -> {
                // Place node here, this branch becomes a leaf
                childBranch.children = BranchChildren.Data(node)
                childBranch.childrenCount = 1
                _updateBranchMass(childBranch, node)
            }
            1 -> {
                // This branch already contains a node, so we must split it
                val existingNode = (childBranch.children as BranchChildren.Data).node

                // Check for exact overlap
                // Note: removed (* 0.1) from _rng.nextDouble()
                if (existingNode != null && existingNode.x == node.x && existingNode.y == node.y) {
                    // Jiggle the new node slightly
                    node.x += _rng.nextDouble()
                    node.y += _rng.nextDouble()
                }

                // Split the branch. This will re-place the existingNode.
                _splitBranch(childBranch)
                // Now place the new node in the newly split branch
                _placeInTree(childBranch, node)
            }
            4 -> {
                // This branch is already split, place node recursively
                _placeInTree(childBranch, node)
            }
        }
    }

    /**
     * This function splits a branch into 4 sub-branches. If the branch
     * contained a node, we place it in the appropriate sub-branch.
     */
    private fun _splitBranch(parentBranch: Branch) {
        var containedNode: Node? = null
        if (parentBranch.childrenCount == 1) {
            containedNode = (parentBranch.children as BranchChildren.Data).node
            // Reset mass, it will be recalculated
            parentBranch.mass = 0.0
            parentBranch.centerOfMass.x = 0.0
            parentBranch.centerOfMass.y = 0.0
        }

        parentBranch.childrenCount = 4

        // Create the 4 new sub-regions
        val nw = _createRegion(parentBranch, "NW")
        val ne = _createRegion(parentBranch, "NE")
        val sw = _createRegion(parentBranch, "SW")
        val se = _createRegion(parentBranch, "SE")

        parentBranch.children = BranchChildren.SubBranches(nw, ne, sw, se)

        if (containedNode != null) {
            // Re-place the node that was previously in this branch
            _placeInTree(parentBranch, containedNode)
        }
    }

    /**
     * This function creates a new [Branch] for a specific sub-region.
     * @return The newly created [Branch].
     */
    private fun _createRegion(parentBranch: Branch, region: String): Branch {
        val minX: Double
        val maxX: Double
        val minY: Double
        val maxY: Double
        val childSize = 0.5 * parentBranch.size

        when (region) {
            "NW" -> {
                minX = parentBranch.range.minX
                maxX = parentBranch.range.minX + childSize
                minY = parentBranch.range.minY
                maxY = parentBranch.range.minY + childSize
            }
            "NE" -> {
                minX = parentBranch.range.minX + childSize
                maxX = parentBranch.range.maxX
                minY = parentBranch.range.minY
                maxY = parentBranch.range.minY + childSize
            }
            "SW" -> {
                minX = parentBranch.range.minX
                maxX = parentBranch.range.minX + childSize
                minY = parentBranch.range.minY + childSize
                maxY = parentBranch.range.maxY
            }
            "SE" -> {
                minX = parentBranch.range.minX + childSize
                maxX = parentBranch.range.maxX
                minY = parentBranch.range.minY + childSize
                maxY = parentBranch.range.maxY
            }
            else -> throw IllegalStateException("Invalid region")
        }

        val newRange = Range(minX, maxX, minY, maxY)

        return Branch(
            range = newRange,
            size = childSize,
            level = parentBranch.level + 1,
            children = BranchChildren.Data(null), // Starts as an empty leaf
            childrenCount = 0 // Starts empty
        )
    }
}