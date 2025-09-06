package com.tau.cryptic_ui_v0

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun QueryResultView(result: ExecutionResult?) {
    when (result) {
        is ExecutionResult.Success -> {
            if (result.results.isEmpty() || result.results.all { it.rows.isEmpty() }) {
                Text("Query executed successfully, but returned no data.")
            } else {
                val scrollState = rememberScrollState()
                Column(Modifier.horizontalScroll(scrollState)) {
                    result.results.forEach { formattedResult ->
                        if (formattedResult.headers.isNotEmpty() && formattedResult.rows.isNotEmpty()) {
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
                                                .defaultMinSize(minWidth = (columnWidths[index] ?: 0).dp * 8)
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
                                                    .defaultMinSize(minWidth = (columnWidths[index] ?: 0).dp * 8)
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
        is ExecutionResult.Error -> {
            Text("Error: ${result.message}", color = MaterialTheme.colorScheme.error)
        }
        null -> {
            Text("No query has been executed yet.")
        }
    }
}