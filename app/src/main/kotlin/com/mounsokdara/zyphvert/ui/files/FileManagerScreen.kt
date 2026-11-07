@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.mounsokdara.zyphvert.ui.files

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.mounsokdara.zyphvert.tools.InKind
import com.mounsokdara.zyphvert.tools.PickedFile
import com.mounsokdara.zyphvert.util.StorageAccess
import java.io.File

/** Set when the browser is used to pick files for a tool; null means it is the Files tab. */
class PickConfig(
    val kind: InKind,
    val multiple: Boolean,
    val title: String,
    val onDismiss: () -> Unit,
    /** Fallback when all-files access isn't granted. */
    val onUseSystem: () -> Unit,
    val onPicked: (List<PickedFile>) -> Unit,
    /** When set, the browser chooses a folder instead of files: only folders are listed and "Select" returns the open one. */
    val onFolder: ((File) -> Unit)? = null,
    /** Folder to open first. Null keeps wherever the picker was left. */
    val startPath: String? = null,
)

/** The file picker: the same screen as the Files tab, full screen, in pick mode. */
@Composable
fun FilePickerDialog(
    kind: InKind,
    multiple: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onUseSystem: () -> Unit,
    onPicked: (List<PickedFile>) -> Unit,
) {
    PickerDialog(PickConfig(kind, multiple, title, onDismiss, onUseSystem, onPicked))
}

/** The same picker, choosing a folder (the terminal uses it for `cd`). */
@Composable
fun FolderPickerDialog(title: String, startPath: String?, onDismiss: () -> Unit, onPicked: (File) -> Unit) {
    PickerDialog(PickConfig(InKind.Any, false, title, onDismiss, onUseSystem = {}, onPicked = {}, onFolder = onPicked, startPath = startPath))
}

@Composable
private fun PickerDialog(config: PickConfig) {
    Dialog(onDismissRequest = config.onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            FileManagerScreen(pick = config)
        }
    }
}

/** The Files tab, or (with [pick]) the file picker. Both share this one screen and one ViewModel class. */
@Composable
fun FileManagerScreen(
    bottomPadding: Dp = 0.dp,
    onOpenInTool: (toolId: String, filePath: String) -> Unit = { _, _ -> },
    onMediaInfo: (filePath: String) -> Unit = {},
    pick: PickConfig? = null,
    vm: FileManagerViewModel = hiltViewModel(key = if (pick != null) "picker" else null),
) {
    val ctx = LocalContext.current
    val picks = remember { mutableStateListOf<FileEntry>() }
    fun orderOf(e: FileEntry) = picks.indexOfFirst { it.file.path == e.file.path }
    fun finishPick(items: List<FileEntry>) {
        if (items.isEmpty()) return
        pick?.onPicked(items.map { PickedFile(it.file.toUri(), it.name, it.companion?.path, it.live) })
    }
    var menu by remember { mutableStateOf(false) }
    var places by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showFolders by rememberSaveable { mutableStateOf(true) }
    var kinds by remember { mutableStateOf(emptySet<SearchKind>()) }
    fun closeSearch() { searching = false; vm.query = ""; showFolders = true; kinds = emptySet() }
    val rootList = remember { roots(ctx) }
    val drives = remember { rootList.filter { it.first == "Internal storage" || it.first == "SD card" }.map { it.second.path }.toSet() }
    val folderMode = pick?.onFolder != null
    if (pick != null) LaunchedEffect(pick.kind, pick.title) {
        vm.configurePick(pick.kind, drives)
        pick.startPath?.let { vm.navigateTo(it) }
    }
    var renameFor by remember { mutableStateOf<FileEntry?>(null) }
    var deleteFor by remember { mutableStateOf<FileEntry?>(null) }
    var propsFor by remember { mutableStateOf<FileEntry?>(null) }
    LaunchedEffect(vm.message) {
        vm.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.message = null }
    }
    val list = vm.filtered()
        .filter { !searching || passesSearchFilters(it.isDirectory, it.extension, showFolders, kinds) }
        .filter { !folderMode || it.isDirectory }

    // A tap: the Files tab opens a file's menu; the picker selects it. Folders always open.
    val onTap: (FileEntry) -> Unit = { e ->
        if (pick == null || e.isDirectory) vm.open(e)
        else {
            val i = orderOf(e)
            when {
                !pick.multiple -> finishPick(listOf(e))
                i >= 0 -> picks.removeAt(i)
                else -> picks.add(e)
            }
        }
    }

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
    BackHandler(enabled = vm.canGoUp && vm.browseMode == BrowseMode.Folders) { vm.goUp() }
    BackHandler(enabled = vm.browseMode == BrowseMode.Media) { vm.setMode(BrowseMode.Folders) }
    BackHandler(enabled = searching) { closeSearch() }

    Column(Modifier.fillMaxSize().statusBarsPadding().then(if (pick != null) Modifier.navigationBarsPadding() else Modifier.padding(bottom = bottomPadding))) {
        if (searching) {
            SearchAppBar(
                query = vm.query, onQueryChange = { vm.query = it }, onClose = { closeSearch() },
                grid = vm.viewMode == ViewMode.Grid,
                onToggleGrid = { vm.viewMode = if (vm.viewMode == ViewMode.Grid) ViewMode.List else ViewMode.Grid },
                menuContent = managerMenu,
            )
            SearchFilters(showFolders, { showFolders = it }, kinds, { kinds = it })
        } else Row(Modifier.fillMaxWidth().padding(start = if (pick != null) 4.dp else 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pick != null) IconButton(onClick = pick.onDismiss) { Icon(Icons.Rounded.Close, "Close") }
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { places = true }.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(pick?.title ?: "Files", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    if (pick != null) TextButton(onClick = { pick.onDismiss(); pick.onUseSystem() }) { Text("System picker") }
                }
            }
        }

        // The two states: walk folders, or one flat list of every media file. The picker adds a type filter.
        if (!folderMode) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = vm.browseMode == BrowseMode.Folders, onClick = { vm.setMode(BrowseMode.Folders) }, label = { Text("Folders") },
                leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = vm.browseMode == BrowseMode.Media, onClick = { vm.setMode(BrowseMode.Media) }, label = { Text("All media") },
                leadingIcon = { Icon(Icons.Rounded.PermMedia, null, Modifier.size(18.dp)) },
            )
            if (pick != null && pick.kind.exts != null) {
                VerticalDivider(Modifier.height(24.dp))
                FilterChip(selected = !vm.showAllTypes, onClick = { vm.showAllTypes = false }, label = { Text(kindLabel(pick.kind)) })
                FilterChip(selected = vm.showAllTypes, onClick = { vm.showAllTypes = true }, label = { Text(if (vm.browseMode == BrowseMode.Media) "All media types" else "All files") })
            }
        }

        // Path bar: every segment is tappable, starting at the real root "/".
        if (vm.browseMode == BrowseMode.Folders) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
        // Pull-to-refresh is the only reaction to a pull at the top. The system rubber-band/stretch is switched off here,
        // otherwise it moves the whole list along with the finger while the refresh spinner shows.
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::pullRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            val hint: @Composable () -> Unit = {
                when {
                    vm.error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { StorageAccess.openAllFilesSettings(ctx) }) { Text("Open storage settings") }
                    }
                    list.isEmpty() && !vm.loading -> Text(
                        if (vm.query.isNotBlank()) "No matches" else if (vm.browseMode == BrowseMode.Media) "No media files found" else if (folderMode) "No folders in here" else "Empty folder",
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
                    if (vm.canGoUp && vm.browseMode == BrowseMode.Folders) item(key = "..up", span = { GridItemSpan(maxLineSpan) }) { UpFolderRow(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)) { vm.goUp() } }
                    item(key = "..hint", span = { GridItemSpan(maxLineSpan) }) { hint() }
                    items(list, key = { it.file.absolutePath }) { e ->
                        FileTile(
                            e.name, e.file, e.isDirectory, Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null), live = e.live,
                            selectedOrder = orderOf(e), showSelect = pick?.multiple == true && !e.isDirectory,
                            onLongClick = { vm.selected = e },
                        ) { onTap(e) }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (vm.canGoUp && vm.browseMode == BrowseMode.Folders) item(key = "..up") { UpFolderRow(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)) { vm.goUp() } }
                    item(key = "..hint") { hint() }
                    items(list, key = { it.file.absolutePath }) { e ->
                        FileRow(
                            name = e.name, file = e.file, isDir = e.isDirectory,
                            size = if (e.isDirectory) null else e.sizeLabel,
                            // In the flat media list, say which folder each file is in.
                            date = if (vm.browseMode == BrowseMode.Media) (e.file.parentFile?.name ?: "") + " · " + e.dateLabel else e.dateLabel,
                            live = e.live, selectedOrder = orderOf(e), showSelect = pick?.multiple == true && !e.isDirectory,
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null), onLongClick = { vm.selected = e }, onClick = { onTap(e) },
                        )
                    }
                }
            }
        }
        }

        // Picker with several files: the running selection and the Select button.
        if (pick?.multiple == true) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (picks.isEmpty()) "Tap files to select" else "${picks.size} selected", style = MaterialTheme.typography.titleSmall)
                    Text("Files are used in the order you pick them", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { list.filter { !it.isDirectory && orderOf(it) < 0 }.forEach { picks.add(it) } }) { Text("All") }
                TextButton(onClick = { picks.clear() }, enabled = picks.isNotEmpty()) { Text("Clear") }
                Button(onClick = { finishPick(picks.toList()) }, enabled = picks.isNotEmpty(), shape = CircleShape) { Text("Select") }
            }
        }
        // Choosing a folder: the folder that is open is the choice.
        pick?.onFolder?.let { onFolder ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use this folder", style = MaterialTheme.typography.titleSmall)
                        Text(vm.currentPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = { onFolder(File(vm.currentPath)) }, shape = CircleShape) { Text("Select") }
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
                if (pick != null) ActionRow(Icons.Rounded.Check, if (pick.multiple) "Select" else "Pick this file") { onTap(selected); close() }
                EntryActions(vm, selected, close, { renameFor = selected }, { deleteFor = selected }, { propsFor = selected })
                if (pick == null) {
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
    var stats by remember(e) { mutableStateOf<FileStats?>(null) }
    LaunchedEffect(e) { stats = vm.stats(e.file) }
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

private fun kindLabel(kind: InKind) = when (kind) {
    InKind.Video -> "Video"
    InKind.Audio -> "Audio"
    InKind.Media -> "Video & audio"
    InKind.Image -> "Images"
    InKind.Visual -> "Video & images"
    InKind.Any -> "All files"
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
