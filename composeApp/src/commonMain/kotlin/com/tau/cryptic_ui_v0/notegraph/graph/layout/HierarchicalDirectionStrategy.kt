package com.tau.cryptic_ui_v0.notegraph.graph.layout

import com.tau.cryptic_ui_v0.notegraph.graph.HierarchicalOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsNode

/**
 * Interface for defining the direction of the hierarchical layout.
 * This is a new, compatible version that works with the PhysicsNode data class.
 */
internal interface HierarchicalDirectionStrategy {
    fun getPosition(node: PhysicsNode): Float
    fun setPosition(node: PhysicsNode, position: Float)
    fun fix(node: PhysicsNode, level: Int)
    fun getCurveType(): String
}

/**
 * Vertical Strategy (UD or DU)
 * Coordinate `y` is fixed on levels, coordinate `x` is free.
 */
internal class VerticalStrategy(private val options: HierarchicalOptions) : HierarchicalDirectionStrategy {
    private val levelSeparation = options.levelSeparation

    override fun getPosition(node: PhysicsNode): Float = node.x
    override fun setPosition(node: PhysicsNode, position: Float) {
        node.x = position
    }

    override fun fix(node: PhysicsNode, level: Int) {
        node.y = levelSeparation * level
        node.isFixed = true // Fix node in the hierarchical dimension
    }

    override fun getCurveType(): String = "horizontal"
}

/**
 * Horizontal Strategy (LR or RL)
 * Coordinate `x` is fixed on levels, coordinate `y` is free.
 */
internal class HorizontalStrategy(private val options: HierarchicalOptions) : HierarchicalDirectionStrategy {
    private val levelSeparation = options.levelSeparation

    override fun getPosition(node: PhysicsNode): Float = node.y
    override fun setPosition(node: PhysicsNode, position: Float) {
        node.y = position
    }

    override fun fix(node: PhysicsNode, level: Int) {
        node.x = levelSeparation * level
        node.isFixed = true // Fix node in the hierarchical dimension
    }

    override fun getCurveType(): String = "vertical"
}
