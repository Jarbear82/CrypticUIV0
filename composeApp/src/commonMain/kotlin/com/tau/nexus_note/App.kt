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
            // Create custom scheme
            val p = hexToColor(themeSettings.primaryHex)
            val onP = hexToColor(themeSettings.onPrimaryHex) // We should auto-calculate this
            val s = hexToColor(themeSettings.secondaryHex)
            val onS = hexToColor(themeSettings.onSecondaryHex) // We should auto-calculate this
            val b = hexToColor(themeSettings.backgroundHex)
            val onB = hexToColor(themeSettings.onBackgroundHex) // We should auto-calculate this
            val surf = hexToColor(themeSettings.surfaceHex)
            val onSurf = hexToColor(themeSettings.onSurfaceHex) // We should auto-calculate this

            // This is a simplified scheme. A full one would need all ...Variant colors.
            if (useDarkTheme) {
                darkColorScheme(
                    primary = p,
                    onPrimary = onP,
                    secondary = s,
                    onSecondary = onS,
                    background = b,
                    onBackground = onB,
                    surface = surf,
                    onSurface = onSurf
                )
            } else {
                lightColorScheme(
                    primary = p,
                    onPrimary = onP,
                    secondary = s,
                    onSecondary = onS,
                    background = b,
                    onBackground = onB,
                    surface = surf,
                    onSurface = onSurf
                )
            }
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