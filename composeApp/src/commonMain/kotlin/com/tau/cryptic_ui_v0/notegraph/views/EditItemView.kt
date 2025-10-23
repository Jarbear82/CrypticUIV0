package com.tau.cryptic_ui_v0.views

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tau.cryptic_ui_v0.*

@Composable
fun EditItemView(
    // The single state object that drives this view
    editScreenState: EditScreenState,

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
    onEdgeSchemaEditRemoveProperty: (Int) -> Unit
) {
    // Use a 'when' block to route to the correct composable
    when (editScreenState) {
        is EditScreenState.CreateNode -> {
            CreateNodeView(
                nodeCreationState = editScreenState.state,
                onSchemaSelected = onNodeCreationSchemaSelected,
                onPropertyChanged = onNodeCreationPropertyChanged,
                onCreateClick = onNodeCreationCreateClick,
                onCancelClick = onCancelClick
            )
        }
        is EditScreenState.CreateEdge -> {
            CreateEdgeView(
                edgeCreationState = editScreenState.state,
                onSchemaSelected = onEdgeCreationSchemaSelected,
                onSrcSelected = onEdgeCreationSrcSelected,
                onDstSelected = onEdgeCreationDstSelected,
                onPropertyChanged = onEdgeCreationPropertyChanged,
                onCreateClick = onEdgeCreationCreateClick,
                onCancelClick = onCancelClick
            )
        }
        is EditScreenState.CreateNodeSchema -> {
            CreateNodeSchemaView(
                state = editScreenState.state,
                onTableNameChange = onNodeSchemaTableNameChange,
                onPropertyChange = onNodeSchemaPropertyChange,
                onAddProperty = onAddNodeSchemaProperty,
                onRemoveProperty = onRemoveNodeSchemaProperty,
                onCreate = onNodeSchemaCreationCreateClick,
                onCancel = onCancelClick
            )
        }
        is EditScreenState.CreateEdgeSchema -> {
            CreateEdgeSchemaView(
                state = editScreenState.state,
                onTableNameChange = onEdgeSchemaTableNameChange,
                onSrcTableChange = onEdgeSchemaSrcTableChange,
                onDstTableChange = onEdgeSchemaDstTableChange,
                onPropertyChange = onEdgeSchemaPropertyChange,
                onAddProperty = onAddEdgeSchemaProperty,
                onRemoveProperty = onRemoveEdgeSchemaProperty,
                onCreate = onEdgeSchemaCreationCreateClick,
                onCancel = onCancelClick
            )
        }
        is EditScreenState.EditNode -> {
            EditNodeView(
                state = editScreenState.state,
                onPropertyChange = onNodeEditPropertyChange,
                onSave = onSaveClick,
                onCancel = onCancelClick
            )
        }
        is EditScreenState.EditEdge -> {
            EditEdgeView(
                state = editScreenState.state,
                onPropertyChange = onEdgeEditPropertyChange,
                onSave = onSaveClick,
                onCancel = onCancelClick
            )
        }
        is EditScreenState.EditNodeSchema -> {
            EditNodeSchemaView(
                state = editScreenState.state,
                onLabelChange = onNodeSchemaEditLabelChange,
                onPropertyChange = onNodeSchemaEditPropertyChange,
                onAddProperty = onNodeSchemaEditAddProperty,
                onRemoveProperty = onNodeSchemaEditRemoveProperty,
                onSave = onSaveClick,
                onCancel = onCancelClick
            )
        }
        is EditScreenState.EditEdgeSchema -> {
            EditEdgeSchemaView(
                state = editScreenState.state,
                onLabelChange = onEdgeSchemaEditLabelChange,
                onPropertyChange = onEdgeSchemaEditPropertyChange,
                onAddProperty = onEdgeSchemaEditAddProperty,
                onRemoveProperty = onEdgeSchemaEditRemoveProperty,
                onSave = onSaveClick,
                onCancel = onCancelClick
            )
        }
        is EditScreenState.None -> {
            Text("No item selected to edit.")
        }
    }
}