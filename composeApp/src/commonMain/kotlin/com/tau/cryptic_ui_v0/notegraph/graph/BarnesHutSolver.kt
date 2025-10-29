// jarbear82/crypticuiv0/CrypticUIV0-kotlin-graph/composeApp/src/commonMain/kotlin/com/tau/cryptic_ui_v0/notegraph/graph/BarnesHutSolver.kt
package com.tau.cryptic_ui_v0.notegraph.graph.physics

import kotlin.math.*
import kotlin.random.Random

/**
 * Barnes Hut Solver (from user prompt, updated based on vis-network.js logic)
 */
open class BarnesHutSolver(
    protected val body: Body,
    protected val physicsBody: PhysicsBody,
    options: BarnesHutOptions
) {
    var options: BarnesHutOptions = options
        set(value) {
            field = value
            thetaInversed = if (value.theta != 0.0) 1.0 / value.theta else Double.POSITIVE_INFINITY
            // Ensure avoidOverlap is between 0 and 1
            effectiveAvoidOverlap = max(0.0, min(1.0, value.avoidOverlap ?: 0.0))
            // Pre-calculate minimum distance factor based on avoidOverlap for efficiency
            // avoidOverlap = 0 --> minDistanceFactor = 0.1 --> minimum distance will be 0.1 + 0*radius = 0.1
            // avoidOverlap = 1 --> minDistanceFactor = 1.1 --> minimum distance will be 0.1 + 1*radius
            minDistanceFactor = 0.1 + effectiveAvoidOverlap
        }

    private var thetaInversed: Double
    protected var effectiveAvoidOverlap: Double
    protected var minDistanceFactor: Double // Pre-calculated factor for minimum distance check

    var barnesHutTree: BarnesHutTree? = null
    private val _rng = Random(options.randomSeed ?: System.currentTimeMillis())

    // Minimum distance for calculations if distance is exactly 0 after overlap adjustments etc.
    protected val minimumCalculationDistance = 0.1

    init {
        // Initialize properties using the custom setter
        this.options = options
        thetaInversed = if (this.options.theta != 0.0) 1.0 / this.options.theta else Double.POSITIVE_INFINITY
        effectiveAvoidOverlap = max(0.0, min(1.0, this.options.avoidOverlap ?: 0.0))
        minDistanceFactor = 0.1 + effectiveAvoidOverlap
    }

    fun solve() {
        // Optimization: Do nothing if gravity is zero and there are no nodes
        if (options.gravitationalConstant == 0.0 && physicsBody.physicsNodeIndices.isEmpty()) {
            return
        }

        val nodes = body.nodes
        val nodeIndices = physicsBody.physicsNodeIndices
        val nodeCount = nodeIndices.size

        // Early exit if no nodes to process
        if (nodeCount == 0) return

        val tree = formBarnesHutTree(nodes, nodeIndices)
        barnesHutTree = tree // For potential debugging

        // Calculate forces for each node
        for (i in 0 until nodeCount) {
            val node = nodes[nodeIndices[i]]
            // Only calculate forces for nodes with mass and present in the physics simulation
            if (node != null && node.options.mass > 0 && physicsBody.forces.containsKey(node.id)) {
                getForceContributions(tree.root, node)
            }
        }
    }

    private fun getForceContributions(parentBranch: Branch, node: Node) {
        // Recursively check children if they exist
        parentBranch.children.NW?.let { getForceContribution(it, node) }
        parentBranch.children.NE?.let { getForceContribution(it, node) }
        parentBranch.children.SW?.let { getForceContribution(it, node) }
        parentBranch.children.SE?.let { getForceContribution(it, node) }
    }


    private fun getForceContribution(branch: Branch, node: Node) {
        // Skip empty branches
        if (branch.childrenCount == 0) return

        val dx = branch.centerOfMass.x - node.x
        val dy = branch.centerOfMass.y - node.y
        val distanceSq = dx * dx + dy * dy

        // Handle exact overlap (rare, but possible with jitter or initial placement)
        if (distanceSq == 0.0) {
            // Apply a small random force if it's a leaf node (and not the same node)
            if (branch.childrenCount == 1) {
                val childNode = branch.children.data
                if (childNode != null && childNode.id != node.id) {
                    applyRandomForce(node, branch)
                }
            } else { // If it's an internal node (childrenCount == 4), recurse further
                getForceContributions(branch, node)
            }
            return // Stop further processing for this branch if distance was zero
        }

        val distance = sqrt(distanceSq)
        val thetaConditionPassed = distance * branch.calcSize > thetaInversed // (distance / size) > (1 / theta) == (size / distance) < theta

        if (thetaConditionPassed) {
            // Node is far away, approximate using center of mass
            calculateForces(distance, dx, dy, node, branch)
        } else {
            // Node is close, examine children
            if (branch.childrenCount == 4) {
                // It's an internal node, recurse
                getForceContributions(branch, node)
            } else { // childrenCount must be 1 (leaf node)
                val childNode = branch.children.data
                // Calculate force directly if it's a different node
                if (childNode != null && childNode.id != node.id) {
                    calculateForces(distance, dx, dy, node, branch)
                }
            }
        }
    }

    /**
     * Applies a small random force to prevent nodes getting stuck at distance 0.
     */
    private fun applyRandomForce(node: Node, interactingBranch: Branch) {
        val randomForceMagnitude = abs(options.gravitationalConstant) * 0.01 // Small force relative to gravity
        var rdx = (_rng.nextDouble() - 0.5) * 2.0
        var rdy = (_rng.nextDouble() - 0.5) * 2.0
        val lenSq = rdx * rdx + rdy * rdy
        if (lenSq > 0) {
            val lenInv = 1.0 / sqrt(lenSq)
            rdx *= lenInv
            rdy *= lenInv
        } else {
            rdx = 1.0; rdy = 0.0 // Fallback direction
        }

        val fx = rdx * randomForceMagnitude
        val fy = rdy * randomForceMagnitude

        physicsBody.forces[node.id]?.let {
            it.x -= fx // <-- MODIFIED: Subtract random force
            it.y -= fy // <-- MODIFIED: Subtract random force
        }

        // Apply opposite force if the branch is just a single *different* node
        if (interactingBranch.childrenCount == 1 && interactingBranch.children.data != null) {
            val childNodeId = interactingBranch.children.data!!.id
            if (childNodeId != node.id) {
                physicsBody.forces[childNodeId]?.let {
                    it.x += fx // <-- MODIFIED: Add random force
                    it.y += fy // <-- MODIFIED: Add random force
                }
            }
        }
    }


    /**
     * Default Barnes Hut force calculation. Can be overridden by subclasses like ForceAtlas2Based.
     */
    open fun calculateForces(distanceIn: Double, dxIn: Double, dyIn: Double, node: Node, branch: Branch) {
        var distance = distanceIn
        var dx = dxIn
        var dy = dyIn

        val nodeRadius = node.shape.radius ?: 0.0
        // Calculate minimum distance using the pre-calculated factor
        val minimumDistance = minDistanceFactor * nodeRadius

        // Check for overlap first
        if (effectiveAvoidOverlap > 0 && distance < minimumDistance) {
            // Calculate overlap force (based roughly on vis.js overlap strategy)
            val overlap = minimumDistance - distance
            // Make overlap force stronger than gravity, proportional to overlap
            val overlapForceMagnitude = abs(options.gravitationalConstant) * (1 + overlap / nodeRadius) * 0.1 // Adjust multiplier as needed

            // Ensure we have a direction (handle distance near zero, though zero distance is handled in getForceContribution)
            if (distance < 1e-9) { // Use a small epsilon
                dx = (_rng.nextDouble() - 0.5) * 2.0
                dy = (_rng.nextDouble() - 0.5) * 2.0
                val lenSq = dx*dx + dy*dy
                if (lenSq > 0) {
                    val lenInv = 1.0 / sqrt(lenSq)
                    dx *= lenInv
                    dy *= lenInv
                } else { dx = 1.0; dy = 0.0 } // Fallback
                distance = minimumCalculationDistance // Prevent division by zero later
            }

            // Apply force pushing nodes apart
            val fx = (dx / distance) * overlapForceMagnitude
            val fy = (dy / distance) * overlapForceMagnitude

            physicsBody.forces[node.id]?.let {
                it.x -= fx // <-- MODIFIED: Subtract overlap force
                it.y -= fy // <-- MODIFIED: Subtract overlap force
            }

            // Apply opposite force only if branch represents a single different node
            if (branch.childrenCount == 1 && branch.children.data != null) {
                val childNodeId = branch.children.data!!.id
                if(childNodeId != node.id) {
                    physicsBody.forces[childNodeId]?.let {
                        it.x += fx // <-- MODIFIED: Add overlap force
                        it.y += fy // <-- MODIFIED: Add overlap force
                    }
                }
            }

        } else {
            // Standard gravitational force calculation if no overlap or overlap disabled
            distance = max(minimumCalculationDistance, distance) // Ensure minimum distance

            // Original force calculation using distance^3 for component projection
            val gravityForceFactor = (options.gravitationalConstant * branch.mass * node.options.mass) / distance.pow(3)
            val fx = dx * gravityForceFactor
            val fy = dy * gravityForceFactor

            physicsBody.forces[node.id]?.let {
                it.x -= fx // <-- MODIFIED: Subtract gravitational force
                it.y -= fy // <-- MODIFIED: Subtract gravitational force
            }

            // Apply opposite force only if branch represents a single different node
            if (branch.childrenCount == 1 && branch.children.data != null) {
                val childNodeId = branch.children.data!!.id
                if(childNodeId != node.id) {
                    physicsBody.forces[childNodeId]?.let {
                        it.x += fx // <-- MODIFIED: Add gravitational force
                        it.y += fy // <-- MODIFIED: Add gravitational force
                    }
                }
            }
        }
    }


    private fun formBarnesHutTree(nodes: Map<String, Node>, nodeIndices: List<String>): BarnesHutTree {
        val nodeCount = nodeIndices.size
        // Handle empty node list gracefully
        if (nodeCount == 0) {
            val defaultRange = Range(-0.5, 0.5, -0.5, 0.5)
            val root = Branch(range = defaultRange, size = 1.0, calcSize = 1.0, childrenCount = 0)
            return BarnesHutTree(root)
        }

        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
        var maxX = Double.MIN_VALUE; var maxY = Double.MIN_VALUE
        var firstNodePos: Point? = null

        // Determine bounds based on nodes with mass
        for (i in 0 until nodeCount) {
            val node = nodes[nodeIndices[i]]
            if (node != null && node.options.mass > 0) {
                if (firstNodePos == null) firstNodePos = Point(node.x, node.y)
                minX = min(minX, node.x); maxX = max(maxX, node.x)
                minY = min(minY, node.y); maxY = max(maxY, node.y)
            }
        }

        // Handle cases with no valid nodes or only one node
        if (firstNodePos == null) { // No nodes with mass found
            minX = -0.5; maxX = 0.5; minY = -0.5; maxY = 0.5
        } else if (minX == Double.MAX_VALUE) { // Only one node found or all nodes at the same position
            minX = firstNodePos.x - 0.5; maxX = firstNodePos.x + 0.5
            minY = firstNodePos.y - 0.5; maxY = firstNodePos.y + 0.5
        }

        // Make the region square
        val sizeX = maxX - minX; val sizeY = maxY - minY
        val sizeDiff = sizeX - sizeY
        if (sizeDiff > 0) { minY -= 0.5 * sizeDiff; maxY += 0.5 * sizeDiff }
        else { minX += 0.5 * sizeDiff; maxX -= 0.5 * sizeDiff }

        val minimumTreeSize = 1e-5 // Avoid zero size
        val rootSize = max(minimumTreeSize, maxX - minX) // Use the now square size
        val halfRootSize = 0.5 * rootSize
        val centerX = 0.5 * (minX + maxX); val centerY = 0.5 * (minY + maxY)

        val rootRange = Range(centerX - halfRootSize, centerX + halfRootSize, centerY - halfRootSize, centerY + halfRootSize)
        val root = Branch(
            range = rootRange,
            size = rootSize,
            calcSize = if (rootSize == 0.0) Double.POSITIVE_INFINITY else 1.0 / rootSize, // Avoid division by zero
            childrenCount = 0 // Start as a leaf
        )
        // DO NOT split the root initially. Let placeInTree handle the first split.

        // Place nodes one by one
        for (i in 0 until nodeCount) {
            val node = nodes[nodeIndices[i]]
            if (node != null && node.options.mass > 0) {
                // Clamp node position to be strictly within the root boundary for robust quadrant calculation
                // Subtracting a tiny epsilon from maxX/maxY ensures it falls into the correct quadrant
                val epsilon = 1e-9
                node.x = node.x.coerceIn(root.range.minX, root.range.maxX - epsilon)
                node.y = node.y.coerceIn(root.range.minY, root.range.maxY - epsilon)

                // Place the node, starting from the root.
                placeInTree(root, node)
            }
        }
        return BarnesHutTree(root)
    }

    private fun updateBranchMass(branch: Branch, node: Node) {
        val centerOfMass = branch.centerOfMass
        val totalMass = branch.mass + node.options.mass
        // Avoid division by zero if totalMass is 0 (shouldn't happen with nodes having mass > 0)
        if (totalMass == 0.0) return

        val totalMassInv = 1.0 / totalMass
        // Update center of mass: (current_com * current_mass + new_node_pos * new_node_mass) / new_total_mass
        centerOfMass.x = (centerOfMass.x * branch.mass + node.x * node.options.mass) * totalMassInv
        centerOfMass.y = (centerOfMass.y * branch.mass + node.y * node.options.mass) * totalMassInv
        branch.mass = totalMass

        // Update maxWidth (used for overlap check approximation)
        val nodeRadius = node.shape.radius ?: 0.0
        val nodeSize = max(nodeRadius, max(node.shape.width ?: 0.0, node.shape.height ?: 0.0))
        branch.maxWidth = max(branch.maxWidth, nodeSize)
    }

    // skipMassUpdate removed, simplified logic closer to vis.js
    private fun placeInTree(branch: Branch, node: Node) {
        // Update the mass of the branch *before* deciding where to place the node.
        updateBranchMass(branch, node)

        // Determine which quadrant the node belongs to.
        val children = branch.children
        val branchCenterX = branch.range.minX + branch.size / 2.0
        val branchCenterY = branch.range.minY + branch.size / 2.0

        val targetChildBranch: Branch?
        val regionStr: String

        if (node.x < branchCenterX) {
            targetChildBranch = if (node.y < branchCenterY) children.NW else children.SW
            regionStr = if (node.y < branchCenterY) "NW" else "SW"
        } else {
            targetChildBranch = if (node.y < branchCenterY) children.NE else children.SE
            regionStr = if (node.y < branchCenterY) "NE" else "SE"
        }

        // Place the node in the determined region/branch.
        if (targetChildBranch != null) {
            placeInRegion(branch, node, targetChildBranch, regionStr)
        } else {
            // This can happen if the branch hasn't been split yet (it's a leaf).
            // The logic in placeInRegion handles the initial split.
            // If targetChildBranch is null here, it means branch.childrenCount should be 0 or 1.
            if (branch.childrenCount <= 1) {
                placeInRegion(branch, node, branch, regionStr) // Pass the branch itself for leaf handling
            } else {
                // This indicates a potential logic error if childrenCount is 4 but a quadrant is null.
                throw IllegalStateException("Target child branch $regionStr is null but parent branch is not a leaf (childrenCount=${branch.childrenCount}). Node ${node.id} at (${node.x}, ${node.y}) in parent ${branch.range}")
            }
        }
    }

    // region parameter is now mainly for debugging/clarity, quadrant logic is inside placeInTree
    private fun placeInRegion(parentBranch: Branch, node: Node, targetBranch: Branch, region: String) {
        when (targetBranch.childrenCount) {
            0 -> { // Target branch is an empty leaf
                targetBranch.children.data = node
                targetBranch.childrenCount = 1
                // Mass was already updated in placeInTree for the parent, now update the leaf itself
                updateBranchMass(targetBranch, node)
            }
            1 -> { // Target branch is a leaf with one node already
                val existingNode = targetBranch.children.data
                if (existingNode != null) {
                    // Check for exact overlap and apply jitter if necessary
                    if (existingNode.x == node.x && existingNode.y == node.y) {
                        val sizeFactor = targetBranch.size * 0.01 // Small jitter relative to branch size
                        var jitterX = 0.0; var jitterY = 0.0
                        while (abs(jitterX) < 1e-9) { jitterX = (_rng.nextDouble() - 0.5) * 2.0 * sizeFactor } // Ensure non-zero jitter
                        while (abs(jitterY) < 1e-9) { jitterY = (_rng.nextDouble() - 0.5) * 2.0 * sizeFactor }
                        node.x = (node.x + jitterX).coerceIn(targetBranch.range.minX, targetBranch.range.maxX - 1e-9) // Clamp within bounds
                        node.y = (node.y + jitterY).coerceIn(targetBranch.range.minY, targetBranch.range.maxY - 1e-9)
                        println("Applied jitter to node ${node.id} due to overlap with ${existingNode.id}. New pos: (${node.x}, ${node.y})")
                    }

                    // Split the current branch (targetBranch was the leaf)
                    splitBranch(targetBranch) // This clears targetBranch.children.data and sets childrenCount to 4

                    // Important: splitBranch might immediately make the branch a leaf again if the size is too small.
                    if (targetBranch.childrenCount == 4) {
                        // Re-insert the existing node into the *newly split* targetBranch
                        // We need to place it again because splitBranch cleared the data
                        placeInTree(targetBranch, existingNode)

                        // Now, place the new node into the *newly split* targetBranch
                        placeInTree(targetBranch, node)
                    } else {
                        // splitBranch resulted in a leaf again (size too small)
                        // This is an edge case. For simplicity, we might just keep the original node
                        // and effectively ignore the new one for force calculations in this tiny region.
                        // OR, try to place the new node anyway if the existing node placement failed (unlikely)
                        if (targetBranch.children.data == null) { // If splitBranch cleared but didn't re-place
                            targetBranch.children.data = node
                            targetBranch.childrenCount = 1
                            updateBranchMass(targetBranch, node) // Update leaf mass
                            println("Warning: Branch ${targetBranch.range} too small after split. Placed new node ${node.id}, existing node ${existingNode.id} might be lost in this branch.")
                        } else {
                            println("Warning: Branch ${targetBranch.range} too small after split. Keeping existing node ${existingNode.id}, ignoring new node ${node.id} in this branch.")
                            // Ensure mass reflects only the existing node
                            targetBranch.mass = 0.0; targetBranch.centerOfMass = Point(0.0,0.0);
                            updateBranchMass(targetBranch, existingNode)
                        }
                    }
                } else {
                    // Should not happen if childrenCount is 1
                    throw IllegalStateException("Branch ${targetBranch.range} has childrenCount 1 but no data node.")
                }
            }
            4 -> { // Target branch is already an internal node
                // Recurse down to place the node in the appropriate sub-quadrant
                placeInTree(targetBranch, node)
            }
            else -> throw IllegalStateException("Invalid childrenCount (${targetBranch.childrenCount}) for branch ${targetBranch.range}")
        }
    }


    private fun splitBranch(branch: Branch) {
        // Minimum size check (moved from insertRegion to prevent unnecessary object creation)
        if (branch.size < 1e-6) {
            println("Warning: Branch size is very small (${branch.size}). Cannot split further. Branch at ${branch.range} remains a leaf.")
            // Ensure it remains a leaf. If it had data, keep it. If not, it's an empty leaf.
            if (branch.children.data == null) {
                branch.childrenCount = 0
            } else {
                branch.childrenCount = 1
                // Mass should already be correct from previous updateBranchMass calls.
            }
            return // Stop the splitting process
        }

        val containedNode = if (branch.childrenCount == 1) branch.children.data else null

        // Reset branch properties before creating children and re-inserting
        branch.mass = 0.0
        branch.centerOfMass = Point(0.0, 0.0)
        branch.children.data = null // Clear data link
        branch.childrenCount = 4    // Mark as internal node (temporarily)

        // Create the four child quadrants
        insertRegion(branch, "NW")
        insertRegion(branch, "NE")
        insertRegion(branch, "SW")
        insertRegion(branch, "SE")

        // Re-insert the original node (if any) into the correct new child quadrant
        containedNode?.let {
            // placeInTree will update masses correctly starting from the child branch upwards
            placeInTree(branch, it)
        }
    }


    private fun insertRegion(parentBranch: Branch, region: String) {
        val childSize = 0.5 * parentBranch.size
        val parentRange = parentBranch.range
        val midX = parentRange.minX + childSize
        val midY = parentRange.minY + childSize

        val newRange = when (region) {
            "NW" -> Range(parentRange.minX, midX, parentRange.minY, midY)
            "NE" -> Range(midX, parentRange.maxX, parentRange.minY, midY)
            "SW" -> Range(parentRange.minX, midX, midY, parentRange.maxY)
            "SE" -> Range(midX, parentRange.maxX, midY, parentRange.maxY)
            else -> throw IllegalArgumentException("Invalid region specified: $region")
        }

        val newBranch = Branch(
            range = newRange,
            size = childSize,
            // Avoid division by zero for calcSize
            calcSize = if (childSize == 0.0) Double.POSITIVE_INFINITY else 1.0 / childSize,
            level = parentBranch.level + 1,
            childrenCount = 0 // New branches start as empty leaves
        )

        // Assign the new branch to the correct child property
        when (region) {
            "NW" -> parentBranch.children.NW = newBranch
            "NE" -> parentBranch.children.NE = newBranch
            "SW" -> parentBranch.children.SW = newBranch
            "SE" -> parentBranch.children.SE = newBranch
        }
    }
}

// Options class remains the same
data class BarnesHutOptions(
    val theta: Double = 0.5,
    val gravitationalConstant: Double = -2000.0, // vis.js default for barnesHut
    val avoidOverlap: Double? = 0.0, // vis.js default is 0
    val randomSeed: Long? = null
)