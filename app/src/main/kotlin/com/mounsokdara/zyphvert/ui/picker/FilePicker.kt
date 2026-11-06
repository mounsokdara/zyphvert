@file:OptIn(ExperimentalMaterial3Api::class)

package com.mounsokdara.zyphvert.ui.picker

import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mounsokdara.zyphvert.tools.*
import com.mounsokdara.zyphvert.ui.components.FileRow
import com.mounsokdara.zyphvert.ui.components.FileTile
import com.mounsokdara.zyphvert.ui.components.SearchField
import com.mounsokdara.zyphvert.ui.components.UpFolderRow
import com.mounsokdara.zyphvert.util.FsFallback
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.mounsokdara.zyphvert.util.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Sort(val label: String) { Newest("Newest first"), Name("Name"), Size("Largest first") }

private class Entry(
    val file: File,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val live: Boolean,
    val companion: File?,
)

private var lastDir: String? = null

private fun motionName(n: String): Boolean {
    val l = n.lowercase()
    return l.startsWith("mvimg_") || ".mp." in l || "_mp." in l || l.contains("motionphoto")
}

private fun listDir(dir: File, kind: InKind, showAll: Boolean, showHidden: Boolean): List<Entry> {
    val all = FsFallback.children(dir)?.filter { showHidden || !it.name.startsWith(".") } ?: return emptyList()
    val videoBases = all.filter { it.isFile && it.extension.lowercase() in LIVE_VIDEO_EXTS }
        .associateBy { it.nameWithoutExtension.lowercase() }
    val exts = kind.exts
    return all.mapNotNull { f ->
        if (f.isDirectory) return@mapNotNull Entry(f, true, 0, f.lastModified(), false, null)
        val ext = f.extension.lowercase()
        val pair = if (ext in LIVE_STILL_EXTS) videoBases[f.nameWithoutExtension.lowercase()] else null
        val live = pair != null || (ext in LIVE_STILL_EXTS && motionName(f.name))
        val show = showAll || exts == null || ext in exts || (kind.acceptsLive && live)
        if (show) Entry(f, false, f.length(), f.lastModified(), live, pair) else null
    }
}

private fun roots(ctx: android.content.Context): List<Pair<String, File>> {
    val base = Environment.getExternalStorageDirectory()
    val list = mutableListOf("Internal storage" to base)
    for ((label, dir) in listOf(
        "Camera" to Environment.DIRECTORY_DCIM, "Downloads" to Environment.DIRECTORY_DOWNLOADS,
        "Movies" to Environment.DIRECTORY_MOVIES, "Music" to Environment.DIRECTORY_MUSIC,
        "Pictures" to Environment.DIRECTORY_PICTURES, "Documents" to Environment.DIRECTORY_DOCUMENTS,
    )) {
        val f = Environment.getExternalStoragePublicDirectory(dir)
        if (f.isDirectory) list += label to f
    }
    File(base, "Zyphvert").takeIf { it.isDirectory }?.let { list += "Zyphvert" to it }
    list += "Root (/)" to File("/")
    // SD cards and USB drives show up as extra app-files dirs; walk up to the volume root.
    ctx.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { d ->
        val root = d.path.substringBefore("/Android/")
        if (root != d.path && File(root).isDirectory) list += "SD card" to File(root)
    }
    return list
}

private const val HOME = "::home"
private val FolderOrange = androidx.compose.ui.graphics.Color(0xFFFFA726)
private val StorageGreen = androidx.compose.ui.graphics.Color(0xFF4CAF50)

private fun usageOf(ctx: android.content.Context, f: File): String? = runCatching {
    val st = StatFs(f.path)
    val total = st.totalBytes
    Formatter.formatFileSize(ctx, total - st.availableBytes) + "/" + Formatter.formatFileSize(ctx, total)
}.getOrNull()

/** Full-screen file browser. Replaces the system picker so every file on the phone is reachable. */
@Composable
fun FilePicker(
    kind: InKind,
    multiple: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onUseSystem: () -> Unit,
    onPicked: (List<PickedFile>) -> Unit,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(StorageAccess.hasAllFilesAccess()) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = StorageAccess.hasAllFilesAccess() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val rootList = remember { roots(ctx) }
    val storageRoot = remember { Environment.getExternalStorageDirectory() }
    // Whole drives: going "up" from one of these shows the home list of storages.
    val volumePaths = remember { rootList.filter { it.first == "Internal storage" || it.first == "SD card" }.map { it.second.path }.toSet() }
    var dir by rememberSaveable { mutableStateOf(lastDir ?: storageRoot.path) }
    var showAll by rememberSaveable { mutableStateOf(kind.exts == null) }
    var sort by rememberSaveable { mutableStateOf(Sort.Newest) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var placesMenu by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val byPath = remember(entries) { entries.associateBy { it.file.path } }
    val atHome = dir == HOME
    val dateFmt = remember { SimpleDateFormat("M/d/yy h:mm a", Locale.getDefault()) }

    LaunchedEffect(dir, showAll, showHidden, granted, refreshTick) {
        if (dir == HOME) { entries = emptyList(); loading = false; return@LaunchedEffect }
        loading = true
        entries = withContext(Dispatchers.IO) { listDir(File(dir), kind, showAll, showHidden) }
        lastDir = dir
        loading = false
    }

    val canUp = !atHome
    fun goUp() {
        query = ""
        dir = if (dir in volumePaths) HOME else File(dir).parentFile?.path ?: HOME
    }
    BackHandler(enabled = canUp) { goUp() }

    val shown = remember(entries, query, sort) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) entries else entries.filter { it.file.name.contains(q, ignoreCase = true) }
        val cmp: Comparator<Entry> = when (sort) {
            Sort.Newest -> compareByDescending { it.modified }
            Sort.Name -> compareBy { it.file.name.lowercase() }
            Sort.Size -> compareByDescending { it.size }
        }
        filtered.filter { it.isDir }.sortedWith(compareBy { it.file.name.lowercase() }) + filtered.filter { !it.isDir }.sortedWith(cmp)
    }

    fun finish(paths: List<String>) {
        val picked = paths.mapNotNull { byPath[it] }.map {
            PickedFile(it.file.toUri(), it.file.name, it.companion?.path, it.live)
        }
        if (picked.isNotEmpty()) onPicked(picked)
    }

    val folderName = when {
        atHome -> title
        dir == storageRoot.path -> "Internal storage"
        else -> File(dir).name.ifEmpty { "/" }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TopAppBar(
                    title = {
                        Box {
                            Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { placesMenu = true }.padding(vertical = 2.dp, horizontal = 4.dp)) {
                                Text(folderName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (atHome) "Choose a location" else dir, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            DropdownMenu(expanded = placesMenu, onDismissRequest = { placesMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("All locations") }, leadingIcon = { Icon(Icons.Rounded.Home, null) },
                                    onClick = { dir = HOME; query = ""; placesMenu = false },
                                )
                                rootList.forEach { (label, f) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        leadingIcon = { Icon(if (label == "Internal storage" || label == "SD card") Icons.Rounded.Storage else Icons.Rounded.Folder, null) },
                                        onClick = { dir = f.path; query = ""; placesMenu = false },
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (canUp) IconButton(onClick = { goUp() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Up one folder") }
                        else IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
                    },
                    actions = {
                        if (!atHome) IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(if (searching) Icons.Rounded.SearchOff else Icons.Rounded.Search, "Search")
                        }
                        if (canUp) IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
                        if (!atHome) Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Show hidden files") },
                                    leadingIcon = { Icon(if (showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null) },
                                    trailingIcon = { if (showHidden) Icon(Icons.Rounded.Check, null) },
                                    onClick = { showHidden = !showHidden; menu = false },
                                )
                                HorizontalDivider()
                                Text("View", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                listOf(false to "List", true to "Grid").forEach { (isGrid, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        leadingIcon = { Icon(if (isGrid) Icons.Rounded.GridView else Icons.Rounded.ViewList, null) },
                                        trailingIcon = { if (grid == isGrid) Icon(Icons.Rounded.Check, null) },
                                        onClick = { grid = isGrid; menu = false },
                                    )
                                }
                                HorizontalDivider()
                                Text("Sort by", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Sort.entries.forEach { s2 ->
                                    DropdownMenuItem(
                                        text = { Text(s2.label) }, onClick = { sort = s2; menu = false },
                                        trailingIcon = { if (sort == s2) Icon(Icons.Rounded.Check, null) },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )

                if (!granted) {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Allow all-files access to browse your whole phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { StorageAccess.openAllFilesSettings(ctx) }) { Text("Allow") }
                                TextButton(onClick = { onDismiss(); onUseSystem() }) { Text("Use system picker") }
                            }
                        }
                    }
                }

                if (!atHome && kind.exts != null) Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !showAll, onClick = { showAll = false }, label = { Text(kindLabel(kind)) },
                        leadingIcon = if (!showAll) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                    )
                    FilterChip(
                        selected = showAll, onClick = { showAll = true }, label = { Text("All files") },
                        leadingIcon = if (showAll) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                    )
                }

                if (searching && !atHome) SearchField(query, { query = it }, "Search in this folder", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        atHome -> LazyColumn(Modifier.fillMaxSize()) {
                            items(rootList, key = { it.second.path + it.first }) { (label, f) ->
                                val isDrive = label == "Internal storage" || label == "SD card"
                                Row(
                                    Modifier.fillMaxWidth().clickable { dir = f.path; query = "" }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (!isDrive) Icons.Rounded.Folder else if (label == "SD card") Icons.Rounded.SdCard else Icons.Rounded.Storage,
                                        null, Modifier.size(44.dp).padding(start = 4.dp), tint = if (isDrive) StorageGreen else FolderOrange,
                                    )
                                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                        Text(label, style = MaterialTheme.typography.bodyLarge)
                                        Text(f.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isDrive) usageOf(ctx, f)?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        else -> PullToRefreshBox(isRefreshing = loading, onRefresh = { refreshTick++ }, modifier = Modifier.fillMaxSize()) {
                            val onRow: (Entry) -> Unit = { e ->
                                val order = selected.indexOf(e.file.path)
                                when {
                                    e.isDir -> { dir = e.file.path; query = "" }
                                    !multiple -> finish(listOf(e.file.path))
                                    order >= 0 -> selected.remove(e.file.path)
                                    else -> selected.add(e.file.path)
                                }
                            }
                            val emptyHint: @Composable () -> Unit = {
                                if (shown.isEmpty() && !loading) Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (query.isNotBlank()) "No matches" else "Nothing to pick here", style = MaterialTheme.typography.titleMedium)
                                    if (!showAll && kind.exts != null && query.isBlank()) TextButton(onClick = { showAll = true }) { Text("Show all files") }
                                }
                            }
                            if (grid) LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                                item(key = "..up", span = { GridItemSpan(maxLineSpan) }) { UpFolderRow(Modifier.animateItem()) { goUp() } }
                                item(key = "..hint", span = { GridItemSpan(maxLineSpan) }) { emptyHint() }
                                gridItems(shown, key = { it.file.path }) { e ->
                                    FileTile(
                                        e.file.name, e.file, e.isDir, Modifier.animateItem(), live = e.live,
                                        selectedOrder = selected.indexOf(e.file.path), showSelect = multiple && !e.isDir,
                                    ) { onRow(e) }
                                }
                            } else LazyColumn(Modifier.fillMaxSize()) {
                                item(key = "..up") { UpFolderRow(Modifier.animateItem()) { goUp() } }
                                item(key = "..hint") { emptyHint() }
                                items(shown, key = { it.file.path }) { e ->
                                    FileRow(
                                        name = e.file.name, file = e.file, isDir = e.isDir,
                                        size = if (e.isDir) null else Formatter.formatFileSize(ctx, e.size),
                                        date = dateFmt.format(Date(e.modified)),
                                        live = e.live, selectedOrder = selected.indexOf(e.file.path), showSelect = multiple && !e.isDir,
                                        modifier = Modifier.animateItem(), onClick = { onRow(e) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (multiple) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (selected.isEmpty()) "Tap files to select" else "${selected.size} selected", style = MaterialTheme.typography.titleSmall)
                            Text("Files are used in the order you pick them", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { shown.filter { !it.isDir }.forEach { if (it.file.path !in selected) selected.add(it.file.path) } }) { Text("All") }
                        TextButton(onClick = { selected.clear() }, enabled = selected.isNotEmpty()) { Text("Clear") }
                        Button(onClick = { finish(selected.toList()) }, enabled = selected.isNotEmpty(), shape = CircleShape) { Text("Select") }
                    }
                }
            }
        }
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
