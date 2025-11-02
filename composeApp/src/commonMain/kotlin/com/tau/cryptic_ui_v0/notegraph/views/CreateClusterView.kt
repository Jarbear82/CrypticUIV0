package com.tau.cryptic_ui_v0.notegraph.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.ClusterCreationState
import com.tau.cryptic_ui_v0.SchemaDefinitionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClusterView(
    clusterCreationState: ClusterCreationState,
    onSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onPropertyChanged: (String, String) -> Unit,
    onCreateClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(8.dp)) {
        Text("Create Cluster", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = clusterCreationState.selectedSchema?.name ?: "Select Schema",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                clusterCreationState.schemas.forEach { schema ->
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

        clusterCreationState.selectedSchema?.properties?.forEach { property ->
            OutlinedTextField(
                value = clusterCreationState.properties[property.name] ?: "",
                onValueChange = { onPropertyChanged(property.name, it) },
                label = { Text("${property.name}: ${property.type}") },
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
