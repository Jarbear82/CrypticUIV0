package com.tau.cryptic_ui_v0.notegraph.views // UPDATED: Package name

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tau.cryptic_ui_v0.* // UPDATED: Imports all new data classes

@Composable
fun EditItemView(
    // The single state object that drives this view
    editScreenState: EditScreenState,

    // Event Handlers
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,

    // Node Creation Handlers
    onNodeCreationSchemaSelected: (SchemaDefinitionItem) -> Unit, // UPDATED: Parameter type
    onNodeCreationPropertyChanged: (String, String) -> Unit,
    onNodeCreationCreateClick: () -> Unit,

    // Edge Creation Handlers
    onEdgeCreationSchemaSelected: (SchemaDefinitionItem) -> Unit, // UPDATED: Parameter type
    onEdgeCreationConnectionSelected: (ConnectionPair) -> Unit,
    // --- MODIFIED ---
    onEdgeCreationSrcSelected: (GraphEntityDisplayItem) -> Unit,
    onEdgeCreationDstSelected: (GraphEntityDisplayItem) -> Unit,
    // --- END MODIFICATION ---
    onEdgeCreationPropertyChanged: (String, String) -> Unit,
    onEdgeCreationCreateClick: () -> Unit,

    // Node Schema Creation Handlers
    onNodeSchemaCreationCreateClick: () -> Unit, // UPDATED: State is now in ViewModel
    onNodeSchemaTableNameChange: (String) -> Unit,
    onNodeSchemaPropertyChange: (Int, SchemaProperty) -> Unit, // UPDATED: Parameter type
    onAddNodeSchemaProperty: () -> Unit,
    onRemoveNodeSchemaProperty: (Int) -> Unit,

    // Edge Schema Creation Handlers
    onEdgeSchemaCreationCreateClick: () -> Unit, // UPDATED: State is now in ViewModel
    onEdgeSchemaTableNameChange: (String) -> Unit,
    onEdgeSchemaCreationAddConnection: (String, String) -> Unit,
    onEdgeSchemaCreationRemoveConnection: (Int) -> Unit,
    onEdgeSchemaPropertyChange: (Int, SchemaProperty) -> Unit, // UPDATED: Parameter type
    onAddEdgeSchemaProperty: () -> Unit,
    onRemoveEdgeSchemaProperty: (Int) -> Unit,

    // Node Edit Handlers
    onNodeEditPropertyChange: (String, String) -> Unit, // UPDATED: Key is String
    onNodeEditClusterChange: (ClusterDisplayItem?) -> Unit,

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
    allNodeSchemaNames: List<String>, // ADDED: Needed for edge schema editors

    // --- ADDED: Cluster Creation ---
    onClusterCreationSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onClusterCreationPropertyChanged: (String, String) -> Unit,
    onClusterCreationCreateClick: () -> Unit,

    // --- ADDED: Cluster Schema Creation ---
    onClusterSchemaCreationCreateClick: () -> Unit,
    onClusterSchemaTableNameChange: (String) -> Unit,
    onClusterSchemaPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddClusterSchemaProperty: () -> Unit,
    onRemoveClusterSchemaProperty: (Int) -> Unit,

    // --- ADDED: Cluster Edit ---
    onClusterEditPropertyChange: (String, String) -> Unit,

    // --- ADDED: Cluster Schema Edit ---
    onClusterSchemaEditLabelChange: (String) -> Unit,
    onClusterSchemaEditPropertyChange: (Int, SchemaProperty) -> Unit,
    onClusterSchemaEditAddProperty: () -> Unit,
    onClusterSchemaEditRemoveProperty: (Int) -> Unit
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
                onConnectionSelected = onEdgeCreationConnectionSelected,
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
                onCreate = { onNodeSchemaCreationCreateClick() }, // UPDATED
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
                onCreate = { onEdgeSchemaCreationCreateClick() }, // UPDATED
                onCancel = onCancelClick
            )
        }
        is EditScreenState.EditNode -> {
            EditNodeView(
                state = editScreenState.state,
                onPropertyChange = onNodeEditPropertyChange,
                onClusterChange = onNodeEditClusterChange,
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
                onCancel = onCancelClick,
                onAddConnection = onEdgeSchemaEditAddConnection, // ADDED
                onRemoveConnection = onEdgeSchemaEditRemoveConnection, // ADDED
                allNodeSchemaNames = allNodeSchemaNames // ADDED
            )
        }
        // --- ADDED: All Cluster Views ---
        is EditScreenState.CreateCluster -> {
            // TODO: Create CreateClusterView.kt
            // For now, using a placeholder
            Text("Create Cluster View Placeholder")
//            CreateClusterView(
//                state = editScreenState.state,
//                onSchemaSelected = onClusterCreationSchemaSelected,
//                onPropertyChanged = onClusterCreationPropertyChanged,
//                onCreateClick = onClusterCreationCreateClick,
//                onCancelClick = onCancelClick
//            )
        }
        is EditScreenState.CreateClusterSchema -> {
            // TODO: Create CreateClusterSchemaView.kt
            // For now, using a placeholder
            Text("Create Cluster Schema View Placeholder")
//            CreateClusterSchemaView(
//                state = editScreenState.state,
//                onTableNameChange = onClusterSchemaTableNameChange,
//                onPropertyChange = onClusterSchemaPropertyChange,
//                onAddProperty = onAddClusterSchemaProperty,
//                onRemoveProperty = onRemoveClusterSchemaProperty,
//                onCreate = { onClusterSchemaCreationCreateClick() },
//                onCancel = onCancelClick
//            )
        }
        is EditScreenState.EditCluster -> {
            // TODO: Create EditClusterView.kt
            // For now, using a placeholder
            Text("Edit Cluster View Placeholder")
//            EditClusterView(
//                state = editScreenState.state,
//                onPropertyChange = onClusterEditPropertyChange,
//                onSave = onSaveClick,
//                onCancel = onCancelClick
//            )
        }
        is EditScreenState.EditClusterSchema -> {
            // TODO: Create EditClusterSchemaView.kt
            // For now, using a placeholder
            Text("Edit Cluster Schema View Placeholder")
//            EditClusterSchemaView(
//                state = editScreenState.state,
//                onLabelChange = onClusterSchemaEditLabelChange,
//                onPropertyChange = onClusterSchemaEditPropertyChange,
//                onAddProperty = onClusterSchemaEditAddProperty,
//                onRemoveProperty = onRemoveClusterSchemaEditRemoveProperty,
//                onSave = onSaveClick,
//                onCancel = onCancelClick
//            )
        }
        // --- END ADDED ---
        is EditScreenState.None -> {
            Text("No item selected to edit.")
        }
    }
}

