package com.tau.nexus_note

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.tau.nexus_note.viewmodels.rememberMainViewModel
import com.tau.nexus_note.views.MainView
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        // Create the MainViewModel which now controls navigation
        val mainViewModel = rememberMainViewModel()
        // val scope = rememberCoroutineScope()

        // Show the MainView, which contains the nav drawer and screen logic
        MainView(mainViewModel)

        DisposableEffect(Unit) {
            onDispose {
                // Dispose the MainViewModel, which in turn disposes the CodexViewModel
                mainViewModel.onDispose()
            }
        }
    }
}