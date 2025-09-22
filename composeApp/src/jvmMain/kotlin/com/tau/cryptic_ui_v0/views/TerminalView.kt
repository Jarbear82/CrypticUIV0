package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.TerminalViewModel
import kotlinx.coroutines.launch

@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    val schema by viewModel.schema.collectAsState()
    val queryResult by viewModel.queryResult.collectAsState()
    val metaData by viewModel.dbMetaData.collectAsState()
    val query by viewModel.query
    val scope = rememberCoroutineScope() // Get a coroutine scope

    // Collect the state for the MetadataView
    val nodes by viewModel.nodeList.collectAsState()
    val relationships by viewModel.relationshipList.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val nodeCreationState by viewModel.nodeCreationState.collectAsState()
    val relCreationState by viewModel.relCreationState.collectAsState()
    val nodeSchemaCreationState by viewModel.nodeSchemaCreationState.collectAsState()
    val relSchemaCreationState by viewModel.relSchemaCreationState.collectAsState()


    // Tabs
    var selectedTab by remember { mutableStateOf(TerminalViewTabs.METADATA) }

    fun selectMetadata() { selectedTab = TerminalViewTabs.METADATA }
    fun selectSchema() { selectedTab = TerminalViewTabs.SCHEMA }
    fun selectSelected() { selectedTab = TerminalViewTabs.SELECTED }


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
                                viewModel.showSchema()
                            }
                        }) {
                            Text("Show Schema")
                        }

                        Button(onClick = { viewModel.listNodes() }) {
                            Text("List Nodes")
                        }

                        Button(onClick = { viewModel.listEdges() }) {
                            Text("List Rels")
                        }

                        Button(onClick = { viewModel.listAll() }) {
                            Text("List All")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        label = { Text("Cypher Query") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.executeQuery() },
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
                            onClick = { selectedTab = tab }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    TerminalViewTabs.METADATA -> MetadataView(
                        dbMetaData = metaData,
                        nodes = nodes,
                        relationships = relationships,
                        onNodeClick = { viewModel.selectItem(it); println("Item: $it is being selected"); selectSelected() },
                        onRelationshipClick = { viewModel.selectItem(it); selectSelected() },
                        onDeleteNodeClick = { viewModel.deleteDisplayItem(it) },
                        onDeleteRelClick = { viewModel.deleteDisplayItem(it) },
                        onAddNodeClick = { viewModel.initiateNodeCreation(); selectSelected() },
                        onAddRelClick = { viewModel.initiateRelCreation(); selectSelected() }
                    )
                    TerminalViewTabs.SCHEMA -> SchemaView(
                        schema = schema,
                        onNodeClick = { viewModel.selectItem(it); selectSelected() },
                        onRelationshipClick = { viewModel.selectItem(it); selectSelected() },
                        onDeleteNodeClick = { viewModel.deleteDisplayItem(it) },
                        onDeleteRelClick = { viewModel.deleteDisplayItem(it) },
                        onAddNodeSchemaClick = { viewModel.initiateNodeSchemaCreation(); selectSelected() },
                        onAddRelSchemaClick = { viewModel.initiateRelSchemaCreation(); selectSelected() }
                    )
                    TerminalViewTabs.SELECTED -> SelectedItemView(
                        selectedItem = selectedItem,
                        nodeCreationState = nodeCreationState,
                        relCreationState = relCreationState,
                        nodeSchemaCreationState = nodeSchemaCreationState,
                        relSchemaCreationState = relSchemaCreationState,
                        onClearSelection = { viewModel.clearSelectedItem(); selectMetadata() },
                        onNodeCreationSchemaSelected = { viewModel.updateNodeCreationSchema(it) },
                        onNodeCreationPropertyChanged = { key, value ->
                            viewModel.updateNodeCreationProperty(
                                key,
                                value
                            )
                        },
                        onNodeCreationCreateClick = { viewModel.createNodeFromState(); selectMetadata() },
                        onNodeCreationCancelClick = { viewModel.cancelNodeCreation(); selectMetadata() },
                        onRelCreationSchemaSelected = { viewModel.updateRelCreationSchema(it) },
                        onRelCreationSrcSelected = { viewModel.updateRelCreationSrc(it) },
                        onRelCreationDstSelected = { viewModel.updateRelCreationDst(it) },
                        onRelCreationPropertyChanged = { key, value ->
                            viewModel.updateRelCreationProperty(
                                key,
                                value
                            )
                        },
                        onRelCreationCreateClick = { viewModel.createRelFromState(); selectMetadata() },
                        onRelCreationCancelClick = { viewModel.cancelRelCreation(); selectMetadata() },
                        onNodeSchemaCreationCreateClick = { viewModel.createNodeSchemaFromState(it); selectSchema() },
                        onNodeSchemaCreationCancelClick = { viewModel.cancelNodeSchemaCreation(); selectSchema() },
                        onRelSchemaCreationCreateClick = { viewModel.createRelSchemaFromState(it); selectSchema() },
                        onRelSchemaCreationCancelClick = { viewModel.cancelRelSchemaCreation(); selectSchema() },
                        onNodeSchemaTableNameChange = { viewModel.onNodeSchemaTableNameChange(it) },
                        onNodeSchemaPropertyChange = { index, property -> viewModel.onNodeSchemaPropertyChange(index, property) },
                        onAddNodeSchemaProperty = { viewModel.onAddNodeSchemaProperty() },
                        onRemoveNodeSchemaProperty = { viewModel.onRemoveNodeSchemaProperty(it) },
                        onRelSchemaTableNameChange = { viewModel.onRelSchemaTableNameChange(it) },
                        onRelSchemaSrcTableChange = { viewModel.onRelSchemaSrcTableChange(it) },
                        onRelSchemaDstTableChange = { viewModel.onRelSchemaDstTableChange(it) },
                        onRelSchemaPropertyChange = { index, property -> viewModel.onRelSchemaPropertyChange(index, property) },
                        onAddRelSchemaProperty = { viewModel.onAddRelSchemaProperty() },
                        onRemoveRelSchemaProperty = { viewModel.onRemoveRelSchemaProperty(it) }
                    )
                }
            }
        }

        queryResult?.let {
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            ) {
                QueryView(it) {
                    viewModel.clearQueryResult()
                }
            }
        }
    }
}



enum class TerminalViewTabs(val value: Int) {
    METADATA(0),
    SCHEMA(1),
    SELECTED(2)
}