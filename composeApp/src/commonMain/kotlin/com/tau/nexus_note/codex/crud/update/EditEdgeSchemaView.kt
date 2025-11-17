package com.tau.nexus_note.codex.crud.update // UPDATED: Package name

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.EdgeSchemaEditState
import com.tau.nexus_note.datamodels.SchemaProperty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEdgeSchemaView(
    state: EdgeSchemaEditState,
    onLabelChange: (String) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddProperty: () -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    // ADDED: Handlers for editing connections
    onAddConnection: (src: String, dst: String) -> Unit,
    onRemoveConnection: (Int) -> Unit,
    allNodeSchemaNames: List<String>
) {
    // Define supported types
    val dataTypes = listOf("Text", "LongText", "Image", "Audio", "Date", "Number")

    // --- Local state for the "Add Connection" UI ---
    var newSrcTable by remember { mutableStateOf<String?>(null) }
    var newSrcExpanded by remember { mutableStateOf(false) }
    var newDstTable by remember { mutableStateOf<String?>(null) }
    var newDstExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Edit Edge Schema", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.currentName, // UPDATED: Use currentName
            onValueChange = onLabelChange,
            label = { Text("Table Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // UPDATED: Section for adding/removing connection pairs
        Text("Connection Pairs", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Source Table Dropdown
            ExposedDropdownMenuBox(
                expanded = newSrcExpanded,
                onExpandedChange = { newSrcExpanded = !newSrcExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = newSrcTable ?: "From...",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = newSrcExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = newSrcExpanded,
                    onDismissRequest = { newSrcExpanded = false }
                ) {
                    allNodeSchemaNames.forEach { schemaName ->
                        DropdownMenuItem(
                            text = { Text(schemaName) },
                            onClick = {
                                newSrcTable = schemaName
                                newSrcExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // Destination Table Dropdown
            ExposedDropdownMenuBox(
                expanded = newDstExpanded,
                onExpandedChange = { newDstExpanded = !newDstExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = newDstTable ?: "To...",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = newDstExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = newDstExpanded,
                    onDismissRequest = { newDstExpanded = false }
                ) {
                    allNodeSchemaNames.forEach { schemaName ->
                        DropdownMenuItem(
                            text = { Text(schemaName) },
                            onClick = {
                                newDstTable = schemaName
                                newDstExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // Add Button
            IconButton(
                onClick = {
                    onAddConnection(newSrcTable!!, newDstTable!!)
                    newSrcTable = null
                    newDstTable = null
                },
                enabled = newSrcTable != null && newDstTable != null
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Connection Pair")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // --- List of current connection pairs ---
        // --- UPDATED ---
        // Replaced hard-coded border with theme's outline color
        LazyColumn(modifier = Modifier.heightIn(max = 150.dp).border(1.dp, MaterialTheme.colorScheme.outline)) {
            // --- END UPDATE ---
            itemsIndexed(state.connections) { index, connection ->
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(connection.src, style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "to", modifier = Modifier.padding(horizontal = 8.dp))
                            Text(connection.dst, style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemoveConnection(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Connection")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Properties", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            itemsIndexed(state.properties) { index, property ->
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
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).width(120.dp),
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
                    IconButton(onClick = { onRemoveProperty(index) }) {
                        // --- UPDATED ---
                        // Replaced Color.Red with theme's error color
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Property",
                            tint = MaterialTheme.colorScheme.error
                        )
                        // --- END UPDATE ---
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