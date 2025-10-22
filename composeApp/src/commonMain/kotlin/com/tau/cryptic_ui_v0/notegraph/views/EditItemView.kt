package com.tau.cryptic_ui_v0.views

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tau.cryptic_ui_v0.*

@Composable
fun EditItemView(
    // Creation States
    nodeCreationState: NodeCreationState?,
    edgeCreationState: EdgeCreationState?,
    nodeSchemaCreationState: NodeSchemaCreationState,
    edgeSchemaCreationState: EdgeSchemaCreationState,

    // Editing States
    nodeEditState: NodeTable?,
    edgeEditState: EdgeTable?,
    nodeSchemaEditState: NodeSchemaEditState?,
    edgeSchemaEditState: EdgeSchemaEditState?,

    // Event Handlers
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,

    // Node Creation Handlers
    onNodeCreationSchemaSelected: (SchemaNode) -> Unit,
    onNodeCreationPropertyChanged: (String, String) -> Unit,
    onNodeCreationCreateClick: () -> Unit,

    // Edge Creation Handlers
    onEdgeCreationSchemaSelected: (SchemaEdge) -> Unit,
    onEdgeCreationSrcSelected: (NodeDisplayItem) -> Unit,
    onEdgeCreationDstSelected: (NodeDisplayItem) -> Unit,
    onEdgeCreationPropertyChanged: (String, String) -> Unit,
    onEdgeCreationCreateClick: () -> Unit,

    // Node Schema Creation Handlers
    onNodeSchemaCreationCreateClick: (NodeSchemaCreationState) -> Unit,
    onNodeSchemaTableNameChange: (String) -> Unit,
    onNodeSchemaPropertyChange: (Int, Property) -> Unit,
    onAddNodeSchemaProperty: () -> Unit,
    onRemoveNodeSchemaProperty: (Int) -> Unit,

    // Edge Schema Creation Handlers
    onEdgeSchemaCreationCreateClick: (EdgeSchemaCreationState) -> Unit,
    onEdgeSchemaTableNameChange: (String) -> Unit,
    onEdgeSchemaSrcTableChange: (String) -> Unit,
    onEdgeSchemaDstTableChange: (String) -> Unit,
    onEdgeSchemaPropertyChange: (Int, Property) -> Unit,
    onAddEdgeSchemaProperty: () -> Unit,
    onRemoveEdgeSchemaProperty: (Int) -> Unit,

    // Node Edit Handlers
    onNodeEditPropertyChange: (Int, String) -> Unit,

    // Edge Edit Handlers
    onEdgeEditPropertyChange: (Int, String) -> Unit,

    // Node Schema Edit Handlers
    onNodeSchemaEditLabelChange: (String) -> Unit,
    onNodeSchemaEditPropertyChange: (Int, EditableSchemaProperty) -> Unit,
    onNodeSchemaEditAddProperty: () -> Unit,
    onNodeSchemaEditRemoveProperty: (Int) -> Unit,

    // Edge Schema Edit Handlers
    onEdgeSchemaEditLabelChange: (String) -> Unit,
    onEdgeSchemaEditPropertyChange: (Int, EditableSchemaProperty) -> Unit,
    onEdgeSchemaEditAddProperty: () -> Unit,
    onEdgeSchemaEditRemoveProperty: (Int) -> Unit,

    // Special "Create" string triggers
    editItem: Any?
) {
    // --- Creation Flows ---
    if (nodeCreationState != null) {
        CreateNodeView(
            nodeCreationState = nodeCreationState,
            onSchemaSelected = onNodeCreationSchemaSelected,
            onPropertyChanged = onNodeCreationPropertyChanged,
            onCreateClick = onNodeCreationCreateClick,
            onCancelClick = onCancelClick
        )
    } else if (edgeCreationState != null) {
        CreateEdgeView(
            edgeCreationState = edgeCreationState,
            onSchemaSelected = onEdgeCreationSchemaSelected,
            onSrcSelected = onEdgeCreationSrcSelected,
            onDstSelected = onEdgeCreationDstSelected,
            onPropertyChanged = onEdgeCreationPropertyChanged,
            onCreateClick = onEdgeCreationCreateClick,
            onCancelClick = onCancelClick
        )
    } else if (editItem == "CreateNodeSchema") {
        CreateNodeSchemaView(
            state = nodeSchemaCreationState,
            onTableNameChange = onNodeSchemaTableNameChange,
            onPropertyChange = onNodeSchemaPropertyChange,
            onAddProperty = onAddNodeSchemaProperty,
            onRemoveProperty = onRemoveNodeSchemaProperty,
            onCreate = onNodeSchemaCreationCreateClick,
            onCancel = onCancelClick
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
            onCancel = onCancelClick
        )
        // --- Editing Flows ---
    } else if (nodeEditState != null) {
        EditNodeView(
            state = nodeEditState,
            onPropertyChange = onNodeEditPropertyChange,
            onSave = onSaveClick,
            onCancel = onCancelClick
        )
    } else if (edgeEditState != null) {
        EditEdgeView(
            state = edgeEditState,
            onPropertyChange = onEdgeEditPropertyChange,
            onSave = onSaveClick,
            onCancel = onCancelClick
        )
    } else if (nodeSchemaEditState != null) {
        EditNodeSchemaView(
            state = nodeSchemaEditState,
            onLabelChange = onNodeSchemaEditLabelChange,
            onPropertyChange = onNodeSchemaEditPropertyChange,
            onAddProperty = onNodeSchemaEditAddProperty,
            onRemoveProperty = onNodeSchemaEditRemoveProperty,
            onSave = onSaveClick,
            onCancel = onCancelClick
        )
    } else if (edgeSchemaEditState != null) {
        EditEdgeSchemaView(
            state = edgeSchemaEditState,
            onLabelChange = onEdgeSchemaEditLabelChange,
            onPropertyChange = onEdgeSchemaEditPropertyChange,
            onAddProperty = onEdgeSchemaEditAddProperty,
            onRemoveProperty = onEdgeSchemaEditRemoveProperty,
            onSave = onSaveClick,
            onCancel = onCancelClick
        )
    } else {
        Text("No item selected to edit.")
    }
}