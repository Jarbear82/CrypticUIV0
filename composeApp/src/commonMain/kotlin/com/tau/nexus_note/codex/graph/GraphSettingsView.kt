package com.tau.nexus_note.codex.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.nexus_note.codex.graph.physics.PhysicsOptions
import kotlin.math.roundToInt

@Composable
fun GraphSettingsView(
    options: PhysicsOptions,
    onGravityChange: (Float) -> Unit,
    onRepulsionChange: (Float) -> Unit,
    onSpringChange: (Float) -> Unit,
    onDampingChange: (Float) -> Unit,
    onBarnesHutThetaChange: (Float) -> Unit,
    onToleranceChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(300.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Graph Physics", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            // Gravity
            SettingSlider(
                label = "Gravity",
                value = options.gravity,
                onValueChange = onGravityChange,
                range = 0f..2f
            )

            // Repulsion
            SettingSlider(
                label = "Repulsion",
                value = options.repulsion,
                onValueChange = onRepulsionChange,
                range = 0f..10000f
            )

            // Spring
            SettingSlider(
                label = "Spring",
                value = options.spring,
                onValueChange = onSpringChange,
                range = 0.01f..1f
            )

            // Damping
            SettingSlider(
                label = "Damping",
                value = options.damping,
                onValueChange = onDampingChange,
                range = 0.5f..1f
            )

            // Barnes-Hut Theta
            SettingSlider(
                label = "Barnes-Hut Theta",
                value = options.barnesHutTheta,
                onValueChange = onBarnesHutThetaChange,
                range = 0.1f..3f
            )

            // Tolerance
            SettingSlider(
                label = "Tolerance (Speed)",
                value = options.tolerance,
                onValueChange = onToleranceChange,
                range = 0.1f..10f
            )
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp)
            Text(
                // Format to 2 decimal places, or 0 if it's a large number
                text = if (value > 100) value.roundToInt().toString() else String.format("%.2f", value),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
}
