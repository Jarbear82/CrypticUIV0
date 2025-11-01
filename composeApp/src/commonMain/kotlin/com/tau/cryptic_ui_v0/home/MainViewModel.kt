package com.tau.cryptic_ui_v0.viewmodels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tau.cryptic_ui_v0.NoteGraphItem
import com.tau.cryptic_ui_v0.SqliteDbService
import com.tau.cryptic_ui_v0.utils.fileExists
import com.tau.cryptic_ui_v0.utils.getFileName
import com.tau.cryptic_ui_v0.utils.getHomeDirectoryPath
import com.tau.cryptic_ui_v0.utils.listFilesWithExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    // --- Notegraph Management State ---

    private val _noteGraphBaseDirectory = MutableStateFlow(getHomeDirectoryPath())
    val noteGraphBaseDirectory = _noteGraphBaseDirectory.asStateFlow()

    private val _noteGraphs = MutableStateFlow<List<NoteGraphItem>>(emptyList())
    val noteGraphs = _noteGraphs.asStateFlow()

    private val _showBaseDirPicker = MutableStateFlow(false)
    val showBaseDirPicker = _showBaseDirPicker.asStateFlow()

    private val _showNameDialog = MutableStateFlow(false)
    val showNameDialog = _showNameDialog.asStateFlow()

    init {
        loadNoteGraphs()
    }

    /**
     * Scans the base directory for valid SQLiteDB files (.sqlite).
     */
    fun loadNoteGraphs() {
        viewModelScope.launch(Dispatchers.IO) { // Use IO for file scanning
            // UPDATED: Look for .sqlite files
            val files = listFilesWithExtension(_noteGraphBaseDirectory.value, ".sqlite")
            val graphs = files.map {
                NoteGraphItem(getFileName(it), it)
            }

            viewModelScope.launch(Dispatchers.Main) { // Switch back to Main to update state
                _noteGraphs.value = graphs
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
            _noteGraphBaseDirectory.value = it
            loadNoteGraphs()
        }
    }

    /**
     * Shows the dialog to name a new notegraph.
     */
    fun onCreateNewNoteGraphClicked() {
        _showNameDialog.value = true
    }

    /**
     * Callback when the user confirms a name for a new notegraph.
     * This creates and opens the new database.
     */
    fun onNoteGraphNameEntered(name: String) {
        _showNameDialog.value = false
        if (name.isBlank()) return

        // Sanitize the name and ENSURE it ends with .sqlite
        val dbName = name.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        // UPDATED: Use .sqlite
        val finalName = if (dbName.endsWith(".sqlite")) dbName else "$dbName.sqlite"

        val newPath = "${_noteGraphBaseDirectory.value}/$finalName"
        val newItem = NoteGraphItem(finalName, newPath)

        // Open the graph (which initializes the DB file)
        openNoteGraph(newItem)

        // Add to the list
        _noteGraphs.update { (it + newItem).distinctBy { it.path } }
    }

    /**
     * Callback when the user cancels the "create name" dialog.
     */
    fun onNoteGraphNameCancelled() {
        _showNameDialog.value = false
    }

    /**
     * Opens a terminal session for a specific on-disk notegraph.
     */
    fun openNoteGraph(item: NoteGraphItem) {
        viewModelScope.launch {
            try {
                _terminalViewModel.value?.onCleared() // Close previous one
                // UPDATED: Use new SqliteDbService
                val newService = SqliteDbService()
                newService.initialize(item.path) // Initialize with file path
                _terminalViewModel.value = TerminalViewModel(newService)
                _selectedScreen.value = Screen.TERMINAL
            } catch (e: Exception) {
                println("Failed to open notegraph '${item.path}': ${e.message}")
                // TODO: Add a user-facing error message here
            }
        }
    }

    // --- End Notegraph Management ---

    fun navigateTo(screen: Screen) {
        _selectedScreen.value = screen
    }

    /**
     * Opens a terminal session for an in-memory database.
     */
    fun openInMemoryTerminal() {
        viewModelScope.launch {
            _terminalViewModel.value?.onCleared()
            // UPDATED: Use new SqliteDbService
            val newService = SqliteDbService()
            // UPDATED: Pass special string for in-memory DB
            newService.initialize(":memory:")
            _terminalViewModel.value = TerminalViewModel(newService)
            _selectedScreen.value = Screen.TERMINAL
        }
    }

    fun closeTerminal() {
        viewModelScope.launch {
            _terminalViewModel.value?.onCleared()
            _terminalViewModel.value = null
            _selectedScreen.value = Screen.HOME
            // Refresh the list in case a new DB was created
            loadNoteGraphs()
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