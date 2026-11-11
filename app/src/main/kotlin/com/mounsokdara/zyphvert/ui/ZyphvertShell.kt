package com.mounsokdara.zyphvert.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import com.mounsokdara.zyphvert.ui.components.isWideLayout
import com.mounsokdara.zyphvert.ui.files.FileManagerScreen
import com.mounsokdara.zyphvert.ui.files.MediaInfoScreen
import com.mounsokdara.zyphvert.ui.home.HomeScreen
import com.mounsokdara.zyphvert.ui.home.allTools
import com.mounsokdara.zyphvert.ui.more.AboutScreen
import com.mounsokdara.zyphvert.ui.more.LicensesScreen
import com.mounsokdara.zyphvert.ui.more.ChangelogScreen
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
        sub = if (sub == MoreRoute.Licenses || sub == MoreRoute.Changelog) MoreRoute.About else null
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
            val infoPath = mediaInfoPath
            val toolId = openToolId?.takeIf { id -> allTools.any { it.id == id } }
            val subRoute = sub
            val screen: Screen = when {
                infoPath != null -> Screen.MediaInfo(infoPath)
                toolId != null -> Screen.ToolS(toolId)
                current == Destination.More && subRoute != null -> Screen.MoreSub(subRoute)
                else -> Screen.Tab(current)
            }

            AnimatedContent(
                targetState = screen,
                modifier = Modifier.weight(1f).fillMaxSize(),
                label = "screen",
                transitionSpec = {
                    val enteringSub = targetState !is Screen.Tab
                    val wasSub = initialState !is Screen.Tab
                    when {
                        enteringSub && !wasSub ->
                            (slideInHorizontally(tween(280)) { it / 8 } + fadeIn(tween(240))) togetherWith
                                (slideOutHorizontally(tween(280)) { -it / 12 } + fadeOut(tween(160)))
                        !enteringSub && wasSub ->
                            (slideInHorizontally(tween(280)) { -it / 12 } + fadeIn(tween(240))) togetherWith
                                (slideOutHorizontally(tween(280)) { it / 8 } + fadeOut(tween(160)))
                        else -> (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)) togetherWith fadeOut(tween(120))
                    }
                },
            ) { sc ->
                when (sc) {
                    is Screen.MediaInfo -> MediaInfoScreen(path = sc.path, onBack = { mediaInfoPath = null })
                    is Screen.ToolS -> {
                        val tool = allTools.first { it.id == sc.id }
                        if (tool.id == "ffmpeg") {
                            val tvm = hiltViewModel<TerminalViewModel>()
                            val seed = pendingFilePath
                            LaunchedEffect(seed) {
                                if (seed != null) {
                                    tvm.insert("\"$seed\"")
                                    pendingFilePath = null
                                }
                            }
                            TerminalScreen(tvm, onBack = { openToolId = null; pendingFilePath = null })
                        } else {
                            // Key ViewModel per tool so form state does not leak across tools
                            val tvm = hiltViewModel<ToolViewModel>(key = tool.id)
                            ToolScreen(
                                tool = tool,
                                seedPath = pendingFilePath,
                                onBack = { openToolId = null; pendingFilePath = null },
                                onQueued = { openToolId = null; pendingFilePath = null; current = Destination.Queue },
                                vm = tvm,
                            )
                        }
                    }
                    is Screen.MoreSub -> when (sc.route) {
                        MoreRoute.Settings -> SettingsScreen(settingsVm, onBack = { sub = null })
                        MoreRoute.Terminal -> TerminalScreen(hiltViewModel(), onBack = { sub = null })
                        MoreRoute.Converted -> com.mounsokdara.zyphvert.ui.more.ConvertedFilesScreen(onBack = { sub = null })
                        MoreRoute.About -> AboutScreen(
                            onBack = { sub = null }, onLicenses = { sub = MoreRoute.Licenses },
                            onChangelog = { sub = MoreRoute.Changelog },
                        )
                        MoreRoute.Licenses -> LicensesScreen(onBack = { sub = MoreRoute.About })
                        MoreRoute.Changelog -> ChangelogScreen(onBack = { sub = MoreRoute.About })
                    }
                    is Screen.Tab -> when (sc.dest) {
                        Destination.Tools -> HomeScreen(
                            bottomPadding = bottom,
                            onToolClick = { tool -> tick(); openToolId = tool.id },
                        )
                        Destination.Files -> FileManagerScreen(
                            bottomPadding = bottom,
                            onOpenInTool = { id, path -> tick(); pendingFilePath = path; openToolId = id },
                            onMediaInfo = { path -> tick(); mediaInfoPath = path },
                        )
                        Destination.Queue -> QueueScreen(bottomPadding = bottom)
                        Destination.More -> MoreScreen(bottomPadding = bottom, onOpen = { tick(); sub = it })
                    }
                }
            }
        }
    }
}

/** What the shell is showing. A tool is identified by its id only, so picking a file for it doesn't re-trigger the transition. */
private sealed interface Screen {
    data class MediaInfo(val path: String) : Screen
    data class ToolS(val id: String) : Screen
    data class MoreSub(val route: MoreRoute) : Screen
    data class Tab(val dest: Destination) : Screen
}
