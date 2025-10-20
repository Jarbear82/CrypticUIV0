package com.tau.cryptic_ui_v0

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.cryptic_ui_v0.viewmodels.TerminalViewModel
import com.tau.cryptic_ui_v0.views.TerminalView
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File

@Composable
@Preview
fun App() {
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    // Use a persistent folder in the user's home directory
    val bundleLocation = File(System.getProperty("user.home"), ".crypticui-kcef")    // Define installDir here so we can reuse it in the settings
    val installDir = File(bundleLocation, "kcef-bundle")

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            KCEF.init(
                builder = {
                    installDir(installDir)

                    // Add arguments to disable GPU acceleration
                    //
                    addArgs("--disable-gpu", "--disable-gpu-compositing")

                    // Explicitly set paths and disable sandbox/windowless rendering
                    //
                    settings {
                        windowlessRenderingEnabled = false
                        noSandbox = true // <-- This is the key fix
                        browserSubProcessPath = File(installDir, "jcef_helper").canonicalPath
                        resourcesDirPath = installDir.canonicalPath
                        localesDirPath = File(installDir, "locales").canonicalPath
                    }

                    progress {
                        onDownloading {
                            isDownloading = true
                            downloadProgress = it
                        }
                        onInitialized {
                            initialized = true
                        }
                    }
                },
                onError = {
                    it?.printStackTrace()
                },
                onRestartRequired = {
                    // This is unlikely to happen, but you could show a dialog
                    // telling the user to restart the application.
                }
            )
        }
    }

    MaterialTheme {
        if (initialized) {
            val viewModel = remember { TerminalViewModel(KuzuRepository()) }
            val scope = rememberCoroutineScope() // Get a coroutine scope
            TerminalView(viewModel)

            DisposableEffect(Unit) {
                onDispose {
                    viewModel.onCleared()
                    scope.launch(Dispatchers.IO) {
                        KCEF.dispose()
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDownloading) {
                        Text("Downloading KCEF dependencies: ${downloadProgress.toInt()}%")
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.width(200.dp)
                        )
                    } else {
                        Text("Initializing KCEF...")
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}