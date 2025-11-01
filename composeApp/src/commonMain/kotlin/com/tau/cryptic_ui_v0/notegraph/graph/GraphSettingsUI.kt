package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// --- ADDED: Import for RepulsionOptions ---
import com.tau.cryptic_ui_v0.notegraph.graph.physics.RepulsionOptions
// ---
import com.tau.cryptic_ui_v0.notegraph.graph.physics.BarnesHutOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.ForceAtlas2Options
import com.tau.cryptic_ui_v0.notegraph.graph.physics.PhysicsOptions
import com.tau.cryptic_ui_v0.notegraph.graph.physics.SolverType
import kotlin.math.roundToInt

// Opt-in for Experimental Material 3 API
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphSettingsUI(
    options: PhysicsOptions,
    onOptionsChange: (PhysicsOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) } // This state seems unused now, can remove if not needed elsewhere

    Card(
        modifier = modifier
            .padding(16.dp)
            .width(300.dp)
            .border(1.dp, Color.Black, MaterialTheme.shapes.medium),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Physics Options", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

            // Solver Selection
            var solverMenuExpanded by remember { mutableStateOf(false) }

            // Use ExposedDropdownMenuBox for better dropdown behavior with OutlinedTextField
            ExposedDropdownMenuBox(
                expanded = solverMenuExpanded,
                onExpandedChange = { solverMenuExpanded = !solverMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = options.solver.name,
                    onValueChange = {}, // No change needed here
                    readOnly = true,
                    label = { Text("Solver") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = solverMenuExpanded
                            // onIconClick is implicitly handled by ExposedDropdownMenuBox's onExpandedChange
                        )
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor() // Important for anchoring the dropdown
                        .fillMaxWidth()

                )
                ExposedDropdownMenu(
                    expanded = solverMenuExpanded,
                    onDismissRequest = { solverMenuExpanded = false }
                ) {
                    // --- UPDATED: Use SolverType.entries ---
                    SolverType.entries.forEach { solver ->
                        DropdownMenuItem(
                            text = { Text(solver.name) },
                            onClick = {
                                onOptionsChange(options.copy(solver = solver))
                                solverMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // General Physics
            SettingsSlider(
                label = "Damping",
                value = options.damping,
                onValueChange = { onOptionsChange(options.copy(damping = it)) },
                range = 0f..1f,
                steps = 100
            )
            SettingsSlider(
                label = "Central Gravity",
                value = options.centralGravity,
                onValueChange = { onOptionsChange(options.copy(centralGravity = it)) },
                range = 0f..2f,
                steps = 100
            )
            SettingsSlider(
                label = "Spring Length",
                value = options.defaultSpringLength,
                onValueChange = { onOptionsChange(options.copy(defaultSpringLength = it)) },
                range = 10f..500f,
                steps = 49
            )
            SettingsSlider(
                label = "Spring Constant",
                value = options.defaultSpringConstant,
                onValueChange = { onOptionsChange(options.copy(defaultSpringConstant = it)) },
                range = 0.001f..0.2f,
                steps = 100
            )

            Divider(Modifier.padding(vertical = 16.dp))

            // Solver-Specific Options
            when (options.solver) {
                SolverType.BARNES_HUT -> {
                    Text("Barnes-Hut Options", style = MaterialTheme.typography.titleSmall)
                    SettingsSlider(
                        label = "Gravitational Const",
                        value = options.barnesHut.gravitationalConstant.toFloat(),
                        onValueChange = {
                            onOptionsChange(options.copy(barnesHut = options.barnesHut.copy(gravitationalConstant = it.toDouble())))
                        },
                        range = -20000f..-100f,
                        steps = 100
                    )
                    SettingsSlider(
                        label = "Avoid Overlap",
                        // --- UPDATED: Removed safe call based on non-nullable type ---
                        value = options.barnesHut.avoidOverlap.toFloat(),
                        onValueChange = {
                            onOptionsChange(options.copy(barnesHut = options.barnesHut.copy(avoidOverlap = it.toDouble())))
                        },
                        range = 0f..1f,
                        steps = 20
                    )
                }
                SolverType.FORCE_ATLAS_2 -> {
                    Text("ForceAtlas2 Options", style = MaterialTheme.typography.titleSmall)
                    SettingsSlider(
                        label = "Gravitational Const",
                        value = options.forceAtlas.gravitationalConstant.toFloat(),
                        onValueChange = {
                            onOptionsChange(options.copy(forceAtlas = options.forceAtlas.copy(gravitationalConstant = it.toDouble())))
                        },
                        range = -1000f..0f,
                        steps = 100
                    )
                    SettingsSlider(
                        label = "Avoid Overlap",
                        // --- UPDATED: Removed safe call based on non-nullable type ---
                        value = options.forceAtlas.avoidOverlap.toFloat(),
                        onValueChange = {
                            onOptionsChange(options.copy(forceAtlas = options.forceAtlas.copy(avoidOverlap = it.toDouble())))
                        },
                        range = 0f..1f,
                        steps = 20
                    )
                }
                SolverType.REPEL -> {
                    Text("Repel Options", style = MaterialTheme.typography.titleSmall)
                    // --- FIX: This slider now correctly controls nodeDistance in RepulsionOptions ---
                    SettingsSlider(
                        label = "Node Distance", // Changed label
                        value = options.repulsion.nodeDistance.toFloat(), // Changed value source
                        onValueChange = {
                            // Changed logic to update the nested RepulsionOptions
                            onOptionsChange(options.copy(repulsion = options.repulsion.copy(nodeDistance = it.toDouble())))
                        },
                        range = 50f..500f, // Changed range to be appropriate for a distance
                        steps = 45 // Matched hierarchical steps
                    )
                    // --- END FIX ---
                }
                SolverType.HIERARCHICAL -> {
                    Text("Hierarchical Options", style = MaterialTheme.typography.titleSmall)
                    SettingsSlider(
                        label = "Node Distance",
                        value = options.hierarchicalRepulsion.nodeDistance.toFloat(),
                        onValueChange = {
                            onOptionsChange(options.copy(hierarchicalRepulsion = options.hierarchicalRepulsion.copy(nodeDistance = it.toDouble())))
                        },
                        range = 50f..500f,
                        steps = 45
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 14.sp)
            Text(
                text = "%.2f".format(value),
                fontSize = 14.sp,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}