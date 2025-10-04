package com.tau.cryptic_ui_v0

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.tau.cryptic_ui_v0.viewmodels.TerminalViewModel
import com.tau.cryptic_ui_v0.views.TerminalView
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        val viewModel = remember { TerminalViewModel(KuzuRepository()) }
        TerminalView(viewModel)

        DisposableEffect(Unit) {
            onDispose {
                viewModel.onCleared()
            }
        }
    }
}