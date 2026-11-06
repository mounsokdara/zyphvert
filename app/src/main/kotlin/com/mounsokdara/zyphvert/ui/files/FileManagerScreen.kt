@file:OptIn(ExperimentalMaterial3Api::class)

package com.mounsokdara.zyphvert.ui.files

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mounsokdara.zyphvert.ui.components.SearchAppBar
import com.mounsokdara.zyphvert.ui.components.SearchFilters
import com.mounsokdara.zyphvert.ui.components.SearchKind
import com.mounsokdara.zyphvert.ui.components.passesSearchFilters
import com.mounsokdara.zyphvert.ui.components.UpFolderRow
import com.mounsokdara.zyphvert.ui.home.allTools
import com.mounsokdara.zyphvert.ui.picker.roots
import com.mounsokdara.zyphvert.util.StorageAccess

@Composable
fun FileManagerScreen(
    bottomPadding: Dp,
    onOpenInTool: (toolId: String, filePath: String) -> Unit,
    onMediaInfo: (filePath: String) -> Unit,
    vm: FileManagerViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var places by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showFolders by rememberSaveable { mutableStateOf(true) }
    var kinds by remember { mutableStateOf(emptySet<SearchKind>()) }
    fun closeSearch() { searching = false; vm.query = ""; showFolders = true; kinds = emptySet() }
    val rootList = remember { roots(ctx) }
    var renameFor by remember { mutableStateOf<FileEntry?>(null) }
    var deleteFor by remember { mutableStateOf<FileEntry?>(null) }
    var propsFor by remember { mutableStateOf<FileEntry?>(null) }
    LaunchedEffect(vm.message) {
        vm.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.message = null }
    }
    val list = vm.filtered().filter { !searching || passesSearchFilters(it.isDirectory, it.extension, showFolders, kinds) }

    val managerMenu: @Composable ColumnScope.(() -> Unit) -> Unit = { dismiss ->
        vm.clip?.let { c ->
            DropdownMenuItem(
                text = { Text("Paste here (${c.files.size})") },
                leadingIcon = { Icon(Icons.Rounded.ContentPaste, null) },
                onClick = { vm.paste(); dismiss() },
            )
            HorizontalDivider()
        }
                    DropdownMenuItem(
                        text = { Text("Show hidden files") },
                        leadingIcon = { Icon(if (vm.showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null) },
                        trailingIcon = { if (vm.showHidden) Icon(Icons.Rounded.Check, null) },
                        onClick = { vm.showHidden = !vm.showHidden; dismiss() },
                    )
                    HorizontalDivider()
                    MenuLabel("View")
                    ViewMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(if (mode == ViewMode.List) "List" else "Grid") },
                            leadingIcon = { Icon(if (mode == ViewMode.List) Icons.Rounded.ViewList else Icons.Rounded.GridView, null) },
                            trailingIcon = { if (vm.viewMode == mode) Icon(Icons.Rounded.Check, null) },
                            onClick = { vm.viewMode = mode; dismiss() },
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
                            onClick = { vm.sort = mode; dismiss() },
                        )
                    }
                    }

    // Back goes up one folder; at the top of the tree it falls through to the app.
    BackHandler(enabled = vm.canGoUp) { vm.goUp() }
    BackHandler(enabled = searching) { closeSearch() }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomPadding)) {
        if (searching) {
            SearchAppBar(
                query = vm.query, onQueryChange = { vm.query = it }, onClose = { closeSearch() },
                grid = vm.viewMode == ViewMode.Grid,
                onToggleGrid = { vm.viewMode = if (vm.viewMode == ViewMode.Grid) ViewMode.List else ViewMode.Grid },
                menuContent = managerMenu,
            )
            SearchFilters(showFolders, { showFolders = it }, kinds, { kinds = it })
        } else         Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { places = true }.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Files", style = MaterialTheme.typography.headlineSmall)
                    Icon(Icons.Rounded.ArrowDropDown, "Choose a location")
                }
                DropdownMenu(expanded = places, onDismissRequest = { places = false }) {
                    rootList.forEach { (label, f) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(if (label == "Internal storage" || label == "SD card") Icons.Rounded.Storage else Icons.Rounded.Folder, null) },
                            onClick = { vm.navigateTo(f.path); places = false },
                        )
                    }
                }
            }
            IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    managerMenu { menu = false }
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

        vm.clip?.let { c ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)),
            ) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (c.cut) "Cut: " else "Copied: ") + (if (c.files.size == 1) c.files[0].name else "${c.files.size} items"),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { vm.paste() }) { Text("Paste here") }
                    IconButton(onClick = { vm.clearClip() }) { Icon(Icons.Rounded.Close, "Clear clipboard") }
                }
            }
        }

        // Pull down to refresh; there is no refresh button.
        PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::pullRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        FileTile(e.name, e.file, e.isDirectory, Modifier.animateItem(), onLongClick = { vm.selected = e }) { vm.open(e) }
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
                            modifier = Modifier.animateItem(), onLongClick = { vm.selected = e }, onClick = { vm.open(e) },
                        )
                    }
                }
            }
        }
    }

    EntryDialogs(
        vm, renameFor, { renameFor = null }, deleteFor, { deleteFor = null }, propsFor, { propsFor = null },
    )

    val selected = vm.selected
    if (selected != null && selected.isDirectory) {
        AppSheet({ vm.selected = null }) { close ->
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(selected.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                ActionRow(Icons.Rounded.Folder, "Open folder") { vm.open(selected); close() }
                EntryActions(vm, selected, close, { renameFor = selected }, { deleteFor = selected }, { propsFor = selected })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (selected != null && !selected.isDirectory) {
        AppSheet({ vm.selected = null }) { close ->
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${selected.sizeLabel} · ${selected.dateLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                EntryActions(vm, selected, close, { renameFor = selected }, { deleteFor = selected }, { propsFor = selected })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
private fun EntryDialogs(
    vm: FileManagerViewModel,
    renameFor: FileEntry?, onRenameDone: () -> Unit,
    deleteFor: FileEntry?, onDeleteDone: () -> Unit,
    propsFor: FileEntry?, onPropsDone: () -> Unit,
) {
    renameFor?.let { e ->
        var text by remember(e) { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = onRenameDone, title = { Text("Rename") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.rename(e, text); onRenameDone() }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = onRenameDone) { Text("Cancel") } },
        )
    }
    deleteFor?.let { e ->
        AlertDialog(
            onDismissRequest = onDeleteDone, title = { Text("Delete ${if (e.isDirectory) "folder" else "file"}?") },
            text = { Text(if (e.isDirectory) "\"${e.name}\" and everything inside it will be deleted. This can't be undone." else "\"${e.name}\" will be deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.delete(e); onDeleteDone() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDeleteDone) { Text("Cancel") } },
        )
    }
    propsFor?.let { e -> PropertiesDialog(vm, e, onPropsDone) }
}

@Composable
private fun EntryActions(
    vm: FileManagerViewModel, e: FileEntry, close: () -> Unit,
    onRename: () -> Unit, onDelete: () -> Unit, onProps: () -> Unit,
) {
    ActionRow(Icons.Rounded.ContentCopy, "Copy") { vm.copy(e); close() }
    ActionRow(Icons.Rounded.ContentCut, "Cut") { vm.cut(e); close() }
    if (e.isDirectory && vm.clip != null) ActionRow(Icons.Rounded.ContentPaste, "Paste into this folder") { vm.paste(e.file); close() }
    ActionRow(Icons.Rounded.DriveFileRenameOutline, "Rename") { onRename(); close() }
    ActionRow(Icons.Rounded.Delete, "Delete") { onDelete(); close() }
    ActionRow(Icons.Rounded.Info, "Properties") { onProps(); close() }
}

@Composable
private fun PropertiesDialog(vm: FileManagerViewModel, e: FileEntry, onDismiss: () -> Unit) {
    val stats by produceState<FileStats?>(null, e) { value = vm.stats(e.file) }
    var sums by remember(e) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busy by remember(e) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PropRow("Name", e.name)
                    PropRow("Location", e.file.parent ?: "/")
                    PropRow("Type", if (e.isDirectory) "Folder" else e.extension.uppercase().ifEmpty { "File" })
                    PropRow(
                        "Size",
                        stats?.let {
                            FileEntry.humanSize(it.bytes) + if (e.isDirectory) " (${it.files} files, ${it.folders} folders)" else " (${it.bytes} bytes)"
                        } ?: "Calculating…",
                    )
                    PropRow("Modified", e.dateLabel)
                    PropRow("Access", listOfNotNull(if (e.file.canRead()) "Read" else null, if (e.file.canWrite()) "Write" else null).joinToString(", ").ifEmpty { "None" })
                    if (!e.isDirectory) {
                        listOf("MD5", "SHA-256").forEach { algo ->
                            val v = sums[algo]
                            if (v != null) PropRow(algo, v)
                            else TextButton(onClick = { scope.launch { busy = algo; sums = sums + (algo to vm.checksum(e.file, algo)); busy = null } }, enabled = busy == null) {
                                Text(if (busy == algo) "Working out $algo…" else "Show $algo checksum")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PropRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
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
