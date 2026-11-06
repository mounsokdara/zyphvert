@file:OptIn(ExperimentalMaterial3Api::class)

package com.mounsokdara.zyphvert.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mounsokdara.zyphvert.ui.components.AppSheet
import com.mounsokdara.zyphvert.ui.components.FileRow
import com.mounsokdara.zyphvert.ui.components.FileTile
import com.mounsokdara.zyphvert.ui.components.SearchField
import com.mounsokdara.zyphvert.ui.components.UpFolderRow
import com.mounsokdara.zyphvert.ui.home.allTools
import com.mounsokdara.zyphvert.util.StorageAccess

@Composable
fun FileManagerScreen(
    bottomPadding: Dp,
    onOpenInTool: (toolId: String, filePath: String) -> Unit,
    onMediaInfo: (filePath: String) -> Unit,
    vm: FileManagerViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val list = vm.filtered()
    var menu by remember { mutableStateOf(false) }

    // Back goes up one folder; at the top of the tree it falls through to the app.
    BackHandler(enabled = vm.canGoUp) { vm.goUp() }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomPadding)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Files", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Show hidden files") },
                        leadingIcon = { Icon(if (vm.showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null) },
                        trailingIcon = { if (vm.showHidden) Icon(Icons.Rounded.Check, null) },
                        onClick = { vm.showHidden = !vm.showHidden; menu = false },
                    )
                    HorizontalDivider()
                    MenuLabel("View")
                    ViewMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(if (mode == ViewMode.List) "List" else "Grid") },
                            leadingIcon = { Icon(if (mode == ViewMode.List) Icons.Rounded.ViewList else Icons.Rounded.GridView, null) },
                            trailingIcon = { if (vm.viewMode == mode) Icon(Icons.Rounded.Check, null) },
                            onClick = { vm.viewMode = mode; menu = false },
                        )
                    }
                    HorizontalDivider()
                    MenuLabel("Sort by")
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (mode) {
                                        SortMode.NameAsc -> "Name (A–Z)"
                                        SortMode.NameDesc -> "Name (Z–A)"
                                        SortMode.SizeDesc -> "Size (largest)"
                                        SortMode.DateDesc -> "Date (newest)"
                                        SortMode.Type -> "Type"
                                    }
                                )
                            },
                            trailingIcon = { if (vm.sort == mode) Icon(Icons.Rounded.Check, null) },
                            onClick = { vm.sort = mode; menu = false },
                        )
                    }
                }
            }
        }

        if (!StorageAccess.hasAllFilesAccess()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Grant All files access to browse storage.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onTertiaryContainer)
                    TextButton(onClick = { StorageAccess.openAllFilesSettings(ctx) }) { Text("Grant") }
                }
            }
        }

        // Path bar: every segment is tappable, starting at the real root "/".
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            items(vm.breadcrumbs.size) { i ->
                val (label, path) = vm.breadcrumbs[i]
                Text(
                    label, style = MaterialTheme.typography.labelLarge,
                    color = if (i == vm.breadcrumbs.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.navigateTo(path) }.padding(horizontal = 4.dp, vertical = 2.dp),
                    maxLines = 1,
                )
                if (i < vm.breadcrumbs.lastIndex) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SearchField(vm.query, { vm.query = it }, "Search this folder", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

        // Pull down to refresh; there is no refresh button.
        PullToRefreshBox(isRefreshing = vm.loading, onRefresh = vm::reload, modifier = Modifier.weight(1f).fillMaxWidth()) {
            val hint: @Composable () -> Unit = {
                when {
                    vm.error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { StorageAccess.openAllFilesSettings(ctx) }) { Text("Open storage settings") }
                    }
                    list.isEmpty() && !vm.loading -> Text(
                        if (vm.query.isNotBlank()) "No matches" else "Empty folder",
                        Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (vm.viewMode == ViewMode.Grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp), contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (vm.canGoUp) item(key = "..up", span = { GridItemSpan(maxLineSpan) }) { UpFolderRow(Modifier.animateItem()) { vm.goUp() } }
                    item(key = "..hint", span = { GridItemSpan(maxLineSpan) }) { hint() }
                    items(list, key = { it.file.absolutePath }) { e ->
                        FileTile(e.name, e.file, e.isDirectory, Modifier.animateItem()) { vm.open(e) }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (vm.canGoUp) item(key = "..up") { UpFolderRow(Modifier.animateItem()) { vm.goUp() } }
                    item(key = "..hint") { hint() }
                    items(list, key = { it.file.absolutePath }) { e ->
                        FileRow(
                            name = e.name, file = e.file, isDir = e.isDirectory,
                            size = if (e.isDirectory) null else e.sizeLabel, date = e.dateLabel,
                            modifier = Modifier.animateItem(), onClick = { vm.open(e) },
                        )
                    }
                }
            }
        }
    }

    val selected = vm.selected
    if (selected != null && !selected.isDirectory) {
        AppSheet({ vm.selected = null }) { close ->
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${selected.sizeLabel} · ${selected.dateLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                ActionRow(Icons.Rounded.Info, "Media info") { onMediaInfo(selected.file.absolutePath); close() }
                ActionRow(Icons.Rounded.Transform, "Open in converter…") {
                    val id = when (selected.extension) {
                        in listOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "flv", "ts", "m2ts") -> "video"
                        in listOf("mp3", "aac", "flac", "wav", "ogg", "m4a", "opus", "wma") -> "audio"
                        in listOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "gif", "bmp") -> "image"
                        in listOf("srt", "vtt", "ass", "ssa") -> "subs"
                        else -> "ffmpeg"
                    }
                    onOpenInTool(id, selected.file.absolutePath); close()
                }
                ActionRow(Icons.Rounded.Terminal, "Open in FFmpeg terminal") { onOpenInTool("ffmpeg", selected.file.absolutePath); close() }
                Text("Tools", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                allTools.filter { it.id != "batch" }.forEach { tool ->
                    ActionRow(tool.icon, tool.title) { onOpenInTool(tool.id, selected.file.absolutePath); close() }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
