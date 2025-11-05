package com.tau.nexus_note.viewmodels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tau.nexus_note.datamodels.CodexItem
import com.tau.nexus_note.SqliteDbService
import com.tau.nexus_note.utils.getFileName
import com.tau.nexus_note.utils.getHomeDirectoryPath
import com.tau.nexus_note.utils.listFilesWithExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen {
    HOME,
    CODEX
}

class MainViewModel {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    private val _selectedScreen = MutableStateFlow(Screen.HOME)
    val selectedScreen = _selectedScreen.asStateFlow()

    private val _codexViewModel = MutableStateFlow<CodexViewModel?>(null)
    val codexViewModel = _codexViewModel.asStateFlow()

    // --- Codex Management State ---

    private val _codexBaseDirectory = MutableStateFlow(getHomeDirectoryPath())
    val codexBaseDirectory = _codexBaseDirectory.asStateFlow()

    private val _codicies = MutableStateFlow<List<CodexItem>>(emptyList())
    val codicies = _codicies.asStateFlow()

    private val _showBaseDirPicker = MutableStateFlow(false)
    val showBaseDirPicker = _showBaseDirPicker.asStateFlow()

    private val _showNameDialog = MutableStateFlow(false)
    val showNameDialog = _showNameDialog.asStateFlow()

    init {
        loadCodicies()
    }

    /**
     * Scans the base directory for valid SQLiteDB files (.sqlite).
     */
    fun loadCodicies() {
        viewModelScope.launch(Dispatchers.IO) { // Use IO for file scanning
            // UPDATED: Look for .sqlite files
            val files = listFilesWithExtension(_codexBaseDirectory.value, ".sqlite")
            val graphs = files.map {
                CodexItem(getFileName(it), it)
            }

            viewModelScope.launch(Dispatchers.Main) { // Switch back to Main to update state
                _codicies.value = graphs
            }
        }
    }

    /**
     * Opens the directory picker to change the base storage directory.
     */
    fun onChangeBaseDirectoryClicked() {
        _showBaseDirPicker.value = true
    }

    /**
     * Callback for when the user selects a new base directory.
     */
    fun onBaseDirectorySelected(path: String?) {
        _showBaseDirPicker.value = false
        path?.let {
            _codexBaseDirectory.value = it
            loadCodicies()
        }
    }

    /**
     * Shows the dialog to name a new codex.
     */
    fun onCreateNewCodexClicked() {
        _showNameDialog.value = true
    }

    /**
     * Callback when the user confirms a name for a new codex.
     * This creates and opens the new database.
     */
    fun onCodexNameEntered(name: String) {
        _showNameDialog.value = false
        if (name.isBlank()) return

        // Sanitize the name and ENSURE it ends with .sqlite
        val dbName = name.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        // UPDATED: Use .sqlite
        val finalName = if (dbName.endsWith(".sqlite")) dbName else "$dbName.sqlite"

        val newPath = "${_codexBaseDirectory.value}/$finalName"
        val newItem = CodexItem(finalName, newPath)

        // Open the graph (which initializes the DB file)
        openCodex(newItem)

        // Add to the list
        _codicies.update { (it + newItem).distinctBy { it.path } }
    }

    /**
     * Callback when the user cancels the "create name" dialog.
     */
    fun onCodexNameCancelled() {
        _showNameDialog.value = false
    }

    /**
     * Opens a terminal session for a specific on-disk codex.
     */
    fun openCodex(item: CodexItem) {
        viewModelScope.launch {
            try {
                _codexViewModel.value?.onCleared() // Close previous one
                // UPDATED: Use new SqliteDbService
                val newService = SqliteDbService()
                newService.initialize(item.path) // Initialize with file path
                _codexViewModel.value = CodexViewModel(newService)
                _selectedScreen.value = Screen.CODEX
            } catch (e: Exception) {
                println("Failed to open codex '${item.path}': ${e.message}")
                // TODO: Add a user-facing error message here
            }
        }
    }

    // --- End Codex Management ---

    fun navigateTo(screen: Screen) {
        _selectedScreen.value = screen
    }

    /**
     * Opens a terminal session for an in-memory database.
     */
    fun openInMemoryTerminal() {
        viewModelScope.launch {
            _codexViewModel.value?.onCleared()
            // UPDATED: Use new SqliteDbService
            val newService = SqliteDbService()
            // UPDATED: Pass special string for in-memory DB
            newService.initialize(":memory:")
            _codexViewModel.value = CodexViewModel(newService)
            _selectedScreen.value = Screen.CODEX
        }
    }

    fun closeTerminal() {
        viewModelScope.launch {
            _codexViewModel.value?.onCleared()
            _codexViewModel.value = null
            _selectedScreen.value = Screen.HOME
            // Refresh the list in case a new DB was created
            loadCodicies()
        }
    }

    fun onDispose() {
        // This will be called from the main App composable's onDispose
        _codexViewModel.value?.onCleared()
    }
}

// Helper for remembering the ViewModel
@Composable
fun rememberMainViewModel(): MainViewModel {
    return remember { MainViewModel() }
}