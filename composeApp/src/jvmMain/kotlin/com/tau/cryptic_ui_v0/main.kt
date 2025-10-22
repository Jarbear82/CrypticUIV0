package com.tau.cryptic_ui_v0

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    System.setProperty("compose.swing.render.on.graphics", "true")
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cryptic UI", // Changed from "Cryptic UI V0"
    ) {
        App()
    }
}