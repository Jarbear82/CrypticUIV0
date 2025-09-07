package com.tau.cryptic_ui_v0

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SelectedItemView(
    selectedItem: Any?,
    onClearSelection: () -> Unit
) {
    if (selectedItem == null) {
        Text("No item selected.")
        return
    }

    Column(modifier = Modifier.padding(8.dp)) {
        when (selectedItem) {
            is DisplayItem -> {
                Text("Selected Node", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Label: ${selectedItem.label}")
                Text("Primary Key: ${selectedItem.primaryKey}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Properties:", style = MaterialTheme.typography.titleMedium)
                LazyColumn {
                    items(selectedItem.properties.toList()) { (key, value) ->
                        Text("$key: $value")
                    }
                }
            }
            is RelDisplayItem -> {
                Text("Selected Relationship", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Label: ${selectedItem.label}")
                Text("Source: ${selectedItem.srcLabel} (${selectedItem.src})")
                Text("Destination: ${selectedItem.dstLabel} (${selectedItem.dst})")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Properties:", style = MaterialTheme.typography.titleMedium)
                LazyColumn {
                    items(selectedItem.properties.toList()) { (key, value) ->
                        Text("$key: $value")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onClearSelection) {
            Text("Clear Selection")
        }
    }
}