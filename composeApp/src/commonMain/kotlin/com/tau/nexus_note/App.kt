package com.tau.nexus_note

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tau.nexus_note.settings.ThemeMode
import com.tau.nexus_note.utils.getFontColor
import com.tau.nexus_note.utils.hexToColor
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    // Create the MainViewModel which now controls navigation and settings
    val mainViewModel = rememberMainViewModel()

    // Observe settings from the ViewModel
    val settings by mainViewModel.appSettings.collectAsState()
    val themeSettings = settings.theme

    // --- UPDATED: Helper function to get contrast color ---
    fun getOnColor(baseColor: Color): Color {
        val colorInt = baseColor.toArgb()
        val r = (colorInt shr 16) and 0xFF
        val g = (colorInt shr 8) and 0xFF
        val b = colorInt and 0xFF
        val hex = getFontColor(intArrayOf(r, g, b))
        return hexToColor(hex)
    }

    // --- UPDATED: New Theme Logic ---
    val systemIsDark = isSystemInDarkTheme() // Call composable function here
    val colorScheme = remember(themeSettings, systemIsDark) { // Use the value as a key
        val accentColor = Color(themeSettings.accentColor)
        val onAccentColor = getOnColor(accentColor)
        // Use a simple derivation for containers
        val accentContainerColor = accentColor.copy(alpha = 0.3f)
        val onAccentContainerColor = getOnColor(accentContainerColor)

        // 1. Determine the base background and dark mode status
        val (useDarkTheme, backgroundColor) = when (themeSettings.themeMode) {
            ThemeMode.LIGHT -> false to Color.White
            ThemeMode.DARK -> true to Color.Black
            ThemeMode.SYSTEM -> systemIsDark to if (systemIsDark) Color.Black else Color.White // Use the variable
            ThemeMode.CUSTOM -> {
                val customBg = Color(themeSettings.customBackgroundColor)
                // Determine if custom background is "dark" based on luminance
                val isDark = getOnColor(customBg) == Color.White
                isDark to customBg
            }
        }

        // 2. Get the base scheme (light or dark)
        val baseScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme()

        // 3. Apply overrides
        baseScheme.copy(
            primary = accentColor,
            onPrimary = onAccentColor,
            primaryContainer = accentContainerColor,
            onPrimaryContainer = onAccentContainerColor,

            // Also apply to secondary to ensure buttons, etc., are colored
            secondary = accentColor,
            onSecondary = onAccentColor,
            secondaryContainer = accentContainerColor,
            onSecondaryContainer = onAccentContainerColor,

            // Apply to tertiary as well for good measure
            tertiary = accentColor,
            onTertiary = onAccentColor,
            tertiaryContainer = accentContainerColor,
            onTertiaryContainer = onAccentContainerColor,

            // Apply background
            background = backgroundColor,
            onBackground = getOnColor(backgroundColor),

            // Make surface match background for simplicity
            surface = backgroundColor,
            onSurface = getOnColor(backgroundColor)

            // Other colors like error, outlines, etc.,
            // will fall back to the Material defaults from baseScheme.
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        MainView(mainViewModel)

        DisposableEffect(Unit) {
            onDispose {
                mainViewModel.onDispose()
            }
        }
    }
}