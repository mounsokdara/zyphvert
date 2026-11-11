@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mounsokdara.zyphvert.ui.tool

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.tools.*
import com.mounsokdara.zyphvert.ui.components.AppSheet
import com.mounsokdara.zyphvert.ui.files.PickRequest
import com.mounsokdara.zyphvert.ui.files.rememberFilePicker

private val MetaShape = RoundedCornerShape(24.dp)

@Composable
internal fun MetadataEditor(vm: ToolViewModel) {
    val files = vm.picked["media"].orEmpty()
    if (files.isEmpty()) return
    val info = vm.meta
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (files.size > 1) {
            Surface(shape = MetaShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Showing ${files.first().name}. Your changes apply to all ${files.size} files; tags you don't touch stay as they are.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        if (vm.metaLoading) {
            Surface(shape = MetaShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Reading the file…", Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            if (info == null) {
                Surface(shape = MetaShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text("Couldn't read this file's metadata. You can still add tags.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (info?.audio == true) CoverCard(vm, info.cover)
            TagsCard(vm, photo = info?.photo == true)
            if (info != null) DetailsCard(info)
        }
    }
}

@Composable
private fun CoverCard(vm: ToolViewModel, current: android.graphics.Bitmap?) {
    val ctx = LocalContext.current
    val newCover = vm.picked["cover"]?.firstOrNull()
    val removed = vm.values["meta.coverRemove"] == "true"
    val shown = if (newCover != null) vm.coverPreview else if (removed) null else current
    val has = newCover != null || (!removed && current != null)
    val system = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setCover(ctx, PickedFile(uri, ctx.displayName(uri)))
    }
    val launch = rememberFilePicker(
        onUseSystem = { system.launch(InKind.Image.mimes) },
        onPicked = { picked -> picked.firstOrNull()?.let { vm.setCover(ctx, it) } },
    )
    Surface(shape = MetaShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (shown != null) Image(shown.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cover art", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        newCover != null -> "New: ${newCover.name}"
                        removed -> "Will be removed"
                        current != null -> "Stored in the file"
                        else -> "No cover yet"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = { launch(PickRequest(InKind.Image, false, "Cover art")) }) { Text(if (has) "Replace" else "Add") }
                    if (has) TextButton(onClick = vm::removeCover) { Text("Remove") }
                    if (newCover != null || removed) TextButton(onClick = vm::resetCover) { Text("Undo") }
                }
            }
        }
    }
}

@Composable
private fun TagsCard(vm: ToolViewModel, photo: Boolean) {
    var adding by remember { mutableStateOf(false) }
    Surface(shape = MetaShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (photo) "Photo details" else "Tags", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${vm.metaTags.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Every tag the file has is listed. Change a value to edit it, or tap × to remove the tag.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!photo) {
                val on = vm.values["meta.useFilename"] == "true"
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.values["meta.useFilename"] = (!on).toString() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = { vm.values["meta.useFilename"] = it.toString() })
                    Text("Use file name as title")
                }
            }
            if (vm.metaTags.isEmpty()) {
                Text("Nothing stored yet.", style = MaterialTheme.typography.bodyMedium)
            }
            vm.metaTags.forEach { t ->
                OutlinedTextField(
                    value = t.value, onValueChange = { t.value = it },
                    label = { Text(t.key) }, singleLine = t.key.lowercase() !in longTextTags,
                    trailingIcon = { IconButton(onClick = { vm.removeTag(t) }) { Icon(Icons.Rounded.Close, "Remove ${t.key}") } },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("  Add tag")
            }
        }
    }
    if (adding) AddTagSheet(vm, photo) { adding = false }
}

@Composable
private fun AddTagSheet(vm: ToolViewModel, photo: Boolean, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    val have = vm.metaTags.map { it.key.lowercase() }.toSet()
    val options = (if (photo) exifSuggestions else mediaTagSuggestions).filter { it.lowercase() !in have }
    AppSheet(onDismiss) { close ->
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add tag", style = MaterialTheme.typography.titleLarge)
            if (!photo) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = custom, onValueChange = { custom = it }, label = { Text("Your own tag name") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = { vm.addTag(custom); close() }, enabled = custom.isNotBlank()) { Text("Add") }
                }
            }
            if (options.isNotEmpty()) {
                Text("Common tags", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { o -> AssistChip(onClick = { vm.addTag(o); close() }, label = { Text(o) }) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailsCard(info: MetaInfo) {
    if (info.sections.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Surface(shape = MetaShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("All details", Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
                if (open) IconButton(onClick = { clipboard.setText(AnnotatedString(info.asText())) }) { Icon(Icons.Rounded.ContentCopy, "Copy all") }
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tap a value to copy it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    info.sections.forEach { s ->
                        Text(s.title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        s.rows.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { clipboard.setText(AnnotatedString(row.value)) }.padding(vertical = 4.dp),
                            ) {
                                Text(row.key, Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(row.value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
