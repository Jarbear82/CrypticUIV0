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
import com.tau.cryptic_ui_v0.*

@Composable
fun EditItemView(
    editItem: Any?,
    nodeCreationState: NodeCreationState?,
    edgeCreationState: EdgeCreationState?,
    nodeSchemaCreationState: NodeSchemaCreationState,
    edgeSchemaCreationState: EdgeSchemaCreationState,
    onClearSelection: () -> Unit,
    onNodeCreationSchemaSelected: (SchemaNode) -> Unit,
    onNodeCreationPropertyChanged: (String, String) -> Unit,
    onNodeCreationCreateClick: () -> Unit,
    onNodeCreationCancelClick: () -> Unit,
    onEdgeCreationSchemaSelected: (SchemaEdge) -> Unit,
    onEdgeCreationSrcSelected: (NodeDisplayItem) -> Unit,
    onEdgeCreationDstSelected: (NodeDisplayItem) -> Unit,
    onEdgeCreationPropertyChanged: (String, String) -> Unit,
    onEdgeCreationCreateClick: () -> Unit,
    onEdgeCreationCancelClick: () -> Unit,
    onNodeSchemaCreationCreateClick: (NodeSchemaCreationState) -> Unit,
    onNodeSchemaCreationCancelClick: () -> Unit,
    onEdgeSchemaCreationCreateClick: (EdgeSchemaCreationState) -> Unit,
    onEdgeSchemaCreationCancelClick: () -> Unit,
    onNodeSchemaTableNameChange: (String) -> Unit,
    onNodeSchemaPropertyChange: (Int, Property) -> Unit,
    onAddNodeSchemaProperty: () -> Unit,
    onRemoveNodeSchemaProperty: (Int) -> Unit,
    onEdgeSchemaTableNameChange: (String) -> Unit,
    onEdgeSchemaSrcTableChange: (String) -> Unit,
    onEdgeSchemaDstTableChange: (String) -> Unit,
    onEdgeSchemaPropertyChange: (Int, Property) -> Unit,
    onAddEdgeSchemaProperty: () -> Unit,
    onRemoveEdgeSchemaProperty: (Int) -> Unit
) {
    if (nodeCreationState != null) {
        CreateNodeView(
            nodeCreationState = nodeCreationState,
            onSchemaSelected = onNodeCreationSchemaSelected,
            onPropertyChanged = onNodeCreationPropertyChanged,
            onCreateClick = onNodeCreationCreateClick,
            onCancelClick = onNodeCreationCancelClick
        )
    } else if (edgeCreationState != null) {
        CreateEdgeView(
            edgeCreationState = edgeCreationState,
            onSchemaSelected = onEdgeCreationSchemaSelected,
            onSrcSelected = onEdgeCreationSrcSelected,
            onDstSelected = onEdgeCreationDstSelected,
            onPropertyChanged = onEdgeCreationPropertyChanged,
            onCreateClick = onEdgeCreationCreateClick,
            onCancelClick = onEdgeCreationCancelClick
        )
    } else if (editItem == "CreateNodeSchema") {
        CreateNodeSchemaView(
            state = nodeSchemaCreationState,
            onTableNameChange = onNodeSchemaTableNameChange,
            onPropertyChange = onNodeSchemaPropertyChange,
            onAddProperty = onAddNodeSchemaProperty,
            onRemoveProperty = onRemoveNodeSchemaProperty,
            onCreate = onNodeSchemaCreationCreateClick,
            onCancel = onNodeSchemaCreationCancelClick
        )
    } else if (editItem == "CreateEdgeSchema") {
        CreateEdgeSchemaView(
            state = edgeSchemaCreationState,
            onTableNameChange = onEdgeSchemaTableNameChange,
            onSrcTableChange = onEdgeSchemaSrcTableChange,
            onDstTableChange = onEdgeSchemaDstTableChange,
            onPropertyChange = onEdgeSchemaPropertyChange,
            onAddProperty = onAddEdgeSchemaProperty,
            onRemoveProperty = onRemoveEdgeSchemaProperty,
            onCreate = onEdgeSchemaCreationCreateClick,
            onCancel = onEdgeSchemaCreationCancelClick
        )
    } else if (editItem == null) {
        Text("No item selected to edit.")
    } else {
        Column(modifier = Modifier.padding(8.dp)) {
            when (editItem) {
                is NodeTable -> {
                    Text("Edit Node", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Label: ${editItem.label}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Properties:", style = MaterialTheme.typography.titleMedium)
                    LazyColumn {
                        items(editItem.properties) { property ->
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
                is EdgeTable -> {
                    Text("Edit Edge", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Label: ${editItem.label}", fontWeight = FontWeight.Bold)
                    Text("Source: ${editItem.src.label} (${editItem.src.primarykeyProperty.value})")
                    Text("Destination: ${editItem.dst.label} (${editItem.dst.primarykeyProperty.value})")
                    Spacer(modifier = Modifier.height(8.dp))

                    // Properties are nullable for edges, so check before displaying
                    editItem.properties?.let { properties ->
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
                    Text("Edit Node Schema", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Label: ${editItem.label}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Properties:", style = MaterialTheme.typography.titleMedium)
                    LazyColumn {
                        items(editItem.properties) { property ->
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
                is SchemaEdge -> {
                    Text("Edit Edge Schema", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Label: ${editItem.label}", fontWeight = FontWeight.Bold)
                    Text("Source: ${editItem.srcLabel}")
                    Text("Destination: ${editItem.dstLabel}")
                    Spacer(modifier = Modifier.height(8.dp))

                    // Properties are nullable for edges, so check before displaying
                    editItem.properties.let { properties ->
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
                Text("Clear")
            }
        }
    }
}