package com.tau.cryptic_ui_v0.notegraph.views // UPDATED: Package name

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.NodeCreationState // UPDATED: Uses new state class
import com.tau.cryptic_ui_v0.SchemaDefinitionItem // UPDATED: Uses new schema class

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNodeView(
    nodeCreationState: NodeCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit, // UPDATED: Parameter type
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
                value = nodeCreationState.selectedSchema?.name ?: "Select Schema", // UPDATED: Use .name
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
                        text = { Text(schema.name) }, // UPDATED: Use .name
                        onClick = {
                            onSchemaSelected(schema)
                            expanded = false
                        }
                    )
                }
            }
        }

        // UPDATED: Iterate over properties from selectedSchema
        nodeCreationState.selectedSchema?.properties?.forEach { property ->
            OutlinedTextField(
                value = nodeCreationState.properties[property.name] ?: "", // UPDATED: Use property.name
                onValueChange = { onPropertyChanged(property.name, it) }, // UPDATED: Use property.name
                label = { Text("${property.name}: ${property.type}") }, // UPDATED: Use property.name and .type
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
