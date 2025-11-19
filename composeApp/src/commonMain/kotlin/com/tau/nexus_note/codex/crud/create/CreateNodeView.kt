package com.tau.nexus_note.codex.crud.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.datamodels.NodeCreationState
import com.tau.nexus_note.datamodels.SchemaDefinitionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNodeView(
    nodeCreationState: NodeCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Create Node", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = nodeCreationState.selectedSchema?.name ?: "Select Schema",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    nodeCreationState.schemas.forEach { schema ->
                        DropdownMenuItem(
                            text = { Text(schema.name) },
                            onClick = {
                                onSchemaSelected(schema)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Iterate over properties
            if (nodeCreationState.selectedSchema != null) {
                nodeCreationState.selectedSchema.properties.forEach { property ->
                    val currentValue = nodeCreationState.properties[property.name] ?: ""
                    val modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)

                    when (property.type) {
                        "Number" -> {
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = {
                                    if (it.isEmpty() || it == "-" || it.matches(Regex("-?\\d*(\\.\\d*)?"))) {
                                        onPropertyChanged(property.name, it)
                                    }
                                },
                                label = { Text("${property.name} (Number)") },
                                modifier = modifier,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        "LongText" -> {
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = { onPropertyChanged(property.name, it) },
                                label = { Text("${property.name} (LongText)") },
                                modifier = modifier,
                                singleLine = false,
                                maxLines = 5
                            )
                        }
                        "Date" -> {
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = { onPropertyChanged(property.name, it) },
                                label = { Text("${property.name} (Date)") },
                                placeholder = { Text("YYYY-MM-DD") },
                                modifier = modifier
                            )
                        }
                        "Image", "Audio" -> {
                            Row(
                                modifier = modifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = currentValue,
                                    onValueChange = { onPropertyChanged(property.name, it) },
                                    label = { Text("${property.name} (${property.type} Path)") },
                                    modifier = Modifier.weight(1f),
                                    readOnly = true
                                )
                                Button(
                                    onClick = { /* TODO: Launch file picker */ },
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text("...")
                                }
                            }
                        }
                        else -> { // Default "Text"
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = { onPropertyChanged(property.name, it) },
                                label = { Text("${property.name} (Text)") },
                                modifier = modifier,
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fixed Buttons
        Row {
            Button(onClick = onCreateClick) {
                Text("Create")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancelClick) {
                Text("Cancel")
            }
        }
    }
}