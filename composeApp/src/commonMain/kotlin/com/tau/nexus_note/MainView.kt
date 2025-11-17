package com.tau.nexus_note.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings // ADDED
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
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
import com.tau.nexus_note.settings.SettingsView // ADDED
import com.tau.nexus_note.viewmodels.MainViewModel
import com.tau.nexus_note.viewmodels.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(mainViewModel: MainViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedScreen by mainViewModel.selectedScreen.collectAsState()
    val codexViewModel by mainViewModel.codexViewModel.collectAsState()

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Menu",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Home Item
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Nexus") },
                    label = { Text("Nexus") },
                    selected = selectedScreen == Screen.NEXUS,
                    onClick = {
                        scope.launch { drawerState.close() }
                        // This will close the codex if one is open
                        mainViewModel.closeTerminal()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // Codex Item
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Codex") },
                    label = { Text("Codex") },
                    selected = selectedScreen == Screen.CODEX,
                    onClick = {
                        if (codexViewModel != null) {
                            scope.launch { drawerState.close() }
                            mainViewModel.navigateTo(Screen.CODEX)
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // --- ADDED: Settings Item ---
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedScreen == Screen.SETTINGS,
                    onClick = {
                        scope.launch { drawerState.close() }
                        mainViewModel.navigateTo(Screen.SETTINGS)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = {
                        val title = when (selectedScreen) {
                            Screen.NEXUS -> "Nexus"
                            Screen.CODEX -> "Codex"
                            Screen.SETTINGS -> "Settings" // ADDED
                        }
                        Text(title.toString())
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.apply { if (isClosed) open() else close() }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    // Add actions for the TopAppBar
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
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when (selectedScreen) {
                    Screen.NEXUS -> NexusView(
                        viewModel = mainViewModel
                    )
                    Screen.CODEX -> {
                        val vm = codexViewModel
                        if (vm != null) {
                            CodexView(viewModel = vm)
                        } else {
                            // Fallback in case state is somehow incorrect
                            NexusView(viewModel = mainViewModel)
                        }
                    }
                    // --- ADDED: Settings Screen Case ---
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