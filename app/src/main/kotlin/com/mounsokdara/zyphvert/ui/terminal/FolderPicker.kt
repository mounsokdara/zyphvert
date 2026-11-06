package com.mounsokdara.zyphvert.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SubdirectoryArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.ui.components.AppSheet
import com.mounsokdara.zyphvert.util.FsFallback
import java.io.File

/** A small folder chooser for the terminal: browse, then "Use this folder" runs `cd` there. */
@Composable
fun FolderPicker(start: File, root: File, onPick: (File) -> Unit, onDismiss: () -> Unit) {
    var dir by remember { mutableStateOf(start) }
    val folders = remember(dir) {
        FsFallback.children(dir)?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() }
    }
    val shortcuts = remember(root) {
        listOf("Internal storage" to root) + listOf("DCIM", "Download", "Movies", "Pictures", "Zyphvert")
            .map { it to File(root, it) }.filter { it.second.isDirectory }
    }

    AppSheet(onDismiss) { close ->
        Text("Choose a folder", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
        Text(
            dir.path, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcuts.forEach { (label, f) ->
                AssistChip(onClick = { dir = f }, label = { Text(label) }, leadingIcon = { Icon(Icons.Rounded.Storage, null, Modifier.size(18.dp)) })
            }
        }
        LazyColumn(Modifier.weight(1f, fill = false)) {
            dir.parentFile?.let { parent ->
                item(key = "..up") {
                    Row(Modifier.fillMaxWidth().clickable { dir = parent }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SubdirectoryArrowLeft, null)
                        Text("Up", Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (folders == null) item(key = "denied") {
                Text("Can't open this folder (permission denied).", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
            } else if (folders.isEmpty()) item(key = "empty") {
                Text("No folders in here.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else items(folders, key = { it.path }) { f ->
                Row(Modifier.fillMaxWidth().clickable { dir = f }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Text(f.name, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Button(
            onClick = { onPick(dir); close() },
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).navigationBarsPadding(),
        ) { Text("Use this folder") }
    }
}
