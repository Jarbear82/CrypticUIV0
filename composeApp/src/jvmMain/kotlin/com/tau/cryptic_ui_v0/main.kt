package com.tau.cryptic_ui_v0

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cryptic UI V0",
    ) {
        App()
    }
}