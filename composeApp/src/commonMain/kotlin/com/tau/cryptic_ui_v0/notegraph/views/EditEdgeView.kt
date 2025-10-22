package com.tau.cryptic_ui_v0.views

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
import com.tau.cryptic_ui_v0.EdgeTable

@Composable
fun EditEdgeView(
    state: EdgeTable,
    onPropertyChange: (Int, String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Edit Edge: ${state.label}", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("From: ${state.src.label} (${state.src.primarykeyProperty.value})", style = MaterialTheme.typography.bodyMedium)
        Text("To: ${state.dst.label} (${state.dst.primarykeyProperty.value})", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Properties", style = MaterialTheme.typography.titleMedium)
        if (state.properties.isNullOrEmpty()) {
            Text("No properties to edit.", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                itemsIndexed(state.properties!!) { index, property ->
                    OutlinedTextField(
                        value = property.value?.toString() ?: "",
                        onValueChange = {
                            onPropertyChange(index, it)
                        },
                        label = { Text(property.key) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
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