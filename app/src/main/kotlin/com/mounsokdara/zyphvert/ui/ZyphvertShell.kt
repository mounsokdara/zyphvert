package com.mounsokdara.zyphvert.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Queue
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.mounsokdara.zyphvert.ui.home.HomeScreen
import com.mounsokdara.zyphvert.ui.more.AboutScreen
import com.mounsokdara.zyphvert.ui.more.LicensesScreen
import com.mounsokdara.zyphvert.ui.more.MoreRoute
import com.mounsokdara.zyphvert.ui.more.MoreScreen
import com.mounsokdara.zyphvert.ui.screens.QueueScreen
import com.mounsokdara.zyphvert.ui.settings.SettingsScreen
import com.mounsokdara.zyphvert.ui.settings.SettingsViewModel
import com.mounsokdara.zyphvert.ui.terminal.TerminalScreen
import com.mounsokdara.zyphvert.ui.terminal.TerminalViewModel
import kotlinx.coroutines.launch

private enum class Destination(val label: String, val icon: ImageVector) {
    Tools("Tools", Icons.Rounded.Apps),
    Queue("Queue", Icons.Rounded.Queue),
    More("More", Icons.Rounded.Menu),
}

/** App shell: bottom navigation, snackbar host, and the More sub-screens (bar hidden inside them). */
@Composable
fun ZyphvertShell(settingsVm: SettingsViewModel, hapticsEnabled: Boolean) {
    var current by rememberSaveable { mutableStateOf(Destination.Tools) }
    var sub by rememberSaveable { mutableStateOf<MoreRoute?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    fun tick() { if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    BackHandler(enabled = sub != null) {
        sub = if (sub == MoreRoute.Licenses) MoreRoute.About else null
    }

    val inSub = current == Destination.More && sub != null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!inSub) {
                NavigationBar {
                    Destination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest,
                            onClick = { tick(); current = dest },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val bottom = padding.calculateBottomPadding()
        when (current) {
            Destination.Tools -> HomeScreen(
                bottomPadding = bottom,
                onToolClick = { tool ->
                    tick()
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("${tool.title} is coming soon")
                    }
                },
            )
            Destination.Queue -> QueueScreen(bottomPadding = bottom)
            Destination.More -> when (sub) {
                null -> MoreScreen(bottomPadding = bottom, onOpen = { tick(); sub = it })
                MoreRoute.Settings -> SettingsScreen(settingsVm, onBack = { sub = null })
                MoreRoute.Terminal -> TerminalScreen(hiltViewModel<TerminalViewModel>(), onBack = { sub = null })
                MoreRoute.About -> AboutScreen(onBack = { sub = null }, onLicenses = { sub = MoreRoute.Licenses })
                MoreRoute.Licenses -> LicensesScreen(onBack = { sub = MoreRoute.About })
            }
        }
    }
}
