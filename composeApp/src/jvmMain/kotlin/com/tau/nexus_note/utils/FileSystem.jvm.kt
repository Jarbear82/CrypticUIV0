package com.tau.nexus_note.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import javax.swing.JFileChooser

/**
 * JVM implementation for getting the user's home directory.
 */
actual fun getHomeDirectoryPath(): String {
    return System.getProperty("user.home")
}

/**
 * JVM implementation for the directory picker using JFileChooser.
 */
@Composable
actual fun DirectoryPicker(
    show: Boolean,
    title: String,
    initialDirectory: String,
    onResult: (String?) -> Unit
) {
    // This runs on the main (AWT Event Dispatch) thread
    LaunchedEffect(show) {
        if (show) {
            val fileChooser = JFileChooser(initialDirectory).apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = title
                isAcceptAllFileFilterUsed = false
            }

            val result = fileChooser.showOpenDialog(null) // null for parent frame
            if (result == JFileChooser.APPROVE_OPTION) {
                onResult(fileChooser.selectedFile.absolutePath)
            } else {
                onResult(null) // User cancelled
            }
        }
    }
}

/**
 * JVM implementation for listing files with a specific extension.
 */
actual fun listFilesWithExtension(path: String, extension: String): List<String> {
    return try {
        File(path).listFiles { file ->
            file.isFile && file.name.endsWith(extension)
        }?.map { it.absolutePath } ?: emptyList()
    } catch (e: Exception) {
        println("Error listing files: ${e.message}")
        emptyList()
    }
}

/**
 * JVM implementation for getting a file name.
 */
actual fun getFileName(path: String): String {
    return File(path).name
}

/**
 * JVM implementation for checking if a file exists.
 */
actual fun fileExists(path: String): Boolean {
    return File(path).exists()
}