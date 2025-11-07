package com.tau.nexus_note.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages the state for the Settings screen.
 * It receives the master MutableStateFlow from MainViewModel
 * and provides event handlers to modify it.
 */
class SettingsViewModel(
    private val settingsFlow: MutableStateFlow<SettingsData>
) {
    // --- Theme ---
    fun onThemeModeChange(mode: ThemeMode) {
        settingsFlow.update { it.copy(theme = it.theme.copy(themeMode = mode)) }
    }

    fun onUseCustomThemeChange(use: Boolean) {
        settingsFlow.update { it.copy(theme = it.theme.copy(useCustomTheme = use)) }
    }

    fun onPrimaryColorChange(hex: String) {
        settingsFlow.update { it.copy(theme = it.theme.copy(primaryHex = hex)) }
    }

    fun onSecondaryColorChange(hex: String) {
        settingsFlow.update { it.copy(theme = it.theme.copy(secondaryHex = hex)) }
    }

    fun onBackgroundColorChange(hex: String) {
        settingsFlow.update { it.copy(theme = it.theme.copy(backgroundHex = hex)) }
    }

    fun onSurfaceColorChange(hex: String) {
        settingsFlow.update { it.copy(theme = it.theme.copy(surfaceHex = hex)) }
    }

    fun onResetTheme() {
        settingsFlow.update { it.copy(theme = ThemeSettings.Default) }
    }

    // --- Graph Physics ---
    fun onGravityChange(value: Float) {
        settingsFlow.update { it.copy(graphPhysics = it.graphPhysics.copy(options = it.graphPhysics.options.copy(gravity = value))) }
    }

    fun onRepulsionChange(value: Float) {
        settingsFlow.update { it.copy(graphPhysics = it.graphPhysics.copy(options = it.graphPhysics.options.copy(repulsion = value))) }
    }

    fun onSpringChange(value: Float) {
        settingsFlow.update { it.copy(graphPhysics = it.graphPhysics.copy(options = it.graphPhysics.options.copy(spring = value))) }
    }

    fun onDampingChange(value: Float) {
        settingsFlow.update { it.copy(graphPhysics = it.graphPhysics.copy(options = it.graphPhysics.options.copy(damping = value))) }
    }

    fun onBarnesHutThetaChange(value: Float) {
        settingsFlow.update { it.copy(graphPhysics = it.graphPhysics.copy(options = it.graphPhysics.options.copy(barnesHutTheta = value))) }
    }

    fun onToleranceChange(value: Float) {
        settingsFlow.update { it.copy(graphPhysics = it.graphPhysics.copy(options = it.graphPhysics.options.copy(tolerance = value))) }
    }

    fun onResetPhysics() {
        settingsFlow.update { it.copy(graphPhysics = GraphPhysicsSettings.Default) }
    }

    // --- Graph Rendering ---
    fun onStartSimulationOnLoadChange(enabled: Boolean) {
        settingsFlow.update { it.copy(graphRendering = it.graphRendering.copy(startSimulationOnLoad = enabled)) }
    }

    fun onShowNodeLabelsChange(enabled: Boolean) {
        settingsFlow.update { it.copy(graphRendering = it.graphRendering.copy(showNodeLabels = enabled)) }
    }

    fun onShowEdgeLabelsChange(enabled: Boolean) {
        settingsFlow.update { it.copy(graphRendering = it.graphRendering.copy(showEdgeLabels = enabled)) }
    }

    fun onShowCrosshairsChange(enabled: Boolean) {
        settingsFlow.update { it.copy(graphRendering = it.graphRendering.copy(showCrosshairs = enabled)) }
    }

    // --- Graph Interaction ---
    fun onZoomSensitivityChange(value: Float) {
        settingsFlow.update { it.copy(graphInteraction = it.graphInteraction.copy(zoomSensitivity = value)) }
    }

    fun onNodeBaseRadiusChange(value: Float) {
        settingsFlow.update {
            val newOptions = it.graphPhysics.options.copy(nodeBaseRadius = value)
            it.copy(
                graphPhysics = it.graphPhysics.copy(options = newOptions),
                graphInteraction = it.graphInteraction.copy(nodeBaseRadius = value)
            )
        }
    }

    fun onNodeRadiusEdgeFactorChange(value: Float) {
        settingsFlow.update {
            val newOptions = it.graphPhysics.options.copy(nodeRadiusEdgeFactor = value)
            it.copy(
                graphPhysics = it.graphPhysics.copy(options = newOptions),
                graphInteraction = it.graphInteraction.copy(nodeRadiusEdgeFactor = value)
            )
        }
    }

    // --- Data ---
    fun onChangeDefaultDirectory() {
        // This would trigger navigation via the MainViewModel
        // For now, we just log it
        println("Directory change requested")
    }

    fun onAutoLoadLastCodexChange(enabled: Boolean) {
        settingsFlow.update { it.copy(data = it.data.copy(autoLoadLastCodex = enabled)) }
    }

    // ... other data setting handlers ...

    // --- General ---
    fun onStartupScreenChange(screen: String) {
        settingsFlow.update { it.copy(general = it.general.copy(startupScreen = screen)) }
    }

    fun onDefaultCodexViewChange(view: String) {
        settingsFlow.update { it.copy(general = it.general.copy(defaultCodexView = view)) }
    }

    fun onConfirmNodeEdgeDeletionChange(enabled: Boolean) {
        settingsFlow.update { it.copy(general = it.general.copy(confirmNodeEdgeDeletion = enabled)) }
    }

    fun onConfirmSchemaDeletionChange(enabled: Boolean) {
        settingsFlow.update { it.copy(general = it.general.copy(confirmSchemaDeletion = enabled)) }
    }
}