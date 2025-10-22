package com.tau.cryptic_ui_v0.viewmodels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tau.cryptic_ui_v0.KuzuDBService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Screen {
    HOME,
    TERMINAL
}

class MainViewModel {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val _selectedScreen = MutableStateFlow(Screen.HOME)
    val selectedScreen = _selectedScreen.asStateFlow()

    private val _terminalViewModel = MutableStateFlow<TerminalViewModel?>(null)
    val terminalViewModel = _terminalViewModel.asStateFlow()

    fun navigateTo(screen: Screen) {
        _selectedScreen.value = screen
    }

    fun openTerminal() {
        if (_terminalViewModel.value == null) {
            _terminalViewModel.value = TerminalViewModel(KuzuDBService())
        }
        _selectedScreen.value = Screen.TERMINAL
    }

    fun closeTerminal() {
        viewModelScope.launch {
            _terminalViewModel.value?.onCleared()
            _terminalViewModel.value = null
            _selectedScreen.value = Screen.HOME
        }
    }

    fun onDispose() {
        // This will be called from the main App composable's onDispose
        _terminalViewModel.value?.onCleared()
    }
}

// Helper for remembering the ViewModel
@Composable
fun rememberMainViewModel(): MainViewModel {
    return remember { MainViewModel() }
}