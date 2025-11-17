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
import androidx.compose.ui.graphics.Color
import com.tau.nexus_note.settings.ThemeMode
import com.tau.nexus_note.utils.getFontColor // ADDED IMPORT
import com.tau.nexus_note.utils.hexToColor // ADDED IMPORT
import com.tau.nexus_note.utils.labelToColor
import com.tau.nexus_note.viewmodels.rememberMainViewModel
import com.tau.nexus_note.views.MainView
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    // Create the MainViewModel which now controls navigation and settings
    val mainViewModel = rememberMainViewModel()

    // Observe settings from the ViewModel
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

    val useDarkTheme = when (isDark) {
        true -> true
        false -> false
        null -> isSystemInDarkTheme()
    }

    // Determine the ColorScheme
    val colorScheme = remember(themeSettings, useDarkTheme) {
        if (themeSettings.useCustomTheme) {
            // Convert the Long value directly to a Color
            val primarySeed = Color(themeSettings.primarySeedValue.toInt())
            val secondarySeed = Color(themeSettings.secondarySeedValue.toInt())
            val tertiarySeed = Color(themeSettings.tertiarySeedValue.toInt())
            val errorSeed = Color(themeSettings.errorSeedValue.toInt())

            // 1. Convert Compose Color (0.0f-1.0f) to RGB IntArray (0-255)
            val primarySeedRgb = intArrayOf(
                (primarySeed.red * 255).toInt(),
                (primarySeed.green * 255).toInt(),
                (primarySeed.blue * 255).toInt()
            )
            // 2. Get the contrast color string ("#FFFFFF" or "#000000")
            val onPrimaryColorString = getFontColor(primarySeedRgb)
            // 3. Convert that string back into a Compose Color
            val onPrimaryColor = hexToColor(onPrimaryColorString)


            // Provide clean, distinct colors for background and panels.
            if (useDarkTheme) {
                darkColorScheme(
                    primary = primarySeed,
                    secondary = secondarySeed,
                    tertiary = tertiarySeed,
                    error = errorSeed,

                    // Fix the FAB
                    primaryContainer = primarySeed,
                    onPrimaryContainer = onPrimaryColor, // Use the dynamic color

                    // Fix the main background (standard dark)
                    background = Color(0xFF121212),
                    onBackground = Color.White,
                    surface = Color(0xFF121212), // Main canvas
                    onSurface = Color.White,

                    // Fix the right panel (distinct lighter grey)
                    surfaceVariant = Color(0xFF1E1E1E),
                    onSurfaceVariant = Color(0xFFCACACA)
                )
            } else {
                lightColorScheme(
                    primary = primarySeed,
                    secondary = secondarySeed,
                    tertiary = tertiarySeed,
                    error = errorSeed,

                    // Fix the FAB
                    primaryContainer = primarySeed,
                    onPrimaryContainer = onPrimaryColor, // Use the dynamic color

                    // Fix the main background (pure white)
                    background = Color.White,
                    onBackground = Color.Black,
                    surface = Color.White, // Main canvas
                    onSurface = Color.Black,

                    // Fix the right panel (distinct light grey)
                    surfaceVariant = Color(0xFFF0F0F0),
                    onSurfaceVariant = Color.Black
                )
            }

        } else {
            // Use standard light/dark scheme
            if (useDarkTheme) darkColorScheme() else lightColorScheme()
        }
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