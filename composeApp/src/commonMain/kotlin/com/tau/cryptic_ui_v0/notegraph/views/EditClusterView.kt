package com.tau.cryptic_ui_v0.notegraph.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.ClusterEditState

@Composable
fun EditClusterView(
    state: ClusterEditState,
    onPropertyChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Edit Cluster: ${state.schema.name}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Properties", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            itemsIndexed(state.schema.properties) { index, schemaProperty ->
                OutlinedTextField(
                    value = state.properties[schemaProperty.name] ?: "",
                    onValueChange = {
                        onPropertyChange(schemaProperty.name, it)
                    },
                    label = { Text("${schemaProperty.name} (${schemaProperty.type})") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = onSave) {
                Text("Save")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}
