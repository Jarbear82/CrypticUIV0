package com.tau.cryptic_ui_v0.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.EdgeDisplayItem
import com.tau.cryptic_ui_v0.NodeDisplayItem
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.delay
import org.cef.browser.CefRendering
import java.awt.Color

// --- Define HTML content ---

private val helloWorldHtml = """
    <html>
    <head>
        <title>Hello</title>
        <style>
            body, html {
                margin: 0; padding: 0; width: 100%; height: 100%;
                display: flex; align-items: center; justify-content: center;
                background-color: #FFFFFF; color: #000000;
                font-family: sans-serif; font-size: 2em;
            }
        </style>
    </head>
    <body><h1>Hello World!</h1></body>
    </html>
""".trimIndent()

// -- vis.js example HTML --
private val visExampleHtml = """
    <!doctype html>
    <html lang="en">
      <head>
        <title>Vis Network | Basic usage</title>
        <script type="text/javascript" src="https://unpkg.com/vis-network/standalone/umd/vis-network.min.js"></script>
        <style type="text/css">
          body, html { margin: 0; padding: 0; overflow: hidden; background-color: #2b2b2b; }
          #mynetwork {
            width: 100vw; height: 100vh; border: 1px solid lightgray;
            background-color: #f0f0f0; /* Light background for vis example */
          }
           p { color: #eeeeee; padding: 10px; }
        </style>
      </head>
      <body>
        <p>Create a simple network with some nodes and edges.</p>
        <div id="mynetwork"></div>
        <script type="text/javascript">
          var nodes = new vis.DataSet([ { id: 1, label: "Node 1" }, { id: 2, label: "Node 2" }, { id: 3, label: "Node 3" }, { id: 4, label: "Node 4" }, { id: 5, label: "Node 5" }, ]);
          var edges = new vis.DataSet([ { from: 1, to: 3 }, { from: 1, to: 2 }, { from: 2, to: 4 }, { from: 2, to: 5 }, { from: 3, to: 3 }, ]);
          var container = document.getElementById("mynetwork");
          var data = { nodes: nodes, edges: edges };
          var options = {};
          var network = new vis.Network(container, data, options);
        </script>
      </body>
    </html>
""".trimIndent()

// -- HTML template for dynamic graph rendering --
private val dynamicVisHtml = """
    <html>
    <head>
        <title>Dynamic Graph View</title>
        <script type="text/javascript" src="https://unpkg.com/vis-network/standalone/umd/vis-network.min.js"></script>
        <style type="text/css">
            #mynetwork { width: 100%; height: 100vh; border: 1px solid lightgray; background-color: #FFFFFF; }
            body, html { margin: 0; padding: 0; overflow: hidden; background-color: #FFFFFF; }
        </style>
    </head>
    <body>
        <div id="mynetwork"></div>
        <script type="text/javascript">
            var container = document.getElementById('mynetwork');
            var nodes = new vis.DataSet([]);
            var edges = new vis.DataSet([]);
            var data = { nodes: nodes, edges: edges };
            var options = {
    layout: { randomSeed: 2 },
    physics: { 
        enabled: true, 
        solver: 'forceAtlas2Based',
        forceAtlas2Based: {
            // Increase the length of the edges (springs)
            springLength: 200, 
            // Increase the repulsion force between nodes
            gravitationalConstant: -100, 
        }
    },
    nodes: {
        shape: 'box'
    },
    edges: {
        arrows: 'to', 
        color: { color: '#848484', highlight: '#6075f2' }, 
        font: { align: 'top', color: '#000000' }, 
    }
};
            var network = new vis.Network(container, data, options);

            // Function to be called from Kotlin to update the graph
            function setGraphData(nodesJson, edgesJson) {
                console.log("Setting graph data. Nodes:", nodesJson, "Edges:", edgesJson);
                try {
                    nodes.clear();
                    edges.clear();
                    // vis.js expects arrays, ensure the input is parsed correctly
                    const nodesToAdd = typeof nodesJson === 'string' ? JSON.parse(nodesJson) : nodesJson;
                    const edgesToAdd = typeof edgesJson === 'string' ? JSON.parse(edgesJson) : edgesJson;
                    nodes.add(nodesToAdd);
                    edges.add(edgesToAdd);
                    network.fit(); // Zoom to fit all nodes
                } catch (e) {
                    console.error("Error setting graph data:", e);
                    return e.message;
                }
                return "Data loaded successfully";
            }
        </script>
    </body>
    </html>
""".trimIndent()

// Enum to represent the content types
private enum class DisplayContent { HELLO_WORLD, GOOGLE, VIS_EXAMPLE, DYNAMIC_VIS }

/**
 * Stores the generated hex color and its raw RGB components.
 */
private data class NodeColorInfo(val hex: String, val rgb: IntArray)

/**
 * Generates a consistent hex color string and RGB array from a label string.
 */
private fun labelToColor(label: String): NodeColorInfo {
    val hash = label.hashCode()
    val r = (hash shr 16) and 0xFF
    val g = (hash shr 8) and 0xFF
    val b = hash and 0xFF
    val hex = String.format("#%02X%02X%02X", r, g, b)
    return NodeColorInfo(hex, intArrayOf(r, g, b))
}

/**
 * NEW: Calculates perceived luminance and returns black or white for best contrast.
 */
private fun getFontColor(rgb: IntArray): String {
    // Standard luminance formula
    val luminance = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255
    // Use white text on dark backgrounds, black text on light backgrounds
    return if (luminance < 0.5) "#FFFFFF" else "#000000"
}


// Simple serializer for the graph data to pass to JavaScript
// Uses JSON syntax directly, escaping quotes within labels if necessary
private fun serializeNodes(nodes: List<NodeDisplayItem>): String {
    return nodes.joinToString(prefix = "[", postfix = "]") {
        // Use primary key value (converted to string) as the unique ID for vis.js
        val id = it.primarykeyProperty.value?.toString() ?: "null_${nodes.indexOf(it)}" // Fallback ID
        val nodeLabel = it.label // Get the base label for hashing
        val displayLabel = "${it.label}\\n(${it.primarykeyProperty.value})".replace("'", "\\'") // Escape single quotes for JS

        // UPDATED: Get background color AND complementary font color
        val colorInfo = labelToColor(nodeLabel)
        val bgColor = colorInfo.hex
        val fontColor = getFontColor(colorInfo.rgb)

        val colorJs = "{ background: '$bgColor', border: '$bgColor', highlight: { background: '$bgColor', border: '$bgColor' } }"
        val fontJs = "{ color: '$fontColor' }" // NEW: Define font color for this node

        "{ id: '$id', label: '$displayLabel', color: $colorJs, font: $fontJs }"
    }
}

private fun serializeEdges(edges: List<EdgeDisplayItem>): String {
    return edges.joinToString(prefix = "[", postfix = "]") {
        val fromId = it.src.primarykeyProperty.value?.toString() ?: "null_src_${edges.indexOf(it)}"
        val toId = it.dst.primarykeyProperty.value?.toString() ?: "null_dst_${edges.indexOf(it)}"
        val label = it.label.replace("'", "\\'")
        "{ from: '$fromId', to: '$toId', label: '$label' }"
    }
}


@Composable
fun GraphView(nodes: List<NodeDisplayItem>, edges: List<EdgeDisplayItem>) {
    val client = remember { KCEF.newClientBlocking() }
    var currentContent by remember { mutableStateOf(DisplayContent.HELLO_WORLD) }
    // Trigger for dynamic graph update
    var updateDynamicGraphTrigger by remember { mutableStateOf(0) }

    val browser = remember {
        client.createBrowser("about:blank", CefRendering.DEFAULT, false)
    }

    // Load content when the state changes
    LaunchedEffect(currentContent, updateDynamicGraphTrigger) {
        when (currentContent) {
            DisplayContent.HELLO_WORLD -> browser.loadHtml(helloWorldHtml, "file:///hello.html")
            DisplayContent.GOOGLE -> browser.loadURL("https://google.com")
            DisplayContent.VIS_EXAMPLE -> browser.loadHtml(visExampleHtml, "file:///vis_example.html")
            DisplayContent.DYNAMIC_VIS -> {
                // 1. Load the base HTML template
                browser.loadHtml(dynamicVisHtml, "file:///dynamic_graph.html")

                // 2. Wait a bit for the page and JS to load.
                // NOTE: A more robust solution might involve JS calling back to Kotlin.
                delay(500) // Adjust delay as needed

                // 3. Serialize current data
                val nodesJsonString = serializeNodes(nodes)
                val edgesJsonString = serializeEdges(edges)

                // 4. Inject data into the loaded page
                // We pass the JSON as strings to the JS function
                browser.evaluateJavaScript("setGraphData($nodesJsonString, $edgesJsonString)") {
                    if (it != null) {
                        println("Dynamic Graph JS Result: $it")
                    } else {
                        println("Dynamic Graph JS call returned null (maybe an error?)")
                    }
                }
            }
        }
    }

    // Dispose the client properly
    DisposableEffect(Unit) {
        onDispose {
            client.dispose()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Buttons to switch content ---
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Button(onClick = { currentContent = DisplayContent.HELLO_WORLD }) {
                Text("Hello World")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { currentContent = DisplayContent.GOOGLE }) {
                Text("Google")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { currentContent = DisplayContent.VIS_EXAMPLE }) {
                Text("Vis.js Example")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                // Set the content type and trigger the LaunchedEffect update
                currentContent = DisplayContent.DYNAMIC_VIS
                updateDynamicGraphTrigger++ // Increment to ensure LaunchedEffect runs
            }) {
                Text("Show Current Graph")
            }
        }

        // --- Browser View ---
        SwingPanel(
            factory = { browser.uiComponent },
            modifier = Modifier.fillMaxSize().weight(1f) // Make panel take remaining space
        )
    }
}