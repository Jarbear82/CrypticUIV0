package com.tau.nexus_note.nexus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.utils.DirectoryPicker
import com.tau.nexus_note.MainViewModel
import com.tau.nexus_note.utils.labelToColor

@Composable
fun NexusView(viewModel: MainViewModel) {
    val codices by viewModel.codices.collectAsState()
    val baseDirectory by viewModel.codexBaseDirectory.collectAsState()
    val showBaseDirPicker by viewModel.showBaseDirPicker.collectAsState()

    // --- State for inline creation ---
    val newCodexName by viewModel.newCodexName.collectAsState()
    val codexNameError by viewModel.codexNameError.collectAsState()
    val codexToDelete by viewModel.codexToDelete.collectAsState()

    // --- Dialogs ---

    DirectoryPicker(
        show = showBaseDirPicker,
        title = "Select Codex Storage Directory",
        initialDirectory = baseDirectory,
        onResult = { viewModel.onBaseDirectorySelected(it) }
    )


    codexToDelete?.let { item ->
        DeleteCodexDialog(
            item = item,
            onConfirm = { viewModel.confirmDeleteCodex() },
            onDismiss = { viewModel.cancelDeleteCodex() }
        )
    }

    // --- Main UI ---

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        // --- Database List ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Existing Codices in: $baseDirectory", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { viewModel.onChangeBaseDirectoryClicked() }) {
                Text("Change")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (codices.isEmpty()) {
                item {
                    Text(
                        "No codices found in this directory.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(codices) { graph ->
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
                    ),
                    // --- Leading icon ---
                    leadingContent = {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = "Codex Icon"
                        )
                    },
                    // --- Delete button ---
                    trailingContent = {
                        IconButton(onClick = { viewModel.requestDeleteCodex(graph) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Codex",
                                tint = colorInfo.composeFontColor // Match icon tint
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Create Codex ---

        // This Text is now part of the row
        Text(
            "Create New Codex:",
            style = MaterialTheme.typography.titleMedium,
            // Align this text to the center of the row's height
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        OutlinedTextField(
            value = newCodexName,
            onValueChange = { viewModel.validateCodexName(it) },
            label = { Text("Codex Name") },
            singleLine = true,
            placeholder = { Text("MyDatabase") },
            suffix = { Text(".sqlite") },
            isError = codexNameError != null,
            supportingText = {
                if (codexNameError != null) {
                    Text(codexNameError!!)
                }
            },
            // Use .weight(1f) to fill all *remaining* horizontal space
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth()
        )

        // --- Actions ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.onCodexNameConfirmed() },
                enabled = newCodexName.isNotBlank() && codexNameError == null,
            ) {
                Text("Create and Open")
            }
            Button(
                onClick = { viewModel.openInMemoryTerminal() },
            ) {
                Text("Open In-Memory Terminal")
            }
        }
    }
}