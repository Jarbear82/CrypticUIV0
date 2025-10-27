package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GraphNavigationUI(
    modifier: Modifier = Modifier,
    options: NavigationOptions,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (options.showZoomButtons) {
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.Black)
            }
            IconButton(onClick = onZoomOut) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.Black)
            }
        }
        if (options.showFitButton) {
            IconButton(onClick = onFit) {
                Icon(Icons.Default.FitScreen, contentDescription = "Fit to Screen", tint = Color.Black)
            }
        }
    }
}