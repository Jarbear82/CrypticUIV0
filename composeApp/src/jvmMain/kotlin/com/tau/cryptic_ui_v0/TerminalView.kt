package com.tau.cryptic_ui_v0

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    val schema by viewModel.schema.collectAsState()
    val queryResult by viewModel.queryResult.collectAsState()
    val metaData by viewModel.dbMetaData.collectAsState()
    var query by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(viewModel.query.value) {
        query = query.copy(text = viewModel.query.value)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {

            // ------------------ Left panel for controls and query results --------------------------

            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.showSchema() }) {
                            Text("Show Schema")
                        }
                        Button(onClick = { viewModel.listNodes() }) {
                            Text("List Nodes")
                        }
                        Button(onClick = { viewModel.listEdges() }) {
                            Text("List Edges")
                        }
                        Button(onClick = { viewModel.listAll() }) {
                            Text("List All")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.query.value = it.text
                        },
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

            Column(modifier = Modifier.width(300.dp).padding(16.dp)) {
                var tabIndex by remember { mutableStateOf(0) }
                val tabs = listOf("Schema", "Metadata")

                TabRow(selectedTabIndex = tabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title) },
                            selected = tabIndex == index,
                            onClick = { tabIndex = index }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (tabIndex) {
                    0 -> SchemaView(schema)
                    1 -> {
                        metaData?.let {
                            Text(
                                "Database Info",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text("Name: ${it.name}")
                            Text("Version: ${it.version}")
                            Text("Storage: ${it.storage}")
                        }
                    }
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