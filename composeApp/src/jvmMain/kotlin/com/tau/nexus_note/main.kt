package com.tau.nexus_note

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    // System.setProperty("compose.swing.render.on.graphics", "true")
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Nexus Note",
    ) {
        App()
    }
}