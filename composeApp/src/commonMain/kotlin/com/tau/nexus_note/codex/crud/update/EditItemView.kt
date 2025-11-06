package com.tau.nexus_note.codex.crud.update

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tau.nexus_note.codex.crud.create.CreateEdgeSchemaView
import com.tau.nexus_note.codex.crud.create.CreateEdgeView
import com.tau.nexus_note.codex.crud.create.CreateNodeSchemaView
import com.tau.nexus_note.codex.crud.create.CreateNodeView
import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.EditScreenState
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.datamodels.SchemaProperty

@Composable
fun EditItemView(
    // The single state object that drives this view
    editScreenState: EditScreenState,

    // --- UPDATED: Simplified Event Handlers ---
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    // --- END UPDATES ---

    // Node Creation Handlers
    onNodeCreationSchemaSelected: (SchemaDefinitionItem) -> Unit, // UPDATED: Parameter type
    onNodeCreationPropertyChanged: (String, String) -> Unit,
    // REMOVED: onNodeCreationCreateClick

    // Edge Creation Handlers
    onEdgeCreationSchemaSelected: (SchemaDefinitionItem) -> Unit, // UPDATED: Parameter type
    onEdgeCreationConnectionSelected: (ConnectionPair) -> Unit,
    onEdgeCreationSrcSelected: (NodeDisplayItem) -> Unit,
    onEdgeCreationDstSelected: (NodeDisplayItem) -> Unit,
    onEdgeCreationPropertyChanged: (String, String) -> Unit,
    // REMOVED: onEdgeCreationCreateClick

    // Node Schema Creation Handlers
    // REMOVED: onNodeSchemaCreationCreateClick
    onNodeSchemaTableNameChange: (String) -> Unit,
    onNodeSchemaPropertyChange: (Int, SchemaProperty) -> Unit, // UPDATED: Parameter type
    onAddNodeSchemaProperty: () -> Unit,
    onRemoveNodeSchemaProperty: (Int) -> Unit,

    // Edge Schema Creation Handlers
    // REMOVED: onEdgeSchemaCreationCreateClick
    onEdgeSchemaTableNameChange: (String) -> Unit,
    onEdgeSchemaCreationAddConnection: (String, String) -> Unit,
    onEdgeSchemaCreationRemoveConnection: (Int) -> Unit,
    onEdgeSchemaPropertyChange: (Int, SchemaProperty) -> Unit, // UPDATED: Parameter type
    onAddEdgeSchemaProperty: () -> Unit,
    onRemoveEdgeSchemaProperty: (Int) -> Unit,

    // Node Edit Handlers
    onNodeEditPropertyChange: (String, String) -> Unit, // UPDATED: Key is String

    // Edge Edit Handlers
    onEdgeEditPropertyChange: (String, String) -> Unit, // UPDATED: Key is String

    // Node Schema Edit Handlers
    onNodeSchemaEditLabelChange: (String) -> Unit,
    onNodeSchemaEditPropertyChange: (Int, SchemaProperty) -> Unit, // UPDATED: Parameter type
    onNodeSchemaEditAddProperty: () -> Unit,
    onNodeSchemaEditRemoveProperty: (Int) -> Unit,

    // Edge Schema Edit Handlers
    onEdgeSchemaEditLabelChange: (String) -> Unit,
    onEdgeSchemaEditPropertyChange: (Int, SchemaProperty) -> Unit, // UPDATED: Parameter type
    onEdgeSchemaEditAddProperty: () -> Unit,
    onEdgeSchemaEditRemoveProperty: (Int) -> Unit,
    // ADDED: Handlers for editing connections
    onEdgeSchemaEditAddConnection: (src: String, dst: String) -> Unit,
    onEdgeSchemaEditRemoveConnection: (Int) -> Unit,
    allNodeSchemaNames: List<String> // ADDED: Needed for edge schema editors
) {
    // Use a 'when' block to route to the correct composable
    when (editScreenState) {
        is EditScreenState.CreateNode -> {
            CreateNodeView(
                nodeCreationState = editScreenState.state,
                onSchemaSelected = onNodeCreationSchemaSelected,
                onPropertyChanged = onNodeCreationPropertyChanged,
                onCreateClick = onSaveClick, // UPDATED
                onCancelClick = onCancelClick
            )
        }
        is EditScreenState.CreateEdge -> {
            CreateEdgeView(
                edgeCreationState = editScreenState.state,
                onSchemaSelected = onEdgeCreationSchemaSelected,
                onConnectionSelected = onEdgeCreationConnectionSelected,
                onSrcSelected = onEdgeCreationSrcSelected,
                onDstSelected = onEdgeCreationDstSelected,
                onPropertyChanged = onEdgeCreationPropertyChanged,
                onCreateClick = onSaveClick, // UPDATED
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
                onCreate = { onSaveClick() }, // UPDATED
                onCancel = onCancelClick
            )
        }
        is EditScreenState.CreateEdgeSchema -> {
            CreateEdgeSchemaView(
                state = editScreenState.state,
                onTableNameChange = onEdgeSchemaTableNameChange,
                onAddConnection = onEdgeSchemaCreationAddConnection,
                onRemoveConnection = onEdgeSchemaCreationRemoveConnection,
                onPropertyChange = onEdgeSchemaPropertyChange,
                onAddProperty = onAddEdgeSchemaProperty,
                onRemoveProperty = onRemoveEdgeSchemaProperty,
                onCreate = { onSaveClick() }, // UPDATED
                onCancel = onCancelClick
            )
        }
        is EditScreenState.EditNode -> {
            EditNodeView(
                state = editScreenState.state,
                onPropertyChange = onNodeEditPropertyChange,
                onSave = onSaveClick, // UPDATED
                onCancel = onCancelClick
            )
        }
        is EditScreenState.EditEdge -> {
            EditEdgeView(
                state = editScreenState.state,
                onPropertyChange = onEdgeEditPropertyChange,
                onSave = onSaveClick, // UPDATED
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
                onSave = onSaveClick, // UPDATED
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
                onSave = onSaveClick, // UPDATED
                onCancel = onCancelClick,
                onAddConnection = onEdgeSchemaEditAddConnection, // ADDED
                onRemoveConnection = onEdgeSchemaEditRemoveConnection, // ADDED
                allNodeSchemaNames = allNodeSchemaNames // ADDED
            )
        }
        is EditScreenState.None -> {
            Text("No item selected to edit.")
        }
    }
}