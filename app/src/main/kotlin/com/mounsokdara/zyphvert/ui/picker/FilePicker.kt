@file:OptIn(ExperimentalMaterial3Api::class)

package com.mounsokdara.zyphvert.ui.picker

import android.os.Environment
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
import com.mounsokdara.zyphvert.ui.components.SearchField
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

private fun listDir(dir: File, kind: InKind, showAll: Boolean): List<Entry> {
    val all = dir.listFiles()?.filter { !it.name.startsWith(".") } ?: return emptyList()
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
    // SD cards and USB drives show up as extra app-files dirs; walk up to the volume root.
    ctx.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { d ->
        val root = d.path.substringBefore("/Android/")
        if (root != d.path && File(root).isDirectory) list += "SD card" to File(root)
    }
    return list
}

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
    var dir by rememberSaveable { mutableStateOf(lastDir ?: storageRoot.path) }
    var showAll by rememberSaveable { mutableStateOf(kind.exts == null) }
    var sort by rememberSaveable { mutableStateOf(Sort.Newest) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val byPath = remember(entries) { entries.associateBy { it.file.path } }

    LaunchedEffect(dir, showAll, granted) {
        loading = true
        entries = withContext(Dispatchers.IO) { listDir(File(dir), kind, showAll) }
        lastDir = dir
        loading = false
    }

    val parent = File(dir).parentFile
    val canUp = parent != null && dir != "/" && File(dir).path.length > "/storage".length
    BackHandler(enabled = canUp) { dir = parent!!.path; query = "" }

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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                dir.removePrefix(storageRoot.path).ifEmpty { "/" }, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") } },
                    actions = {
                        IconButton(onClick = { dir = parent!!.path; query = "" }, enabled = canUp) { Icon(Icons.Rounded.ArrowUpward, "Up one folder") }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                Sort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label) }, onClick = { sort = s; sortMenu = false },
                                        leadingIcon = { if (sort == s) Icon(Icons.Rounded.Check, null) },
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

                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rootList, key = { it.second.path + it.first }) { (label, f) ->
                        FilterChip(selected = dir == f.path, onClick = { dir = f.path; query = "" }, label = { Text(label) })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchField(query, { query = it }, "Search in this folder", Modifier.weight(1f))
                    if (kind.exts != null) FilterChip(selected = showAll, onClick = { showAll = !showAll }, label = { Text("All files") })
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading && entries.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        shown.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.FolderOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(if (query.isNotBlank()) "No matches" else "Nothing to pick here", style = MaterialTheme.typography.titleMedium)
                            if (!showAll && kind.exts != null && query.isBlank()) {
                                TextButton(onClick = { showAll = true }) { Text("Show all files") }
                            }
                        }
                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(shown, key = { it.file.path }) { e ->
                                val order = selected.indexOf(e.file.path)
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        when {
                                            e.isDir -> { dir = e.file.path; query = "" }
                                            !multiple -> finish(listOf(e.file.path))
                                            order >= 0 -> selected.remove(e.file.path)
                                            else -> selected.add(e.file.path)
                                        }
                                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        iconFor(e), null, Modifier.size(28.dp),
                                        tint = if (e.isDir) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                    )
                                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                        Text(e.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                        val meta = if (e.isDir) "Folder" else
                                            Formatter.formatShortFileSize(ctx, e.size) + " · " + SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(e.modified))
                                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (e.live) Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text("LIVE", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                    if (multiple && !e.isDir) {
                                        Spacer(Modifier.width(8.dp))
                                        if (order >= 0) Box(
                                            Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center,
                                        ) { Text("${order + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary) }
                                        else Icon(Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.outline)
                                    }
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

private fun iconFor(e: Entry) = when {
    e.isDir -> Icons.Rounded.Folder
    e.live -> Icons.Rounded.MotionPhotosOn
    e.file.extension.lowercase() in IMAGE_EXTS -> Icons.Rounded.Image
    e.file.extension.lowercase() in AUDIO_EXTS -> Icons.Rounded.MusicNote
    e.file.extension.lowercase() in VIDEO_EXTS -> Icons.Rounded.Movie
    e.file.extension.lowercase() in SUBTITLE_EXTS -> Icons.Rounded.ClosedCaption
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
}
