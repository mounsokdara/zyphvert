package com.mounsokdara.zyphvert.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mounsokdara.zyphvert.ui.components.isWideLayout
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.mounsokdara.zyphvert.ui.files.FileManagerScreen
import com.mounsokdara.zyphvert.ui.files.MediaInfoScreen
import com.mounsokdara.zyphvert.ui.home.HomeScreen
import com.mounsokdara.zyphvert.ui.home.allTools
import com.mounsokdara.zyphvert.ui.more.AboutScreen
import com.mounsokdara.zyphvert.ui.more.LicensesScreen
import com.mounsokdara.zyphvert.ui.more.MoreRoute
import com.mounsokdara.zyphvert.ui.more.MoreScreen
import com.mounsokdara.zyphvert.ui.screens.QueueScreen
import com.mounsokdara.zyphvert.ui.settings.SettingsScreen
import com.mounsokdara.zyphvert.ui.settings.SettingsViewModel
import com.mounsokdara.zyphvert.ui.terminal.TerminalScreen
import com.mounsokdara.zyphvert.ui.terminal.TerminalViewModel
import com.mounsokdara.zyphvert.ui.tool.ToolScreen
import com.mounsokdara.zyphvert.ui.tool.ToolViewModel

private enum class Destination(val label: String, val icon: ImageVector) {
    Tools("Tools", Icons.Rounded.Apps),
    Files("Files", Icons.Rounded.Folder),
    Queue("Queue", Icons.Rounded.Queue),
    More("More", Icons.Rounded.Menu),
}

@Composable
fun ZyphvertShell(settingsVm: SettingsViewModel, hapticsEnabled: Boolean) {
    var current by rememberSaveable { mutableStateOf(Destination.Tools) }
    var sub by rememberSaveable { mutableStateOf<MoreRoute?>(null) }
    var openToolId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaInfoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    fun tick() { if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    BackHandler(enabled = mediaInfoPath != null) { mediaInfoPath = null }
    BackHandler(enabled = mediaInfoPath == null && openToolId != null) {
        openToolId = null
        pendingFilePath = null
    }
    BackHandler(enabled = mediaInfoPath == null && openToolId == null && sub != null) {
        sub = if (sub == MoreRoute.Licenses) MoreRoute.About else null
    }

    val inSub = (current == Destination.More && sub != null) || openToolId != null || mediaInfoPath != null
    val wide = isWideLayout()

    Scaffold(
        bottomBar = {
            if (!inSub && !wide) {
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
        Row(Modifier.fillMaxSize()) {
            if (wide && !inSub) {
                NavigationRail {
                    Destination.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = current == dest,
                            onClick = { tick(); current = dest },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {

                val infoPath = mediaInfoPath
                if (infoPath != null) {
                    MediaInfoScreen(path = infoPath, onBack = { mediaInfoPath = null })
                    return@Box
                }

                val activeTool = openToolId?.let { id -> allTools.find { it.id == id } }
                if (activeTool != null) {
                    if (activeTool.id == "ffmpeg") {
                        val tvm = hiltViewModel<TerminalViewModel>()
                        pendingFilePath?.let { path ->
                            tvm.insert("\"$path\"")
                            pendingFilePath = null
                        }
                        TerminalScreen(tvm, onBack = { openToolId = null; pendingFilePath = null })
                    } else {
                        val tvm = hiltViewModel<ToolViewModel>()
                        pendingFilePath?.let { path ->
                            tvm.setInputPath(path)
                            pendingFilePath = null
                        }
                        ToolScreen(
                            tool = activeTool,
                            onBack = { openToolId = null; pendingFilePath = null },
                            vm = tvm,
                        )
                    }
                } else when (current) {
                    Destination.Tools -> HomeScreen(
                        bottomPadding = bottom,
                        onToolClick = { tool ->
                            tick()
                            openToolId = tool.id
                        },
                    )
                    Destination.Files -> FileManagerScreen(
                        bottomPadding = bottom,
                        onOpenInTool = { toolId, path ->
                            tick()
                            pendingFilePath = path
                            openToolId = toolId
                        },
                        onMediaInfo = { path ->
                            tick()
                            mediaInfoPath = path
                        },
                    )
                    Destination.Queue -> QueueScreen(bottomPadding = bottom)
                    Destination.More -> when (sub) {
                        null -> MoreScreen(bottomPadding = bottom, onOpen = { tick(); sub = it })
                        MoreRoute.Settings -> SettingsScreen(settingsVm, onBack = { sub = null })
                        MoreRoute.Terminal -> TerminalScreen(hiltViewModel(), onBack = { sub = null })
                        MoreRoute.About -> AboutScreen(onBack = { sub = null }, onLicenses = { sub = MoreRoute.Licenses })
                        MoreRoute.Licenses -> LicensesScreen(onBack = { sub = MoreRoute.About })
                        MoreRoute.Files -> FileManagerScreen(
                            bottomPadding = bottom,
                            onOpenInTool = { toolId, path ->
                                sub = null
                                pendingFilePath = path
                                openToolId = toolId
                            },
                            onMediaInfo = { path ->
                                sub = null
                                mediaInfoPath = path
                            },
                        )
                    }
                }
            }
        }
    }
}
