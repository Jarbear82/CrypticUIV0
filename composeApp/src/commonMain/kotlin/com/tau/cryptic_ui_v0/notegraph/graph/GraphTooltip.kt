package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.tau.cryptic_ui_v0.NodeDisplayItem
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp

@Composable
fun GraphTooltip(
    node: NodeDisplayItem,
    position: Offset,
    options: TooltipOptions,
    constraints: Constraints // Screen constraints
) {
    val tooltipText = "${node.label}\n(${node.primarykeyProperty.value})"

    // Use layout modifier to position the tooltip, ensuring it stays on screen
    Box(
        modifier = Modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())

                // Position logic
                var x = (position.x - placeable.width / 2f).roundToInt()
                var y = (position.y - placeable.height - 20f).roundToInt() // 20px above pointer

                // Constrain to screen bounds
                if (x < 0) x = 0
                if (y < 0) y = 0
                if (x + placeable.width > constraints.maxWidth) {
                    x = constraints.maxWidth - placeable.width
                }
                if (y + placeable.height > constraints.maxHeight) {
                    y = constraints.maxHeight - placeable.height
                }

                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(x, y)
                }
            }
            .clip(RoundedCornerShape(4.dp))
            .background(options.backgroundColor)
            .padding(options.padding)
    ) {
        Text(
            text = tooltipText,
            color = options.textColor,
            fontSize = options.fontSizeSp
        )
    }
}