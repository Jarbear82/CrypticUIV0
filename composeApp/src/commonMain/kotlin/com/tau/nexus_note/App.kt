package com.tau.nexus_note

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tau.nexus_note.ui.theme.NexusNoteTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    // Create the MainViewModel which controls navigation and settings
    val mainViewModel = rememberMainViewModel()

    // Observe settings to pass into the theme
    val settings by mainViewModel.appSettings.collectAsState()

    // Apply theme
    NexusNoteTheme(settings = settings.theme) {
        MainView(mainViewModel)

        // cleanup hook
        DisposableEffect(Unit) {
            onDispose {
                mainViewModel.onDispose()
            }
        }
    }
}