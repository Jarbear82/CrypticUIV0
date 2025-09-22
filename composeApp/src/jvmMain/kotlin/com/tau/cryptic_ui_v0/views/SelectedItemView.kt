package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.NodeCreationState
import com.tau.cryptic_ui_v0.NodeDisplayItem
import com.tau.cryptic_ui_v0.NodeSchemaCreationState
import com.tau.cryptic_ui_v0.NodeTable
import com.tau.cryptic_ui_v0.RelCreationState
import com.tau.cryptic_ui_v0.RelSchemaCreationState
import com.tau.cryptic_ui_v0.RelTable
import com.tau.cryptic_ui_v0.SchemaNode
import com.tau.cryptic_ui_v0.SchemaRel

@Composable
fun SelectedItemView(
    selectedItem: Any?,
    nodeCreationState: NodeCreationState?,
    relCreationState: RelCreationState?,
    nodeSchemaCreationState: NodeSchemaCreationState?,
    relSchemaCreationState: RelSchemaCreationState?,
    onClearSelection: () -> Unit,
    onNodeCreationSchemaSelected: (SchemaNode) -> Unit,
    onNodeCreationPropertyChanged: (String, String) -> Unit,
    onNodeCreationCreateClick: () -> Unit,
    onNodeCreationCancelClick: () -> Unit,
    onRelCreationSchemaSelected: (SchemaRel) -> Unit,
    onRelCreationSrcSelected: (NodeDisplayItem) -> Unit,
    onRelCreationDstSelected: (NodeDisplayItem) -> Unit,
    onRelCreationPropertyChanged: (String, String) -> Unit,
    onRelCreationCreateClick: () -> Unit,
    onRelCreationCancelClick: () -> Unit,
    onNodeSchemaCreationCreateClick: (NodeSchemaCreationState) -> Unit,
    onNodeSchemaCreationCancelClick: () -> Unit,
    onRelSchemaCreationCreateClick: (RelSchemaCreationState) -> Unit,
    onRelSchemaCreationCancelClick: () -> Unit
) {
    if (nodeCreationState != null) {
        CreateNodeView(
            nodeCreationState = nodeCreationState,
            onSchemaSelected = onNodeCreationSchemaSelected,
            onPropertyChanged = onNodeCreationPropertyChanged,
            onCreateClick = onNodeCreationCreateClick,
            onCancelClick = onNodeCreationCancelClick
        )
    } else if (relCreationState != null) {
        CreateRelView(
            relCreationState = relCreationState,
            onSchemaSelected = onRelCreationSchemaSelected,
            onSrcSelected = onRelCreationSrcSelected,
            onDstSelected = onRelCreationDstSelected,
            onPropertyChanged = onRelCreationPropertyChanged,
            onCreateClick = onRelCreationCreateClick,
            onCancelClick = onRelCreationCancelClick
        )
    } else if (nodeSchemaCreationState != null) {
        CreateNodeSchemaView(
            onCreate = onNodeSchemaCreationCreateClick,
            onCancel = onNodeSchemaCreationCancelClick
        )
    } else if (relSchemaCreationState != null) {
        CreateRelSchemaView(
            schemas = relSchemaCreationState.allNodeSchemas,
            onCreate = onRelSchemaCreationCreateClick,
            onCancel = onRelSchemaCreationCancelClick
        )
    } else if (selectedItem == null) {
        Text("No item selected.")
    } else {
        Column(modifier = Modifier.padding(8.dp)) {
            when (selectedItem) {
                is NodeTable -> {
                    Text("Selected Node", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Label: ${selectedItem.label}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Properties:", style = MaterialTheme.typography.titleMedium)
                    LazyColumn {
                        items(selectedItem.properties) { property ->
                            Row {
                                Text("${property.key}: ", fontWeight = FontWeight.SemiBold)
                                Text(property.value.toString())
                                if (property.isPrimaryKey) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("⭐", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                is RelTable -> {
                    Text("Selected Relationship", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Label: ${selectedItem.label}", fontWeight = FontWeight.Bold)
                    Text("Source: ${selectedItem.src.label} (${selectedItem.src.primarykeyProperty.value})")
                    Text("Destination: ${selectedItem.dst.label} (${selectedItem.dst.primarykeyProperty.value})")
                    Spacer(modifier = Modifier.height(8.dp))

                    // Properties are nullable for relationships, so check before displaying
                    selectedItem.properties?.let { properties ->
                        if (properties.isNotEmpty()) {
                            Text("Properties:", style = MaterialTheme.typography.titleMedium)
                            LazyColumn {
                                items(properties) { property ->
                                    Row {
                                        Text("${property.key}: ", fontWeight = FontWeight.SemiBold)
                                        Text(property.value.toString())
                                    }
                                }
                            }
                        }
                    }
                }
                is SchemaNode -> {
                    Text("Selected Node Schema", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Label: ${selectedItem.label}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Properties:", style = MaterialTheme.typography.titleMedium)
                    LazyColumn {
                        items(selectedItem.properties) { property ->
                            Row {
                                Text("${property.key}: ", fontWeight = FontWeight.SemiBold)
                                Text(property.valueDataType.toString())
                                if (property.isPrimaryKey) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("⭐", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                is SchemaRel -> {
                    Text("Selected Relationship Schema", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Label: ${selectedItem.label}", fontWeight = FontWeight.Bold)
                    Text("Source: ${selectedItem.srcLabel}")
                    Text("Destination: ${selectedItem.dstLabel}")
                    Spacer(modifier = Modifier.height(8.dp))

                    // Properties are nullable for relationships, so check before displaying
                    selectedItem.properties.let { properties ->
                        if (properties.isNotEmpty()) {
                            Text("Properties:", style = MaterialTheme.typography.titleMedium)
                            LazyColumn {
                                items(properties) { property ->
                                    Row {
                                        Text("${property.key}: ", fontWeight = FontWeight.SemiBold)
                                        Text(property.valueDataType.toString())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onClearSelection) {
                Text("Clear Selection")
            }
        }
    }
}