package com.tau.nexus_note.codex.schema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.codex.SearchableListHeader
import com.tau.nexus_note.codex.schema.SchemaData
import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.utils.labelToColor

@Composable
fun SchemaView(
    schema: SchemaData?,
    primarySelectedItem: Any?,
    secondarySelectedItem: Any?,
    onNodeClick: (SchemaDefinitionItem) -> Unit,
    onEdgeClick: (SchemaDefinitionItem) -> Unit,
    onEditNodeClick: (SchemaDefinitionItem) -> Unit,
    onEditEdgeClick: (SchemaDefinitionItem) -> Unit,
    onDeleteNodeClick: (SchemaDefinitionItem) -> Unit,
    onDeleteEdgeClick: (SchemaDefinitionItem) -> Unit,
    onAddNodeSchemaClick: () -> Unit,
    onAddEdgeSchemaClick: () -> Unit,
    onAddNodeClick: (SchemaDefinitionItem) -> Unit,
    onAddEdgeClick: (SchemaDefinitionItem, ConnectionPair) -> Unit,
    nodeSchemaSearchText: String,
    onNodeSchemaSearchChange: (String) -> Unit,
    edgeSchemaSearchText: String,
    onEdgeSchemaSearchChange: (String) -> Unit,
    schemaVisibility: Map<Long, Boolean>,
    onToggleSchemaVisibility: (Long) -> Unit
) {
    if (schema == null) {
        Text("Schema not loaded.")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            // --- Node Schemas ---
            SearchableListHeader(
                title = "Node Schemas:",
                searchText = nodeSchemaSearchText,
                onSearchTextChange = onNodeSchemaSearchChange,
                onAddClick = onAddNodeSchemaClick,
                addContentDescription = "New Node Schema",
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = "Node Schema",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            LazyColumn {
                val filteredNodeSchemas = schema.nodeSchemas.filter {
                    it.name.contains(nodeSchemaSearchText, ignoreCase = true)
                }
                items(filteredNodeSchemas, key = { it.id }) { table ->
                    val isSelected = primarySelectedItem == table

                    SchemaListItem(
                        item = table,
                        isSelected = isSelected,
                        onClick = { onNodeClick(table) },
                        modifier = Modifier.padding(top = 4.dp, bottom = 0.dp, start = 10.dp, end = 10.dp),
                        content = { fontColor ->
                            NodeSchemaContent(table = table, fontColor = fontColor)
                        },
                        actions = { fontColor ->
                            NodeSchemaActions(
                                table = table,
                                fontColor = fontColor,
                                schemaVisibility = schemaVisibility,
                                onToggleSchemaVisibility = onToggleSchemaVisibility,
                                onAddNodeClick = onAddNodeClick,
                                onEditNodeClick = onEditNodeClick,
                                onDeleteNodeClick = onDeleteNodeClick
                            )
                        }
                    )
                }
            }
        }

        // --- Edge Schemas ---
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            SearchableListHeader(
                title = "Edge Schemas:",
                searchText = edgeSchemaSearchText,
                onSearchTextChange = onEdgeSchemaSearchChange,
                onAddClick = onAddEdgeSchemaClick,
                addContentDescription = "New Edge Schema",
                leadingContent = {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = "Edge Schema",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            LazyColumn {
                val filteredEdgeSchemas = schema.edgeSchemas.filter {
                    it.name.contains(edgeSchemaSearchText, ignoreCase = true)
                }
                items(filteredEdgeSchemas, key = { it.id }) { table ->
                    val isSelected = primarySelectedItem == table

                    SchemaListItem(
                        item = table,
                        isSelected = isSelected,
                        onClick = { onEdgeClick(table) },
                        modifier = Modifier.padding(top = 4.dp, bottom = 0.dp, start = 10.dp, end = 10.dp),
                        content = { fontColor ->
                            EdgeSchemaContent(
                                table = table,
                                fontColor = fontColor,
                                onAddEdgeClick = onAddEdgeClick
                            )
                        },
                        actions = { fontColor ->
                            EdgeSchemaActions(
                                table = table,
                                fontColor = fontColor,
                                schemaVisibility = schemaVisibility,
                                onToggleSchemaVisibility = onToggleSchemaVisibility,
                                onEditEdgeClick = onEditEdgeClick,
                                onDeleteEdgeClick = onDeleteEdgeClick
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * A reusable composable that provides the common frame (background, border, title, dividers)
 * for a schema list item, accepting unique content and actions via slots.
 */
@Composable
private fun SchemaListItem(
    item: SchemaDefinitionItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (fontColor: Color) -> Unit,
    actions: @Composable (fontColor: Color) -> Unit
) {
    val colorInfo = labelToColor(item.name)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(colorInfo.composeColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            .then(modifier) // Apply caller-specific padding
    ) {
        // Title
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
            content = {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = colorInfo.composeFontColor,
                )
            }
        )
        // Using outline is more semantically correct for a divider
        HorizontalDivider(color = colorInfo.composeFontColor)

        // Unique Content Slot
        content(colorInfo.composeFontColor)

        // Using outline is more semantically correct for a divider
        HorizontalDivider(color = colorInfo.composeFontColor)

        // Unique Actions Slot
        actions(colorInfo.composeFontColor)
    }
}

/**
 * Displays the specific content for a Node Schema (its properties).
 */
@Composable
private fun NodeSchemaContent(table: SchemaDefinitionItem, fontColor: Color) {
    Text(
        text = table.properties.joinToString(separator = "\n") { prop ->
            val suffix = if (prop.isDisplayProperty) ": (Display)" else ""
            "  - ${prop.name}: ${prop.type}$suffix"
        },
        style = MaterialTheme.typography.bodyLarge,
        color = fontColor
    )
}

/**
 * Displays the specific action buttons for a Node Schema.
 */
@Composable
private fun NodeSchemaActions(
    table: SchemaDefinitionItem,
    fontColor: Color,
    schemaVisibility: Map<Long, Boolean>,
    onToggleSchemaVisibility: (Long) -> Unit,
    onAddNodeClick: (SchemaDefinitionItem) -> Unit,
    onEditNodeClick: (SchemaDefinitionItem) -> Unit,
    onDeleteNodeClick: (SchemaDefinitionItem) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onToggleSchemaVisibility(table.id) }) {
            val isVisible = schemaVisibility[table.id] ?: true
            Icon(
                imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (isVisible) "Hide Schema" else "Show Schema",
                tint = fontColor,
            )
        }
        IconButton(onClick = { onAddNodeClick(table) }) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New Node",
                tint = fontColor,
            )
        }
        IconButton(onClick = { onEditNodeClick(table) }) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit Node Schema",
                tint = fontColor,
            )
        }
        IconButton(onClick = { onDeleteNodeClick(table) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete Node Schema",
                tint = fontColor,
            )
        }
    }
}

/**
 * Displays the specific content for an Edge Schema (its connection pairs).
 */
@Composable
private fun EdgeSchemaContent(
    table: SchemaDefinitionItem,
    fontColor: Color,
    onAddEdgeClick: (SchemaDefinitionItem, ConnectionPair) -> Unit
) {
    Column {
        (table.connections ?: emptyList()).forEach {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "(${it.src}) -> (${it.dst})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = fontColor
                )
                IconButton(
                    onClick = { onAddEdgeClick(table, it) },
                    modifier = Modifier.wrapContentHeight()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Edge",
                        tint = fontColor
                    )
                }
            }
        }
    }
}

/**
 * Displays the specific action buttons for an Edge Schema.
 */
@Composable
private fun EdgeSchemaActions(
    table: SchemaDefinitionItem,
    fontColor: Color,
    schemaVisibility: Map<Long, Boolean>,
    onToggleSchemaVisibility: (Long) -> Unit,
    onEditEdgeClick: (SchemaDefinitionItem) -> Unit,
    onDeleteEdgeClick: (SchemaDefinitionItem) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onToggleSchemaVisibility(table.id) }) {
            val isVisible = schemaVisibility[table.id] ?: true
            Icon(
                imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (isVisible) "Hide Schema" else "Show Schema",
                tint = fontColor
            )
        }
        IconButton(onClick = { onEditEdgeClick(table) }) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit Edge Schema",
                tint = fontColor
            )
        }
        IconButton(onClick = { onDeleteEdgeClick(table) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete Edge Schema",
                tint = fontColor
            )
        }
    }
}