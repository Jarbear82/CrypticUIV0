package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.viewmodels.TerminalViewModel
import com.tau.cryptic_ui_v0.viewmodels.TerminalViewTabs
import kotlinx.coroutines.launch

@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    val schema by viewModel.schemaViewModel.schema.collectAsState()
    val queryResult by viewModel.queryViewModel.queryResult.collectAsState()
    val metaData by viewModel.metadataViewModel.dbMetaData.collectAsState()
    val query by viewModel.queryViewModel.query
    val scope = rememberCoroutineScope() // Get a coroutine scope

    // Collect the state for the MetadataView
    val nodes by viewModel.metadataViewModel.nodeList.collectAsState()
    val relationships by viewModel.metadataViewModel.relationshipList.collectAsState()
    val selectedItem by viewModel.metadataViewModel.selectedItem.collectAsState()
    val nodeCreationState by viewModel.metadataViewModel.nodeCreationState.collectAsState()
    val relCreationState by viewModel.metadataViewModel.relCreationState.collectAsState()
    val nodeSchemaCreationState by viewModel.schemaViewModel.nodeSchemaCreationState.collectAsState()
    val relSchemaCreationState by viewModel.schemaViewModel.relSchemaCreationState.collectAsState()


    // Tabs
    val selectedTab by viewModel.selectedTab.collectAsState()


    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ------------------ Left panel for controls and query results --------------------------

            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                item {


                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(onClick = {
                            scope.launch { // Launch a coroutine to call the suspend function
                                viewModel.schemaViewModel.showSchema()
                            }
                        }) {
                            Text("Show Schema")
                        }

                        Button(onClick = { viewModel.metadataViewModel.listNodes() }) {
                            Text("List Nodes")
                        }

                        Button(onClick = { viewModel.metadataViewModel.listEdges() }) {
                            Text("List Rels")
                        }

                        Button(onClick = { viewModel.metadataViewModel.listAll() }) {
                            Text("List All")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.queryViewModel.onQueryChange(it) },
                        label = { Text("Cypher Query") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.queryViewModel.executeQuery() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Execute")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // --------------- Right panel for Schema and Metadata tabs ------------------------------

            Column(modifier = Modifier.width(400.dp).padding(16.dp)) {


                TabRow(selectedTabIndex = selectedTab.value) {
                    TerminalViewTabs.entries.forEach { tab ->
                        Tab(
                            text = { Text(tab.name) },
                            selected = selectedTab.value == tab.value,
                            onClick = { viewModel.selectTab(tab) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    TerminalViewTabs.METADATA -> MetadataView(
                        dbMetaData = metaData,
                        nodes = nodes,
                        relationships = relationships,
                        onNodeClick = { viewModel.metadataViewModel.selectItem(it); println("Item: $it is being selected"); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onRelationshipClick = { viewModel.metadataViewModel.selectItem(it); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onDeleteNodeClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onDeleteRelClick = { viewModel.metadataViewModel.deleteDisplayItem(it) },
                        onAddNodeClick = { viewModel.metadataViewModel.initiateNodeCreation(); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onAddRelClick = { viewModel.metadataViewModel.initiateRelCreation(); viewModel.selectTab(TerminalViewTabs.SELECTED) }
                    )
                    TerminalViewTabs.SCHEMA -> SchemaView(
                        schema = schema,
                        onNodeClick = { viewModel.metadataViewModel.selectItem(it); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onRelationshipClick = { viewModel.metadataViewModel.selectItem(it); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onDeleteNodeClick = { viewModel.schemaViewModel.deleteSchemaNode(it) },
                        onDeleteRelClick = { viewModel.schemaViewModel.deleteSchemaRel(it) },
                        onAddNodeSchemaClick = { viewModel.metadataViewModel.initiateNodeSchemaCreation(); viewModel.selectTab(TerminalViewTabs.SELECTED) },
                        onAddRelSchemaClick = { viewModel.metadataViewModel.initiateRelSchemaCreation(); viewModel.selectTab(TerminalViewTabs.SELECTED) }
                    )
                    TerminalViewTabs.SELECTED -> SelectedItemView(
                        selectedItem = selectedItem,
                        nodeCreationState = nodeCreationState,
                        relCreationState = relCreationState,
                        nodeSchemaCreationState = nodeSchemaCreationState,
                        relSchemaCreationState = relSchemaCreationState,
                        onClearSelection = { viewModel.metadataViewModel.clearSelectedItem(); viewModel.selectTab(TerminalViewTabs.METADATA) },
                        onNodeCreationSchemaSelected = { viewModel.metadataViewModel.updateNodeCreationSchema(it) },
                        onNodeCreationPropertyChanged = { key, value ->
                            viewModel.metadataViewModel.updateNodeCreationProperty(
                                key,
                                value
                            )
                        },
                        onNodeCreationCreateClick = { viewModel.metadataViewModel.createNodeFromState(); viewModel.selectTab(TerminalViewTabs.METADATA) },
                        onNodeCreationCancelClick = { viewModel.metadataViewModel.cancelNodeCreation(); viewModel.selectTab(TerminalViewTabs.METADATA) },
                        onRelCreationSchemaSelected = { viewModel.metadataViewModel.updateRelCreationSchema(it) },
                        onRelCreationSrcSelected = { viewModel.metadataViewModel.updateRelCreationSrc(it) },
                        onRelCreationDstSelected = { viewModel.metadataViewModel.updateRelCreationDst(it) },
                        onRelCreationPropertyChanged = { key, value ->
                            viewModel.metadataViewModel.updateRelCreationProperty(
                                key,
                                value
                            )
                        },
                        onRelCreationCreateClick = { viewModel.metadataViewModel.createRelFromState(); viewModel.selectTab(TerminalViewTabs.METADATA) },
                        onRelCreationCancelClick = { viewModel.metadataViewModel.cancelRelCreation(); viewModel.selectTab(TerminalViewTabs.METADATA) },
                        onNodeSchemaCreationCreateClick = { viewModel.schemaViewModel.createNodeSchemaFromState(it) { viewModel.metadataViewModel.clearSelectedItem(); viewModel.selectTab(TerminalViewTabs.SCHEMA)} },
                        onNodeSchemaCreationCancelClick = { viewModel.metadataViewModel.cancelNodeSchemaCreation(); viewModel.selectTab(TerminalViewTabs.SCHEMA) },
                        onRelSchemaCreationCreateClick = { viewModel.schemaViewModel.createRelSchemaFromState(it) { viewModel.metadataViewModel.clearSelectedItem(); viewModel.selectTab(TerminalViewTabs.SCHEMA)} },
                        onRelSchemaCreationCancelClick = { viewModel.metadataViewModel.cancelRelSchemaCreation(); viewModel.selectTab(TerminalViewTabs.SCHEMA) },
                        onNodeSchemaTableNameChange = { viewModel.schemaViewModel.onNodeSchemaTableNameChange(it) },
                        onNodeSchemaPropertyChange = { index, property -> viewModel.schemaViewModel.onNodeSchemaPropertyChange(index, property) },
                        onAddNodeSchemaProperty = { viewModel.schemaViewModel.onAddNodeSchemaProperty() },
                        onRemoveNodeSchemaProperty = { viewModel.schemaViewModel.onRemoveNodeSchemaProperty(it) },
                        onRelSchemaTableNameChange = { viewModel.schemaViewModel.onRelSchemaTableNameChange(it) },
                        onRelSchemaSrcTableChange = { viewModel.schemaViewModel.onRelSchemaSrcTableChange(it) },
                        onRelSchemaDstTableChange = { viewModel.schemaViewModel.onRelSchemaDstTableChange(it) },
                        onRelSchemaPropertyChange = { index, property -> viewModel.schemaViewModel.onRelSchemaPropertyChange(index, property) },
                        onAddRelSchemaProperty = { viewModel.schemaViewModel.onAddRelSchemaProperty() },
                        onRemoveRelSchemaProperty = { viewModel.schemaViewModel.onRemoveRelSchemaProperty(it) }
                    )
                }
            }
        }

        queryResult?.let {
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            ) {
                QueryView(it) {
                    viewModel.queryViewModel.clearQueryResult()
                }
            }
        }
    }
}