package com.mounsokdara.zyphvert.ui.more

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mounsokdara.zyphvert.tools.OutputPaths
import com.mounsokdara.zyphvert.ui.components.AppSheet
import com.mounsokdara.zyphvert.ui.components.InfoTile
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private fun scan(ctx: Context): List<File> =
    OutputPaths.root(ctx).walkTopDown().filter { it.isFile && !it.name.startsWith('.') }
        .sortedByDescending { it.lastModified() }.toList()

/** Everything Zyphvert has converted (the Zyphvert output folder), newest first, with open, share, rename and delete. */
@Composable
fun ConvertedFilesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<File>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<File?>(null) }
    var renaming by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }

    fun reload() {
        scope.launch { files = withContext(Dispatchers.IO) { scan(ctx) } }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { reload() }

    SubScreenScaffold(title = "Converted files", onBack = onBack) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; reload(); delay(500); refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            val list = files
            when {
                list == null -> Box(Modifier.fillMaxSize())
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing converted yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.absolutePath }) { f ->
                        InfoTile(
                            icon = Icons.Rounded.Movie,
                            title = f.name,
                            subtitle = Formatter.formatFileSize(ctx, f.length()) + " · " +
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(f.lastModified())),
                            onClick = { active = f },
                        )
                    }
                }
            }
        }
    }

    active?.let { f ->
        AppSheet({ active = null }) { close ->
            Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SheetAction(Icons.Rounded.OpenInNew, "Open") { close(); open(ctx, f) }
            SheetAction(Icons.Rounded.Share, "Share") { close(); share(ctx, f) }
            SheetAction(Icons.Rounded.DriveFileRenameOutline, "Rename") { close(); renaming = f }
            SheetAction(Icons.Rounded.Delete, "Delete") { close(); deleting = f }
        }
    }

    renaming?.let { f ->
        var name by remember(f) { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val target = File(f.parentFile, name.trim())
                    val ok = name.isNotBlank() && !target.exists() && f.renameTo(target)
                    if (!ok) Toast.makeText(ctx, "Couldn't rename", Toast.LENGTH_SHORT).show()
                    renaming = null; reload()
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this file?") },
            text = { Text(f.name) },
            confirmButton = {
                TextButton(onClick = {
                    if (!f.delete()) Toast.makeText(ctx, "Couldn't delete", Toast.LENGTH_SHORT).show()
                    deleting = null; reload()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(label) },
    )
}

private fun uriFor(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
private fun mimeOf(f: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

private fun open(ctx: Context, f: File) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(ctx, f), mimeOf(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun share(ctx: Context, f: File) {
    val send = Intent(Intent.ACTION_SEND).setType(mimeOf(f)).putExtra(Intent.EXTRA_STREAM, uriFor(ctx, f))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
