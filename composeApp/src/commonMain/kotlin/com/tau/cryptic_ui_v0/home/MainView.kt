package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.viewmodels.MainViewModel
import com.tau.cryptic_ui_v0.viewmodels.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(mainViewModel: MainViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val selectedScreen by mainViewModel.selectedScreen.collectAsState()
    val terminalViewModel by mainViewModel.terminalViewModel.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Menu",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Home Item
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = selectedScreen == Screen.HOME,
                    onClick = {
                        scope.launch { drawerState.close() }
                        mainViewModel.navigateTo(Screen.HOME)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // Terminal Item
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Terminal") },
                    label = { Text("Terminal") },
                    selected = selectedScreen == Screen.TERMINAL,
                    enabled = terminalViewModel != null,
                    onClick = {
                        scope.launch { drawerState.close() }
                        mainViewModel.navigateTo(Screen.TERMINAL)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // You can add other items here later (e.g., Settings, Import/Export)
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val title = when (selectedScreen) {
                            Screen.HOME -> "Cryptic UI"
                            Screen.TERMINAL -> terminalViewModel?.metadataViewModel?.dbMetaData?.collectAsState()?.value?.name ?: "Terminal"
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
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when (selectedScreen) {
                    Screen.HOME -> HomeView(
                        onOpenTerminal = { mainViewModel.openTerminal() }
                    )
                    Screen.TERMINAL -> {
                        val vm = terminalViewModel
                        if (vm != null) {
                            TerminalView(viewModel = vm)
                        } else {
                            // Fallback in case state is somehow incorrect
                            HomeView(onOpenTerminal = { mainViewModel.openTerminal() })
                        }
                    }
                }
            }
        }
    }
}