package com.tau.nexus_note.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.codex.CodexView
import com.tau.nexus_note.nexus.NexusView
import com.tau.nexus_note.settings.SettingsView
import com.tau.nexus_note.viewmodels.MainViewModel
import com.tau.nexus_note.viewmodels.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(mainViewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedScreen by mainViewModel.selectedScreen.collectAsState()
    val codexViewModel by mainViewModel.codexViewModel.collectAsState()
    // Observe the currently opened item for the label
    val openedCodexItem by mainViewModel.openedCodexItem.collectAsState()

    // --- Error Handling Observers ---
    val mainError by mainViewModel.errorFlow.collectAsState()
    val codexError by codexViewModel?.errorFlow?.collectAsState() ?: mutableStateOf<String?>(null)

    LaunchedEffect(mainError) {
        mainError?.let {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = it,
                    withDismissAction = true
                )
            }
            mainViewModel.clearError()
        }
    }

    LaunchedEffect(codexError) {
        codexError?.let {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = it,
                    withDismissAction = true
                )
            }
            codexViewModel?.clearError()
        }
    }
    // --- End Error Handling ---

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    val title = when (selectedScreen) {
                        Screen.NEXUS -> "Nexus"
                        Screen.CODEX -> "Codex"
                        Screen.SETTINGS -> "Settings"
                    }
                    Text(title.toString())
                },
                actions = {
                    // Show close button only when in terminal view
                    if (selectedScreen == Screen.CODEX) {
                        IconButton(onClick = {
                            mainViewModel.closeTerminal()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Close Codex"
                            )
                        }
                    }
                }
            )
        }
    ) { contentPadding ->
        // Use a Row to place the NavigationRail and the Content side-by-side
        Row(modifier = Modifier.padding(contentPadding).fillMaxSize()) {

            NavigationRail {
                Spacer(Modifier.height(12.dp))

                // Home Item
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Nexus") },
                    label = { Text("Nexus") },
                    selected = selectedScreen == Screen.NEXUS,
                    onClick = {
                        mainViewModel.closeTerminal()
                    }
                )

                // Codex Item
                val isCodexLoaded = codexViewModel != null
                // Use the opened codex name, or fallback to "Codex"
                val codexLabel = openedCodexItem?.name ?: "Codex"

                NavigationRailItem(
                    icon = { Icon(Icons.Default.Storage, contentDescription = "Codex") },
                    label = { Text(codexLabel) },
                    selected = selectedScreen == Screen.CODEX,
                    enabled = isCodexLoaded, // Disable if no codex loaded
                    onClick = {
                        if (isCodexLoaded) {
                            mainViewModel.navigateTo(Screen.CODEX)
                        }
                    }
                )

                // Settings Item
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedScreen == Screen.SETTINGS,
                    onClick = {
                        mainViewModel.navigateTo(Screen.SETTINGS)
                    }
                )
            }

            // Content Area
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedScreen) {
                    Screen.NEXUS -> NexusView(
                        viewModel = mainViewModel
                    )
                    Screen.CODEX -> {
                        val vm = codexViewModel
                        if (vm != null) {
                            CodexView(viewModel = vm)
                        } else {
                            NexusView(viewModel = mainViewModel)
                        }
                    }
                    Screen.SETTINGS -> {
                        SettingsView(
                            viewModel = mainViewModel.settingsViewModel
                        )
                    }
                }
            }
        }
    }
}