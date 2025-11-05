package com.tau.nexus_note.codex.crud

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.tau.nexus_note.datamodels.SchemaDefinitionItem

@Composable
fun DeleteSchemaConfirmationDialog(
    item: SchemaDefinitionItem,
    dependencyCount: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = "Delete Schema?"
    val text = if (dependencyCount == 0L) {
        "Are you sure you want to delete the schema '${item.name}'?"
    } else {
        "Warning: This schema is used by $dependencyCount node(s) or edge(s). " +
                "Deleting '${item.name}' will also delete all of them.\n\n" +
                "Are you sure you want to continue?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
