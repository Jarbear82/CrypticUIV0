package com.tau.nexus_note.datamodels

/**
 * A data class to hold the nodes and edges that are encapsulated
 * inside a Supernode. This is attached to the parent `GraphNode`
 * (the supernode container) when `isSupernode` is true.
 *
 * The positions of the nodes in this map are relative to the
 * Supernode's center (or 0,0 in their own physics simulation)
 * and must be offset by the parent Supernode's `pos` when rendering.
 *
 * @param nodes A map of the internal `GraphNode`s, keyed by their ID.
 * @param edges A list of the internal `GraphEdge`s that connect the internal nodes.
 */
data class InternalGraph(
    val nodes: Map<Long, GraphNode>,
    val edges: List<GraphEdge>
)