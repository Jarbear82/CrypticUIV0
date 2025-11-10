package com.tau.nexus_note.codex.crud.update // UPDATED: Package name

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
import com.tau.nexus_note.datamodels.NodeSchemaEditState
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.datamodels.SchemaPropertyTypes // <-- ADDED IMPORT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNodeSchemaView(
    state: NodeSchemaEditState,
    onLabelChange: (String) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit, // UPDATED: Parameter type
    onAddProperty: () -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    // UPDATED: Use the new SchemaPropertyTypes object
    val dataTypes = SchemaPropertyTypes.getCreatableTypes()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Edit Node Schema", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.currentName, // UPDATED: Use currentName
            onValueChange = onLabelChange,
            label = { Text("Table Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Properties", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            itemsIndexed(state.properties) { index, property ->
                // Note: isDeleted logic is handled in the ViewModel, not the view
                var expanded by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = property.name, // UPDATED: Use name
                        onValueChange = {
                            onPropertyChange(index, property.copy(name = it))
                        },
                        label = { Text("Property Name") },
                        modifier = Modifier.weight(1f),
                        isError = property.name.isBlank()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = property.type, // UPDATED: Use type
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().width(120.dp),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            dataTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        onPropertyChange(index, property.copy(type = type))
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    Checkbox(
                        checked = property.isDisplayProperty, // UPDATED: Use isDisplayProperty
                        onCheckedChange = {
                            onPropertyChange(index, property.copy(isDisplayProperty = it))
                        }
                    )
                    Text("Display") // UPDATED: Text changed from PK
                    IconButton(onClick = { onRemoveProperty(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Property", tint = Color.Red)
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