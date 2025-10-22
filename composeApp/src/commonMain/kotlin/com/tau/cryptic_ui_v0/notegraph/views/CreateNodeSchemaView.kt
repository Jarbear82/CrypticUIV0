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
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.NodeSchemaCreationState
import com.tau.cryptic_ui_v0.Property

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNodeSchemaView(
    state: NodeSchemaCreationState,
    onTableNameChange: (String) -> Unit,
    onPropertyChange: (Int, Property) -> Unit,
    onAddProperty: () -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onCancel: () -> Unit,
    onCreate: (NodeSchemaCreationState) -> Unit
) {
    val dataTypes = listOf("STRING", "INT64", "DOUBLE", "BOOL", "DATE", "TIMESTAMP", "INTERVAL", "BLOB", "UUID", "SERIAL")

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Create Node Schema", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.tableName,
            onValueChange = onTableNameChange,
            label = { Text("Table Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Properties", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            itemsIndexed(state.properties) { index, property ->
                var expanded by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = property.name,
                        onValueChange = {
                            onPropertyChange(index, property.copy(name = it))
                        },
                        label = { Text("Property Name") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = property.type,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().width(120.dp)
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
                                    }
                                )
                            }
                        }
                    }
                    Checkbox(
                        checked = property.isPrimaryKey,
                        onCheckedChange = {
                            onPropertyChange(index, property.copy(isPrimaryKey = it))
                        }
                    )
                    Text("PK")
                    IconButton(onClick = { onRemoveProperty(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Property")
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
            Button(onClick = { onCreate(state) }) {
                Text("Create")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}