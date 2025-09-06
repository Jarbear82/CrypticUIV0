package com.tau.cryptic_ui_v0

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel for controls and metadata
        LazyColumn(modifier = Modifier.width(200.dp).padding(16.dp)) {
            item {
                metaData?.let {
                    Text("Database Info", style = MaterialTheme.typography.headlineSmall)
                    Text("Name: ${it.name}")
                    Text("Version: ${it.version}")
                    Text("Storage: ${it.storage}")
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
                Button(onClick = { viewModel.showSchema() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Show Schema")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.listNodes() }, modifier = Modifier.fillMaxWidth()) {
                    Text("List Nodes")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.listEdges() }, modifier = Modifier.fillMaxWidth()) {
                    Text("List Edges")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.listAll() }, modifier = Modifier.fillMaxWidth()) {
                    Text("List All")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
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
                Button(onClick = { viewModel.executeQuery() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Execute")
                }
            }
        }

        // Middle panel for query results
        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Text("Query Result", style = MaterialTheme.typography.headlineSmall)
            QueryResultView(queryResult)
        }

        // Right panel for schema
        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Text("Schema", style = MaterialTheme.typography.headlineSmall)
            SchemaView(schema)
        }
    }
}