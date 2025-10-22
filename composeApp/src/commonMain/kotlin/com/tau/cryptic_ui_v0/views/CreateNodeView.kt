package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.NodeCreationState
import com.tau.cryptic_ui_v0.SchemaNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNodeView(
    nodeCreationState: NodeCreationState,
    onSchemaSelected: (SchemaNode) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(8.dp)) {
        Text("Create Node", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = nodeCreationState.selectedSchema?.label ?: "Select Schema",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                nodeCreationState.schemas.forEach { schema ->
                    DropdownMenuItem(
                        text = { Text(schema.label) },
                        onClick = {
                            onSchemaSelected(schema)
                            expanded = false
                        }
                    )
                }
            }
        }

        nodeCreationState.selectedSchema?.properties?.forEach { property ->
            OutlinedTextField(
                value = nodeCreationState.properties[property.key] ?: "",
                onValueChange = { onPropertyChanged(property.key, it) },
                label = { Text("${property.key}: ${property.valueDataType}") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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