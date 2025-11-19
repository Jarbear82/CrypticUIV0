package com.tau.nexus_note.codex.crud.update

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.EdgeSchemaEditState
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.utils.toCamelCase
import com.tau.nexus_note.utils.toScreamingSnakeCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEdgeSchemaView(
    state: EdgeSchemaEditState,
    onLabelChange: (String) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onAddConnection: (src: String, dst: String) -> Unit,
    onRemoveConnection: (Int) -> Unit
) {
    // Define supported types
    val dataTypes = listOf("Text", "LongText", "Image", "Audio", "Date", "Number")

    // --- Local state for the "Add Connection" UI ---
    var newSrcTable by remember { mutableStateOf<String?>(null) }
    var newSrcExpanded by remember { mutableStateOf(false) }
    var newDstTable by remember { mutableStateOf<String?>(null) }
    var newDstExpanded by remember { mutableStateOf(false) }

    // --- Local state for the "Add Property" UI ---
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf("Text") }
    var typeExpanded by remember { mutableStateOf(false) }

    // Outer Column fills the screen
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Edit Edge Schema", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // Scrollable Content Area (Takes up remaining space)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = state.currentName,
                onValueChange = { onLabelChange(it.replace(" ", "_").toScreamingSnakeCase()) },
                label = { Text("Table Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.currentNameError != null,
                supportingText = { state.currentNameError?.let { Text(it) } },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- Connection Pairs Section ---
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
                        state.allNodeSchemas.forEach { schema ->
                            DropdownMenuItem(
                                text = { Text(schema.name) },
                                onClick = {
                                    newSrcTable = schema.name
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
                        state.allNodeSchemas.forEach { schema ->
                            DropdownMenuItem(
                                text = { Text(schema.name) },
                                onClick = {
                                    newDstTable = schema.name
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

            // List of connections (Using Column loop instead of LazyColumn for nesting safety)
            Column(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            ) {
                state.connections.forEachIndexed { index, connection ->
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
                    if (index < state.connections.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                if (state.connections.isEmpty()) {
                    Text(
                        "No connections defined.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Properties Section ---
            Text("Properties", style = MaterialTheme.typography.titleMedium)

            // Add Property Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Property Name
                OutlinedTextField(
                    value = newPropName,
                    onValueChange = { newPropName = it.toCamelCase() },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Property Type
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = newPropType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        dataTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    newPropType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Add Button (Display property option removed for Edges)
                IconButton(
                    onClick = {
                        onAddProperty(
                            SchemaProperty(
                                name = newPropName,
                                type = newPropType,
                                isDisplayProperty = false // Always false for edges
                            )
                        )
                        newPropName = ""
                        newPropType = "Text"
                    },
                    enabled = newPropName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Property")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List of properties (Using Column loop)
            Column {
                state.properties.forEachIndexed { index, property ->
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = property.name,
                            onValueChange = {
                                onPropertyChange(index, property.copy(name = it.toCamelCase()))
                            },
                            label = { Text("Property Name") },
                            modifier = Modifier.weight(1f),
                            isError = state.propertyErrors.containsKey(index) || property.name.isBlank(),
                            supportingText = { state.propertyErrors[index]?.let { Text(it) } },
                            singleLine = true
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
                        // Display checkbox removed for Edges
                        IconButton(onClick = { onRemoveProperty(index) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Property",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (index < state.properties.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Actions (Fixed at Bottom) ---
        Row {
            Button(
                onClick = onSave,
                enabled = state.currentName.isNotBlank() && state.connections.isNotEmpty() && state.currentNameError == null && state.propertyErrors.isEmpty()
            ) {
                Text("Save")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}