package com.tau.cryptic_ui_v0

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun TerminalView(viewModel: TerminalViewModel) {
    val schema by viewModel.schema.collectAsState()
    val queryResult by viewModel.queryResult.collectAsState()
    var query by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(viewModel.query.value) {
        query = query.copy(text = viewModel.query.value)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel for controls
        Column(modifier = Modifier.width(200.dp).padding(16.dp)) {
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
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.query.value = it.text
                },
                label = { Text("Cypher Query") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.executeQuery() }, modifier = Modifier.fillMaxWidth()) {
                Text("Execute")
            }
        }

        // Right panel for results
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (schema != null) {
                Text("Schema", style = MaterialTheme.typography.headlineSmall)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(schema ?: "Schema not loaded")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Query Result", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text(queryResult)
                }
            }
        }
    }
}