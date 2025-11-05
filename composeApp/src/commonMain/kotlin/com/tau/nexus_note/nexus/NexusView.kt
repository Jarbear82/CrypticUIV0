package com.tau.nexus_note.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.utils.DirectoryPicker
import com.tau.nexus_note.viewmodels.MainViewModel
import com.tau.nexus_note.utils.labelToColor

@Composable
fun HomeView(viewModel: MainViewModel) {
    val codeices by viewModel.codicies.collectAsState()
    val baseDirectory by viewModel.codexBaseDirectory.collectAsState()
    val showNameDialog by viewModel.showNameDialog.collectAsState()
    val showBaseDirPicker by viewModel.showBaseDirPicker.collectAsState()

    // --- Dialogs ---

    DirectoryPicker(
        show = showBaseDirPicker,
        title = "Select Codex Storage Directory",
        initialDirectory = baseDirectory,
        onResult = { viewModel.onBaseDirectorySelected(it) }
    )

    if (showNameDialog) {
        CreateCodexDialog(
            onConfirm = { viewModel.onCodexNameEntered(it) },
            onDismiss = { viewModel.onCodexNameCancelled() }
        )
    }

    // --- Main UI ---

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(
            "Welcome to Nexus Note",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(32.dp))

        // --- Database List ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Codicies in: $baseDirectory", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { viewModel.onChangeBaseDirectoryClicked() }) {
                Text("Change")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (codeices.isEmpty()) {
                item {
                    Text(
                        "No codicies found in this directory.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(codeices) { graph ->
                val colorInfo = labelToColor(graph.path)
                ListItem(
                    headlineContent = { Text(graph.name) },
                    supportingContent = { Text(graph.path) },
                    modifier = Modifier.clickable { viewModel.openCodex(graph) },
                    colors = ListItemDefaults.colors(
                        containerColor = colorInfo.composeColor,
                        headlineColor = colorInfo.composeFontColor,
                        supportingColor = colorInfo.composeFontColor,
                        leadingIconColor = colorInfo.composeFontColor,
                        trailingIconColor = colorInfo.composeFontColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Actions ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { viewModel.onCreateNewCodexClicked() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Create New Codex")
            }
            Button(
                onClick = { viewModel.openInMemoryTerminal() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Open In-Memory Terminal")
            }
        }
    }
}