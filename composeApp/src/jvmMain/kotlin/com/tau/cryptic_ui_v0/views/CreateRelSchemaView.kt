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
import com.tau.cryptic_ui_v0.Property
import com.tau.cryptic_ui_v0.RelSchemaCreationState
import com.tau.cryptic_ui_v0.SchemaNode
import kotlin.collections.plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRelSchemaView(
    schemas: List<SchemaNode>,
    onCreate: (RelSchemaCreationState) -> Unit,
    onCancel: () -> Unit
) {
    var state by remember { mutableStateOf(RelSchemaCreationState(allNodeSchemas = schemas)) }
    val dataTypes = listOf("STRING", "INT64", "DOUBLE", "BOOL", "DATE", "TIMESTAMP", "INTERVAL", "BLOB", "UUID")

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Create Relationship Schema", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.tableName,
            onValueChange = { state = state.copy(tableName = it) },
            label = { Text("Table Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Dropdowns for Src and Dst
        var srcExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = srcExpanded,
            onExpandedChange = { srcExpanded = !srcExpanded }
        ) {
            OutlinedTextField(
                value = state.srcTable ?: "Source Table",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = srcExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = srcExpanded,
                onDismissRequest = { srcExpanded = false }
            ) {
                schemas.forEach { schema ->
                    DropdownMenuItem(
                        text = { Text(schema.label) },
                        onClick = {
                            state = state.copy(srcTable = schema.label)
                            srcExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        var dstExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = dstExpanded,
            onExpandedChange = { dstExpanded = !dstExpanded }
        ) {
            OutlinedTextField(
                value = state.dstTable ?: "Destination Table",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dstExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = dstExpanded,
                onDismissRequest = { dstExpanded = false }
            ) {
                schemas.forEach { schema ->
                    DropdownMenuItem(
                        text = { Text(schema.label) },
                        onClick = {
                            state = state.copy(dstTable = schema.label)
                            dstExpanded = false
                        }
                    )
                }
            }
        }


        Spacer(modifier = Modifier.height(16.dp))

        Text("Properties", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            itemsIndexed(state.properties) { index, property ->
                var expanded by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = property.name,
                        onValueChange = {
                            val newProperties = state.properties.toMutableList()
                            newProperties[index] = property.copy(name = it)
                            state = state.copy(properties = newProperties)
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
                                        val newProperties = state.properties.toMutableList()
                                        newProperties[index] = property.copy(type = type)
                                        state = state.copy(properties = newProperties)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        val newProperties = state.properties.toMutableList()
                        newProperties.removeAt(index)
                        state = state.copy(properties = newProperties)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Property")
                    }
                }
            }
        }

        Button(onClick = {
            state = state.copy(properties = state.properties + Property(isPrimaryKey = false))
        }) {
            Icon(Icons.Default.Add, contentDescription = "Add Property")
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Property")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = { onCreate(state) }, enabled = state.srcTable != null && state.dstTable != null && state.tableName.isNotBlank()) {
                Text("Create")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}