package com.tau.nexus_note

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.tau.nexus_note.settings.ThemeMode
import com.tau.nexus_note.utils.hexToColor
import com.tau.nexus_note.utils.labelToColor
import com.tau.nexus_note.viewmodels.rememberMainViewModel
import com.tau.nexus_note.views.MainView
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    // Create the MainViewModel which now controls navigation and settings
    val mainViewModel = rememberMainViewModel()

    // --- ADDED: Observe settings from the ViewModel ---
    val settings by mainViewModel.appSettings.collectAsState()
    val themeSettings = settings.theme

    // Determine if dark mode is active
    val isDark by remember(themeSettings.themeMode) {
        mutableStateOf(
            when (themeSettings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> null // Will be resolved by isSystemInDarkTheme
            }
        )
    }

    // --- THIS IS THE FIX ---
    val useDarkTheme = when (isDark) {
        true -> true
        false -> false
        null -> isSystemInDarkTheme()
    }
    // --- END FIX ---

    // Determine the ColorScheme
    val colorScheme = remember(themeSettings, useDarkTheme) {
        if (themeSettings.useCustomTheme) {
            // --- UPDATED LOGIC ---
            // 1. Parse the new SEED colors from settings
            val primarySeed = hexToColor(themeSettings.primarySeedHex)
            val secondarySeed = hexToColor(themeSettings.secondarySeedHex)
            val tertiarySeed = hexToColor(themeSettings.tertiarySeedHex)
            val errorSeed = hexToColor(themeSettings.errorSeedHex)

            // 2. Let Material 3 generate the full palette from the seeds
            if (useDarkTheme) {
                darkColorScheme(
                    primary = primarySeed,
                    secondary = secondarySeed,
                    tertiary = tertiarySeed,
                    error = errorSeed
                    // All other colors (background, surface, onPrimary, etc.)
                    // are automatically generated from these seeds.
                )
            } else {
                lightColorScheme(
                    primary = primarySeed,
                    secondary = secondarySeed,
                    tertiary = tertiarySeed,
                    error = errorSeed
                )
            }
            // --- END UPDATED LOGIC ---
        } else {
            // Use standard light/dark scheme
            if (useDarkTheme) darkColorScheme() else lightColorScheme()
        }
    }

    // --- MODIFIED: Apply the dynamic ColorScheme ---
    MaterialTheme(colorScheme = colorScheme) {
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