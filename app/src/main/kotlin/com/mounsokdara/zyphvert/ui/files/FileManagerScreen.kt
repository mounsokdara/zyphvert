package com.mounsokdara.zyphvert.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mounsokdara.zyphvert.ui.components.SearchField
import com.mounsokdara.zyphvert.ui.home.allTools
import com.mounsokdara.zyphvert.util.StorageAccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    bottomPadding: Dp,
    onOpenInTool: (toolId: String, filePath: String) -> Unit,
    onMediaInfo: (filePath: String) -> Unit,
    vm: FileManagerViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val list = vm.filtered()
    var sortMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = bottomPadding),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::goUp) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Up")
            }
            Text(
                "Files",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.showHidden = !vm.showHidden }) {
                Icon(
                    if (vm.showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = "Hidden files",
                )
            }
            IconButton(onClick = {
                vm.viewMode = if (vm.viewMode == ViewMode.List) ViewMode.Grid else ViewMode.List
            }) {
                Icon(
                    if (vm.viewMode == ViewMode.List) Icons.Rounded.GridView else Icons.Rounded.ViewList,
                    contentDescription = "View mode",
                )
            }
            IconButton(onClick = { sortMenu = true }) {
                Icon(Icons.Rounded.Sort, contentDescription = "Sort")
            }
            IconButton(onClick = vm::reload) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }

        if (!StorageAccess.hasAllFilesAccess()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Grant All files access to browse storage.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    TextButton(onClick = { StorageAccess.openAllFilesSettings(ctx) }) {
                        Text("Grant")
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.quickPlaces) { place ->
                FilterChip(
                    selected = vm.currentPath == place.path,
                    onClick = { vm.navigateTo(place.path) },
                    label = { Text(place.label) },
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(vm.breadcrumbs.size) { i ->
                val (label, path) = vm.breadcrumbs[i]
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (i == vm.breadcrumbs.lastIndex)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { vm.navigateTo(path) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    maxLines = 1,
                )
                if (i < vm.breadcrumbs.lastIndex) {
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SearchField(
            query = vm.query,
            onQueryChange = { vm.query = it },
            placeholder = "Search this folder",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        when {
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            vm.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { StorageAccess.openAllFilesSettings(ctx) }) {
                        Text("Open storage settings")
                    }
                }
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Empty folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            vm.viewMode == ViewMode.Grid -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.file.absolutePath }) { entry ->
                        FileGridItem(entry) { vm.open(entry) }
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.file.absolutePath }) { entry ->
                        FileListItem(entry) { vm.open(entry) }
                    }
                }
            }
        }
    }

    if (sortMenu) {
        ModalBottomSheet(onDismissRequest = { sortMenu = false }, sheetState = sheetState) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(
                    "Sort by",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                SortMode.entries.forEach { mode ->
                    val label = when (mode) {
                        SortMode.NameAsc -> "Name (A–Z)"
                        SortMode.NameDesc -> "Name (Z–A)"
                        SortMode.SizeDesc -> "Size (largest)"
                        SortMode.DateDesc -> "Date (newest)"
                        SortMode.Type -> "Type"
                    }
                    Text(
                        label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.sort = mode
                                sortMenu = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        color = if (vm.sort == mode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    val selected = vm.selected
    if (selected != null && !selected.isDirectory) {
        ModalBottomSheet(
            onDismissRequest = { vm.selected = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    selected.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${selected.sizeLabel} · ${selected.dateLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                ActionRow(Icons.Rounded.Info, "Media info") {
                    onMediaInfo(selected.file.absolutePath)
                    vm.selected = null
                }
                ActionRow(Icons.Rounded.Transform, "Open in converter…") {
                    val id = when (selected.extension) {
                        in listOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "flv", "ts", "m2ts") -> "video"
                        in listOf("mp3", "aac", "flac", "wav", "ogg", "m4a", "opus", "wma") -> "audio"
                        in listOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "gif", "bmp") -> "image"
                        in listOf("srt", "vtt", "ass", "ssa") -> "subs"
                        else -> "ffmpeg"
                    }
                    onOpenInTool(id, selected.file.absolutePath)
                    vm.selected = null
                }
                ActionRow(Icons.Rounded.Terminal, "Open in FFmpeg terminal") {
                    onOpenInTool("ffmpeg", selected.file.absolutePath)
                    vm.selected = null
                }
                Text(
                    "Tools",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                allTools.filter { it.id != "batch" }.forEach { tool ->
                    ActionRow(tool.icon, tool.title) {
                        onOpenInTool(tool.id, selected.file.absolutePath)
                        vm.selected = null
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FileListItem(entry: FileEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            iconFor(entry),
            null,
            modifier = Modifier.size(28.dp),
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    if (entry.isDirectory) append("Folder")
                    else {
                        append(entry.sizeLabel)
                        if (entry.extension.isNotEmpty()) append(" · ").append(entry.extension.uppercase())
                    }
                    append(" · ").append(entry.dateLabel)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.isDirectory) {
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileGridItem(entry: FileEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                iconFor(entry),
                null,
                modifier = Modifier.size(40.dp),
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(entry.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun iconFor(entry: FileEntry): ImageVector {
    if (entry.isDirectory) return Icons.Rounded.Folder
    return when (entry.extension) {
        in listOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp") -> Icons.Rounded.Movie
        in listOf("mp3", "aac", "flac", "wav", "ogg", "m4a", "opus") -> Icons.Rounded.AudioFile
        in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "avif", "bmp") -> Icons.Rounded.Image
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}
