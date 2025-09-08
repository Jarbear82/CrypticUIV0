package com.tau.cryptic_ui_v0

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
            is NodeTable -> {
                Text("Selected Node", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Label: ${selectedItem.label}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Properties:", style = MaterialTheme.typography.titleMedium)
                LazyColumn {
                    items(selectedItem.properties) { property ->
                        Row {
                            Text("${property.key}: ", fontWeight = FontWeight.SemiBold)
                            Text(property.value.toString())
                            if (property.isPrimaryKey) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("⭐", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            is RelTable -> {
                Text("Selected Relationship", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Label: ${selectedItem.label}", fontWeight = FontWeight.Bold)
                Text("Source: ${selectedItem.src.label} (${selectedItem.src.primarykeyProperty.value})")
                Text("Destination: ${selectedItem.dst.label} (${selectedItem.dst.primarykeyProperty.value})")
                Spacer(modifier = Modifier.height(8.dp))

                // Properties are nullable for relationships, so check before displaying
                selectedItem.properties?.let { properties ->
                    if (properties.isNotEmpty()) {
                        Text("Properties:", style = MaterialTheme.typography.titleMedium)
                        LazyColumn {
                            items(properties) { property ->
                                Row {
                                    Text("${property.key}: ", fontWeight = FontWeight.SemiBold)
                                    Text(property.value.toString())
                                }
                            }
                        }
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