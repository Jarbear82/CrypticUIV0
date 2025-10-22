package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.ExecutionResult

@Composable
fun QueryView(result: ExecutionResult, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Query Result", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            when (result) {
                is ExecutionResult.Success -> {
                    if (result.results.isEmpty() || result.results.all { it.rows.isEmpty() }) {
                        Text("Query executed successfully, but returned no data.")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            items(result.results) { formattedResult ->
                                if (formattedResult.headers.isNotEmpty() && formattedResult.rows.isNotEmpty()) {
                                    val scrollState = rememberScrollState()
                                    Column(Modifier.horizontalScroll(scrollState)) {
                                        // Calculate column widths based on content
                                        val columnWidths = mutableMapOf<Int, Int>()
                                        formattedResult.headers.forEachIndexed { index, header ->
                                            columnWidths[index] = header.length
                                        }
                                        formattedResult.rows.forEach { row ->
                                            row.forEachIndexed { index, cell ->
                                                val cellLength = cell?.toString()?.length ?: 4
                                                if (cellLength > (columnWidths[index] ?: 0)) {
                                                    columnWidths[index] = cellLength
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                                            // Header
                                            Row {
                                                formattedResult.headers.forEachIndexed { index, header ->
                                                    Text(
                                                        text = header,
                                                        modifier = Modifier
                                                            .defaultMinSize(minWidth = (columnWidths[index]
                                                                ?: 0).dp * 8)
                                                            .padding(4.dp),
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                }
                                            }

                                            // Rows
                                            formattedResult.rows.forEach { row ->
                                                Row {
                                                    row.forEachIndexed { index, cell ->
                                                        Text(
                                                            text = cell?.toString() ?: "null",
                                                            modifier = Modifier
                                                                .defaultMinSize(minWidth = (columnWidths[index]
                                                                    ?: 0).dp * 8)
                                                                .padding(4.dp),
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is ExecutionResult.Error -> {
                    Text(
                        "Error: ${result.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}