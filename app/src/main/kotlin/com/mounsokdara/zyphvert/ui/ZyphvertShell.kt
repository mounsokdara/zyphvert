package com.mounsokdara.zyphvert.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.mounsokdara.zyphvert.ui.home.HomeScreen
import com.mounsokdara.zyphvert.ui.screens.QueueScreen
import com.mounsokdara.zyphvert.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

private enum class Destination(val label: String, val icon: ImageVector) {
    Tools("Tools", Icons.Rounded.Apps),
    Queue("Queue", Icons.Rounded.Queue),
    Settings("Settings", Icons.Rounded.Settings),
}

/** App shell: bottom navigation + snackbar host. Swap for NavHost when tool flows land. */
@Composable
fun ZyphvertShell() {
    var current by rememberSaveable { mutableStateOf(Destination.Tools) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest,
                        onClick = { current = dest },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        // Screens own their top bars (and status-bar insets); only the bottom bar inset is shared.
        val bottom = padding.calculateBottomPadding()
        when (current) {
            Destination.Tools -> HomeScreen(
                bottomPadding = bottom,
                onToolClick = { tool ->
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("${tool.title} is coming soon")
                    }
                },
            )
            Destination.Queue -> QueueScreen(bottomPadding = bottom)
            Destination.Settings -> SettingsScreen(bottomPadding = bottom)
        }
    }
}
