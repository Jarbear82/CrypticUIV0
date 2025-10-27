package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.ConnectionPair
import com.tau.cryptic_ui_v0.EditableSchemaProperty
import com.tau.cryptic_ui_v0.EdgeSchemaEditState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEdgeSchemaView(
    state: EdgeSchemaEditState,
    onLabelChange: (String) -> Unit,
    onAddConnection: (String) -> Unit,
    onDropConnection: (ConnectionPair) -> Unit,
    onPropertyChange: (Int, EditableSchemaProperty) -> Unit,
    onAddProperty: () -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val dataTypes = listOf("STRING", "INT64", "DOUBLE", "BOOL", "DATE", "TIMESTAMP", "INTERVAL", "BLOB", "UUID")

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Edit Edge Schema", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.currentLabel,
            onValueChange = onLabelChange,
            label = { Text("Table Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Kuzu doesn't support updating connections, so we show them as read-only.
        Text("Connections (read-only)", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.heightIn(max = 150.dp).padding(start = 8.dp)) {
            state.originalSchema.connections.forEach {
                Text("FROM (${it.src}) TO (${it.dst})", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Properties", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            itemsIndexed(state.properties) { index, property ->
                if (property.isDeleted) {
                    // Don't show deleted
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = property.key,
                            onValueChange = {
                                onPropertyChange(index, property.copy(key = it))
                            },
                            label = { Text("Property Name") },
                            modifier = Modifier.weight(1f),
                            isError = property.isNew && property.key.isBlank()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = property.valueDataType,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().width(120.dp),
                                enabled = property.isNew // Only allow type change for new properties
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                dataTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            onPropertyChange(index, property.copy(valueDataType = type))
                                            expanded = false
                                        },
                                        enabled = property.isNew
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onRemoveProperty(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Property", tint = Color.Red)
                        }
                    }
                }
            }
        }

        Button(onClick = onAddProperty) {
            Icon(Icons.Default.Add, contentDescription = "Add Property")
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Property")
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