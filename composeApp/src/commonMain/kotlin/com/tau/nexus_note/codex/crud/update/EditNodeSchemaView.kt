package com.tau.nexus_note.codex.crud.update

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.datamodels.NodeSchemaEditState
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.utils.toCamelCase
import com.tau.nexus_note.utils.toPascalCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNodeSchemaView(
    state: NodeSchemaEditState,
    onLabelChange: (String) -> Unit,
    onPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddProperty: (SchemaProperty) -> Unit,
    onRemoveProperty: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    // --- Local state for the "Add Property" UI ---
    var newPropName by remember { mutableStateOf("") }
    var newPropType by remember { mutableStateOf(CodexPropertyDataTypes.TEXT) }
    var newIsDisplay by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Edit Node Schema", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Table Name
            OutlinedTextField(
                value = state.currentName,
                onValueChange = { onLabelChange(it.toPascalCase()) },
                label = { Text("Table Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = state.currentNameError != null,
                supportingText = { state.currentNameError?.let { Text(it) } },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- Add Property Input Row (Unified UI) ---
            Text("Properties", style = MaterialTheme.typography.titleMedium)

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
                        value = newPropType.displayName,
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
                        CodexPropertyDataTypes.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    newPropType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Display Checkbox
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Display", style = MaterialTheme.typography.labelSmall)
                    Checkbox(
                        checked = newIsDisplay,
                        onCheckedChange = { newIsDisplay = it }
                    )
                }

                // Add Button
                IconButton(
                    onClick = {
                        onAddProperty(
                            SchemaProperty(
                                name = newPropName,
                                type = newPropType,
                                isDisplayProperty = newIsDisplay
                            )
                        )
                        newPropName = ""
                        newPropType = CodexPropertyDataTypes.TEXT
                        newIsDisplay = false
                    },
                    enabled = newPropName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Property")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Existing Properties List ---
            Column(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .fillMaxWidth()
            ) {
                state.properties.forEachIndexed { index, property ->
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = property.name,
                            onValueChange = {
                                onPropertyChange(index, property.copy(name = it.toCamelCase()))
                            },
                            label = { Text("Name") },
                            modifier = Modifier.weight(1f),
                            isError = state.propertyErrors.containsKey(index) || property.name.isBlank(),
                            supportingText = { state.propertyErrors[index]?.let { Text(it) } },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = property.type.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                CodexPropertyDataTypes.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.displayName) },
                                        onClick = {
                                            onPropertyChange(index, property.copy(type = type))
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Checkbox(
                                checked = property.isDisplayProperty,
                                onCheckedChange = {
                                    onPropertyChange(index, property.copy(isDisplayProperty = it))
                                }
                            )
                        }
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

        // Fixed Buttons
        Row {
            Button(
                onClick = onSave,
                enabled = state.currentName.isNotBlank() && state.currentNameError == null && state.propertyErrors.isEmpty()
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