package com.tau.nexus_note.settings

import androidx.compose.ui.graphics.Color
import com.tau.nexus_note.codex.graph.physics.PhysicsOptions
import kotlinx.serialization.Serializable

/**
 * Enumeration for the three possible theme modes.
 */
@Serializable
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

/**
 * Holds all settings related to a custom color theme.
 * Colors are stored as hex strings for serialization.
 */
@Serializable
data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColors: Boolean = false, // For Android 12+
    val useCustomTheme: Boolean = false,

    val primaryHex: String = "#6200EE",
    val onPrimaryHex: String = "#FFFFFF",
    val secondaryHex: String = "#03DAC6",
    val onSecondaryHex: String = "#000000",
    val backgroundHex: String = "#FFFFFF",
    val onBackgroundHex: String = "#000000",
    val surfaceHex: String = "#FFFFFF",
    val onSurfaceHex: String = "#000000"
) {
    companion object {
        val Default = ThemeSettings()
    }
}

/**
 * Holds settings for default graph physics.
 * This re-uses the PhysicsOptions data class.
 */
@Serializable
data class GraphPhysicsSettings(
    val options: PhysicsOptions = PhysicsOptions(
        gravity = 0.5f,
        repulsion = 2000f,
        spring = 0.1f,
        damping = 0.9f,
        nodeBaseRadius = 15f,
        nodeRadiusEdgeFactor = 2.0f,
        minDistance = 2.0f,
        barnesHutTheta = 1.2f,
        tolerance = 1.0f
    )
) {
    companion object {
        val Default = GraphPhysicsSettings()
    }
}

/**
 * Holds settings for graph rendering.
 */
@Serializable
data class GraphRenderingSettings(
    val startSimulationOnLoad: Boolean = true,
    val showNodeLabels: Boolean = true,
    val showEdgeLabels: Boolean = false,
    val showCrosshairs: Boolean = true
) {
    companion object {
        val Default = GraphRenderingSettings()
    }
}

/**
 * Holds settings for graph interaction.
 */
@Serializable
data class GraphInteractionSettings(
    val zoomSensitivity: Float = 1.0f,
    // Node sizing is part of physics, but we can put a shortcut here
    val nodeBaseRadius: Float = 15f,
    val nodeRadiusEdgeFactor: Float = 2.0f
) {
    companion object {
        val Default = GraphInteractionSettings()
    }
}

/**
 * Holds settings for data and codex file management.
 */
@Serializable
data class DataSettings(
    val defaultCodexDirectory: String = com.tau.nexus_note.utils.getHomeDirectoryPath(),
    val autoLoadLastCodex: Boolean = false,
    val autoRefreshCodex: Boolean = false,
    val refreshInterval: Float = 60f // in seconds
) {
    companion object {
        val Default = DataSettings()
    }
}

/**
 * Holds general application behavior settings.
 */
@Serializable
data class GeneralSettings(
    val startupScreen: String = "Nexus", // "Nexus" or "Last Codex"
    val defaultCodexView: String = "Graph", // "Graph" or "List"
    val confirmNodeEdgeDeletion: Boolean = true,
    val confirmSchemaDeletion: Boolean = true,
    val defaultMarkdownFlavor: String = "Obsidian"
) {
    companion object {
        val Default = GeneralSettings()
    }
}

/**
 * Root data class holding all application settings.
 */
@Serializable
data class SettingsData(
    val theme: ThemeSettings = ThemeSettings.Default,
    val graphPhysics: GraphPhysicsSettings = GraphPhysicsSettings.Default,
    val graphRendering: GraphRenderingSettings = GraphRenderingSettings.Default,
    val graphInteraction: GraphInteractionSettings = GraphInteractionSettings.Default,
    val data: DataSettings = DataSettings.Default,
    val general: GeneralSettings = GeneralSettings.Default
) {
    companion object {
        /**
         * The master default settings for the entire application.
         */
        val Default = SettingsData()
    }
}