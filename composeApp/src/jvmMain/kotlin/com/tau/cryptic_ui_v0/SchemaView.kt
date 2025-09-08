package com.tau.cryptic_ui_v0

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SchemaView(schema: Schema?) {
    if (schema == null) {
        Text("Schema not loaded.")
        return
    }

    LazyColumn {
        item {
            Text("Node Tables", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
        }
        items(schema.nodeTables) { table ->
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("- ${table.label}", style = MaterialTheme.typography.titleMedium)
                table.properties.forEach { prop ->
                    Text("  - ${prop.key}: ${prop.valueDataType}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Text("Relationship Tables", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        }
        items(schema.relTables) { table ->
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("- ${table.label} (${table.srcLabel} -> ${table.dstLabel})", style = MaterialTheme.typography.titleMedium)
                table.properties.forEach { prop ->
                    Text("  - ${prop.key}: ${prop.valueDataType}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}