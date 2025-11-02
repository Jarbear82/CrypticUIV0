package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.ui.geometry.Offset

/**
 * Computes the convex hull of a set of points using the Monotone Chain algorithm.
 * Based on Andrew's monotone chain algorithm.
 * https://en.wikibooks.org/wiki/Algorithm_Implementation/Geometry/Convex_hull/Monotone_chain
 */
object ConvexHull {

    /**
     * 2D cross product of OA and OB vectors, i.e. z-component of their 3D cross product.
     * @return A positive value, if OAB makes a counter-clockwise turn,
     * negative for clockwise turn, and zero if points are collinear.
     */
    private fun crossProduct(o: Offset, a: Offset, b: Offset): Float {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }

    /**
     * Computes the convex hull of a set of points.
     * @param points A list of [Offset] points.
     * @return A list of points representing the convex hull, in counter-clockwise order.
     */
    fun compute(points: List<Offset>): List<Offset> {
        val n = points.size
        if (n < 3) return points.distinct() // Hull is just the points themselves if < 3

        // Sort points lexicographically (by x-coordinate, then y-coordinate)
        val sortedPoints = points.sortedWith(compareBy({ it.x }, { it.y }))

        val hull = mutableListOf<Offset>()

        // Build lower hull
        for (p in sortedPoints) {
            while (hull.size >= 2 && crossProduct(hull[hull.size - 2], hull.last(), p) <= 0) {
                hull.removeAt(hull.size - 1) // Remove last
            }
            hull.add(p)
        }

        // Build upper hull
        val lowerHullSize = hull.size
        // Iterate in reverse order
        for (i in (n - 2) downTo 0) {
            val p = sortedPoints[i]
            while (hull.size > lowerHullSize && crossProduct(hull[hull.size - 2], hull.last(), p) <= 0) {
                hull.removeAt(hull.size - 1) // Remove last
            }
            hull.add(p)
        }

        // Remove the last point, which is a duplicate of the first point
        hull.removeAt(hull.size - 1)

        return hull
    }
}
