package com.tau.cryptic_ui_v0

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val viewModel = remember { TerminalViewModel() }
        TerminalView(viewModel)

        DisposableEffect(Unit) {
            onDispose {
                viewModel.onCleared()
            }
        }
    }
}