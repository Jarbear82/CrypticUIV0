package com.tau.cryptic_ui_v0.notegraph.graph.layout

import com.tau.cryptic_ui_v0.notegraph.graph.physics.Edge
import com.tau.cryptic_ui_v0.notegraph.graph.physics.EnergyNodeInfo
import com.tau.cryptic_ui_v0.notegraph.graph.physics.Node
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * KamadaKawai positions nodes based on
 * "AN ALGORITHM FOR DRAWING GENERAL UNDIRECTED GRAPHS"
 * by Tomihisa KAMADA and Satoru KAWAI (1989)
 *
 * This algorithm treats the graph as a spring system, minimizing the
 * energy (difference between ideal distance and current distance)
 * to find stable node positions.
 *
 * This version is updated to work with the data classes from PhysicsInterfaces.kt
 */
internal class KamadaKawai(
    private val nodes: Map<String, Node>,
    private val edges: Map<String, Edge>,
    private val springLength: Double,
    private val springConstant: Double
) {
    private val distanceSolver = FloydWarshall()
    private val nodeIds = nodes.keys.toList()
    private val nodeIndexMap = nodeIds.withIndex().associate { (i, id) -> id to i }

    // L_matrix: Ideal distance (spring length * shortest path)
    private var lMatrix: Map<String, Map<String, Double>> = emptyMap()
    // K_matrix: Spring constant (strength / shortest_path^2)
    private var kMatrix: Map<String, Map<String, Double>> = emptyMap()

    // E_matrix: Energy between nodes.
    private var eMatrix: MutableMap<String, MutableList<Pair<Double, Double>>> = mutableMapOf()
    // E_sums: Total energy (dx, dy) for each node.
    private var eSums: MutableMap<String, Pair<Double, Double>> = mutableMapOf()

    companion object {
        /**
         * Main entry point to run the layout algorithm.
         * It positions the nodes in the passed map directly.
         */
        fun calculatePositions(
            nodes: Map<String, Node>,
            edges: Map<String, Edge>,
            springLength: Double,
            springConstant: Double
        ) {
            val kamadaKawai = KamadaKawai(nodes, edges, springLength, springConstant)
            kamadaKawai.solve()
        }
    }


    /**
     * Runs the layout algorithm to position the nodes.
     */
    private fun solve() {
        if (nodeIds.isEmpty()) return

        // Get distance matrix
        val dMatrix = distanceSolver.getDistances(this.nodes, this.edges)

        // Create L and K matrices
        createLMatrix(dMatrix)
        createKMatrix(dMatrix)

        // Initial E Matrix (Energy)
        createEMatrix()

        // Calculate positions
        val threshold = 0.01
        val innerThreshold = 1.0
        var iterations = 0
        val maxIterations = max(1000, min(10 * this.nodeIds.size, 6000))
        val maxInnerIterations = 5

        var maxEnergy = 1e9
        var highENodeId: String
        var dEDx: Double
        var dEDy: Double

        while (maxEnergy > threshold && iterations < maxIterations) {
            iterations += 1
            val (nodeId, energy, dx, dy) = getHighestEnergyNode()
            highENodeId = nodeId
            maxEnergy = energy
            dEDx = dx
            dEDy = dy

            var deltaM = maxEnergy
            var subIterations = 0
            while (deltaM > innerThreshold && subIterations < maxInnerIterations) {
                subIterations += 1
                moveNode(highENodeId, dEDx, dEDy)
                val (newDelta, newDx, newDy) = getEnergy(highENodeId)
                deltaM = newDelta
                dEDx = newDx
                dEDy = newDy
            }
        }
    }

    /**
     * Finds the node with the highest energy (most "out of place").
     */
    private fun getHighestEnergyNode(): EnergyNodeInfo {
        var maxEnergy = 0.0
        var maxEnergyNodeId = nodeIds.first()
        var dEDxMax = 0.0
        var dEDyMax = 0.0

        for (m in nodeIds) {
            val node = nodes[m]!!
            // Only evaluate nodes that are not fixed
            if (!node.options.fixed.x && !node.options.fixed.y) {
                val (deltaM, dEDx, dEDy) = getEnergy(m)
                if (maxEnergy < deltaM) {
                    maxEnergy = deltaM
                    maxEnergyNodeId = m
                    dEDxMax = dEDx
                    dEDyMax = dEDy
                }
            }
        }
        return EnergyNodeInfo(maxEnergyNodeId, maxEnergy, dEDxMax, dEDyMax)
    }

    /**
     * Calculates the energy of a single node.
     * Returns (delta_m, dE_dx, dE_dy)
     */
    private fun getEnergy(m: String): Triple<Double, Double, Double> {
        val (dEDx, dEDy) = eSums[m] ?: Pair(0.0, 0.0)
        val deltaM = sqrt(dEDx.pow(2) + dEDy.pow(2))
        return Triple(deltaM, dEDx, dEDy)
    }

    /**
     * Moves a node based on its energy gradient.
     */
    private fun moveNode(m: String, dEDx: Double, dEDy: Double) {
        var d2EDx2 = 0.0
        var d2EDxDy = 0.0
        var d2EDy2 = 0.0

        val (xM, yM) = nodes[m]!!.let { it.x to it.y }
        val km = kMatrix[m]!!
        val lm = lMatrix[m]!!

        for (i in nodeIds) {
            if (i != m) {
                val (xI, yI) = nodes[i]!!.let { it.x to it.y }
                val kMat = km[i]!!
                val lMat = lm[i]!!
                val denominator = 1.0 / ((xM - xI).pow(2) + (yM - yI).pow(2)).pow(1.5)
                d2EDx2 += kMat * (1 - lMat * (yM - yI).pow(2) * denominator)
                d2EDxDy += kMat * (lMat * (xM - xI) * (yM - yI) * denominator)
                d2EDy2 += kMat * (1 - lMat * (xM - xI).pow(2) * denominator)
            }
        }

        // Solve the linear system for dx and dy
        val a = d2EDx2
        val b = d2EDxDy
        val c = dEDx
        val d = d2EDy2
        val e = dEDy

        val dy: Double
        val dx: Double

        if (a.isNaN() || b.isNaN() || c.isNaN() || d.isNaN() || e.isNaN()) {
            println("KamadaKawai: NaN detected in moveNode. Skipping move.")
            return
        }

        val denominator = (b / a - d / b)
        if (a == 0.0 || b == 0.0 || denominator == 0.0) {
            // Cannot solve system, make a small move in the gradient direction
            val magnitude = sqrt(c*c + e*e)
            if (magnitude > 0) {
                dx = -c / magnitude
                dy = -e / magnitude
            } else {
                dx = 0.0
                dy = 0.0
            }
        } else {
            dy = (c / a + e / b) / denominator
            dx = -(b * dy + c) / a
        }

        // Move the node
        val node = nodes[m]!!
        if (!dx.isNaN() && !dy.isNaN()) {
            node.x += dx
            node.y += dy
        }

        // Recalculate E_matrix (incrementally)
        updateEMatrix(m)
    }

    /**
     * Create the L matrix: L_ij = ideal_length * shortest_path(i, j)
     */
    private fun createLMatrix(dMatrix: Map<String, Map<String, Int>>) {
        val newLMatrix = mutableMapOf<String, Map<String, Double>>()
        for (i in nodeIds) {
            val row = mutableMapOf<String, Double>()
            for (j in nodeIds) {
                row[j] = this.springLength * (dMatrix[i]!![j] ?: 0)
            }
            newLMatrix[i] = row
        }
        this.lMatrix = newLMatrix
    }

    /**
     * Create the K matrix: K_ij = spring_constant / shortest_path(i, j)^2
     */
    private fun createKMatrix(dMatrix: Map<String, Map<String, Int>>) {
        val newKMatrix = mutableMapOf<String, Map<String, Double>>()
        for (i in nodeIds) {
            val row = mutableMapOf<String, Double>()
            for (j in nodeIds) {
                val dist = (dMatrix[i]!![j] ?: 1).toDouble()
                row[j] = if (dist == 0.0) 0.0 else this.springConstant / (dist * dist)
            }
            newKMatrix[i] = row
        }
        this.kMatrix = newKMatrix
    }

    /**
     * Create matrix with all energies between nodes and calculate initial sums.
     */
    private fun createEMatrix() {
        eMatrix = mutableMapOf()
        eSums = mutableMapOf()

        // Initialize E_matrix with empty, sized lists
        for (m in nodeIds) {
            eMatrix[m] = MutableList(nodeIds.size) { Pair(0.0, 0.0) }
        }

        // Fill E_matrix (upper triangle)
        for (mIdx in nodeIds.indices) {
            val m = nodeIds[mIdx]
            val (xM, yM) = nodes[m]!!.let { it.x to it.y }
            for (iIdx in mIdx until nodeIds.size) {
                val i = nodeIds[iIdx]
                if (i != m) {
                    val (xI, yI) = nodes[i]!!.let { it.x to it.y }
                    val dist = sqrt((xM - xI).pow(2) + (yM - yI).pow(2))
                    val denominator = if (dist == 0.0) 0.0 else 1.0 / dist

                    val kVal = kMatrix[m]!![i]!!
                    val lVal = lMatrix[m]!![i]!!

                    val dx = kVal * (xM - xI - lVal * (xM - xI) * denominator)
                    val dy = kVal * (yM - yI - lVal * (yM - yI) * denominator)

                    eMatrix[m]!![iIdx] = Pair(dx, dy)
                    eMatrix[i]!![mIdx] = Pair(dx, dy) // Symmetric fill
                }
            }
        }

        // Calculate initial E_sums (full sums)
        for (m in nodeIds) {
            var dEDx = 0.0
            var dEDy = 0.0
            val row = eMatrix[m]!!
            for (iIdx in nodeIds.indices) {
                dEDx += row[iIdx].first
                dEDy += row[iIdx].second
            }
            eSums[m] = Pair(dEDx, dEDy)
        }
    }

    /**
     * Update energy matrix and sums incrementally after moving node 'm'.
     */
    private fun updateEMatrix(m: String) {
        val colm = eMatrix[m]!!
        val kcolm = kMatrix[m]!!
        val lcolm = lMatrix[m]!!
        val (xM, yM) = nodes[m]!!.let { it.x to it.y }

        var dEDx = 0.0
        var dEDy = 0.0

        for (iIdx in nodeIds.indices) {
            val i = nodeIds[iIdx]
            if (i != m) {
                // Keep old energy value for sum modification below
                val (oldDx, oldDy) = colm[iIdx]

                // Calc new energy
                val (xI, yI) = nodes[i]!!.let { it.x to it.y }
                val dist = sqrt((xM - xI).pow(2) + (yM - yI).pow(2))
                val denominator = if (dist == 0.0) 0.0 else 1.0 / dist

                val kVal = kcolm[i]!!
                val lVal = lcolm[i]!!

                val dx = kVal * (xM - xI - lVal * (xM - xI) * denominator)
                val dy = kVal * (yM - yI - lVal * (yM - yI) * denominator)

                colm[iIdx] = Pair(dx, dy)
                // Update symmetric cell
                eMatrix[i]!![nodeIndexMap[m]!!] = Pair(dx, dy)

                dEDx += dx
                dEDy += dy

                // Add new energy to sum of each column/row 'i'
                val (oldSumDx, oldSumDy) = eSums[i]!!
                eSums[i] = Pair(oldSumDx + dx - oldDx, oldSumDy + dy - oldDy)
            }
        }
        // Store new full sum for 'm'
        eSums[m] = Pair(dEDx, dEDy)
    }
}
