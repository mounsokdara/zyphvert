@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mounsokdara.zyphvert.ui.tool

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.ui.components.AppSheet
import com.mounsokdara.zyphvert.tools.*
import com.mounsokdara.zyphvert.ui.components.SectionLabel
import com.mounsokdara.zyphvert.ui.home.Tool
import com.mounsokdara.zyphvert.ui.home.accent
import com.mounsokdara.zyphvert.ui.files.PickRequest
import com.mounsokdara.zyphvert.ui.files.rememberFilePicker
import com.mounsokdara.zyphvert.util.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun ToolScreen(tool: Tool, seedPath: String?, onBack: () -> Unit, onQueued: () -> Unit, vm: ToolViewModel) {
    val spec = toolSpecs[tool.id]
    val ctx = LocalContext.current
    var askStorage by remember { mutableStateOf(false) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    if (spec == null) {
        Scaffold(topBar = { TopAppBar(title = { Text(tool.title) }, navigationIcon = { BackButton(onBack) }) }) { p ->
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { Text("This tool isn't available.") }
        }
        return
    }
    LaunchedEffect(spec.id, seedPath) { vm.init(spec, seedPath) }
    LaunchedEffect(vm.picked[spec.inputs.first().id]?.firstOrNull()?.uri) { vm.loadMeta(ctx, spec) }

    val ready = spec.inputs.all { it.optional || vm.picked[it.id].orEmpty().isNotEmpty() }
    val vals: Map<String, String> = vm.values
    fun start() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.submit(ctx, spec, tool.title, onQueued)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tool.title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { BackButton(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { if (!StorageAccess.hasAllFilesAccess()) askStorage = true else start() },
                        enabled = ready && !vm.busy,
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().height(56.dp),
                        shape = CircleShape,
                    ) {
                        if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(spec.actionLabel, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IntroCard(tool, spec)

                spec.inputs.forEach { slot ->
                    SlotCard(slot, vm.picked[slot.id].orEmpty()) { vm.setFiles(slot.id, it) }
                }

                val visible = spec.options.filter { it.showIf?.invoke(vals) != false }
                val basic = visible.filter { !it.advanced }
                val advanced = visible.filter { it.advanced }
                basic.forEach { OptionRow(it, vm, spec) }
                if (advanced.isNotEmpty()) {
                    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().animateContentSize()) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { advancedOpen = !advancedOpen }.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Advanced options", Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
                                Icon(if (advancedOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                            }
                            AnimatedVisibility(advancedOpen) {
                                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    advanced.forEach { OptionRow(it, vm, spec) }
                                }
                            }
                        }
                    }
                }

                spec.hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.remember = !vm.remember }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = vm.remember, onCheckedChange = { vm.remember = it })
                    Text("Use the same settings next time", style = MaterialTheme.typography.bodyMedium)
                }
                vm.error?.let {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (askStorage) {
        AlertDialog(
            onDismissRequest = { askStorage = false },
            title = { Text("Allow access to all files?") },
            text = { Text("With all-files access, converted files are saved to Zyphvert/ on your phone and you can pick files from anywhere. Without it, files go to the app's own folder.") },
            confirmButton = { TextButton(onClick = { askStorage = false; StorageAccess.openAllFilesSettings(ctx) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { askStorage = false; start() }) { Text("Use app folder") } },
        )
    }
}

@Composable private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
}

@Composable
private fun IntroCard(tool: Tool, spec: ToolSpec) {
    val accent = tool.category.accent()
    Surface(shape = CardShape, color = accent.container, contentColor = accent.content, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            Icon(tool.icon, null, Modifier.size(32.dp))
            Text(spec.intro, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SlotCard(slot: InputSlot, files: List<PickedFile>, onChange: (List<PickedFile>) -> Unit) {
    val ctx = LocalContext.current
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val add = uris.map { PickedFile(it, ctx.displayName(it)) }
            onChange(if (slot.multiple) files + add else add.take(1))
        }
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onChange(listOf(PickedFile(uri, ctx.displayName(uri))))
    }
    val launchPicker = rememberFilePicker(
        onUseSystem = { if (slot.multiple) multi.launch(slot.kind.mimes) else single.launch(slot.kind.mimes) },
        onPicked = { picked -> onChange(if (slot.multiple) files + picked else picked.take(1)) },
    )
    fun pick() { launchPicker(PickRequest(slot.kind, slot.multiple, slot.label)) }

    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(slot.label, style = MaterialTheme.typography.titleMedium)
                    if (slot.optional) Text("Optional", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (files.isEmpty()) FilledTonalButton(onClick = ::pick) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)); Text("  Choose")
                } else TextButton(onClick = ::pick) { Text(if (slot.multiple) "Add more" else "Change") }
            }
            files.forEachIndexed { i, f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(fileIcon(f.name), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(f.name, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (slot.multiple && files.size > 1) {
                        IconButton(onClick = { onChange(files.swap(i, i - 1)) }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, "Move up", Modifier.size(18.dp)) }
                        IconButton(onClick = { onChange(files.swap(i, i + 1)) }, enabled = i < files.lastIndex) { Icon(Icons.Rounded.ArrowDownward, "Move down", Modifier.size(18.dp)) }
                    }
                    IconButton(onClick = { onChange(files.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Rounded.Close, "Remove", Modifier.size(18.dp)) }
                }
            }
        }
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> {
    if (a !in indices || b !in indices) return this
    return toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }
}

private fun fileIcon(name: String) = when {
    isImageName(name) -> Icons.Rounded.Image
    isAudioName(name) -> Icons.Rounded.MusicNote
    else -> Icons.Rounded.Movie
}

@Composable
private fun OptionRow(opt: Opt, vm: ToolViewModel, spec: ToolSpec) {
    val value = vm.values[opt.id] ?: opt.default
    when (opt) {
        is Opt.Section -> SectionLabel(opt.label)
        is Opt.Name -> {
            val first = spec.inputs.firstOrNull()?.let { vm.picked[it.id].orEmpty().firstOrNull() }
            val multi = spec.inputs.firstOrNull()?.let { vm.picked[it.id].orEmpty().size > 1 } == true
            OutlinedTextField(
                value = value, onValueChange = { vm.values[opt.id] = it },
                label = { Text(opt.label) }, singleLine = true, enabled = !multi,
                placeholder = { Text(if (multi) "Each file keeps its own name" else first?.name?.substringBeforeLast('.') ?: "Same name as the original") },
                trailingIcon = { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary) },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
        }
        is Opt.Presets -> {
            var open by remember { mutableStateOf(false) }
            val current = opt.presets.firstOrNull { it.key == value }
            ValueTile(opt.label, current?.title ?: "Choose a ready-made setup") { open = true }
            if (open) PickerSheet(
                title = opt.label,
                items = opt.presets.map { Item(it.key, it.title, it.desc) },
                selected = value, onDismiss = { open = false },
            ) { key ->
                vm.values[opt.id] = key
                opt.presets.first { it.key == key }.let { vm.applyPreset(it.values) }
            }
        }
        is Opt.Choice -> {
            val short = opt.options.size <= 4 && opt.options.all { it.label.length <= 24 && it.desc == null }
            if (short) {
                Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(opt.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            opt.options.forEach { it ->
                                FilterChip(selected = value == it.key, onClick = { vm.values[opt.id] = it.key }, label = { Text(it.label) })
                            }
                        }
                    }
                }
            } else {
                var open by remember { mutableStateOf(false) }
                ValueTile(opt.label, opt.options.firstOrNull { it.key == value }?.label ?: value) { open = true }
                if (open) PickerSheet(opt.label, opt.options, value, { open = false }) { vm.values[opt.id] = it }
            }
        }
        is Opt.Slider -> Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row {
                    Text(opt.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("$value${opt.unit}", style = MaterialTheme.typography.titleSmall)
                }
                Slider(
                    value = (value.toFloatOrNull() ?: opt.initial.toFloat()),
                    onValueChange = { vm.values[opt.id] = it.toInt().toString() },
                    valueRange = opt.min.toFloat()..opt.max.toFloat(),
                    steps = if (opt.max - opt.min <= 60) opt.max - opt.min - 1 else 0,
                )
            }
        }
        is Opt.Text -> OutlinedTextField(
            value = value, onValueChange = { vm.values[opt.id] = it }, label = { Text(opt.label) },
            placeholder = { if (opt.hint.isNotEmpty()) Text(opt.hint) }, singleLine = true,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        is Opt.Toggle -> Surface(
            shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().clip(CardShape).clickable { vm.values[opt.id] = (value != "true").toString() },
        ) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(opt.label, style = MaterialTheme.typography.titleSmall)
                    opt.desc?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Switch(checked = value == "true", onCheckedChange = { vm.values[opt.id] = it.toString() })
            }
        }
        is Opt.Range -> RangeCard(opt, vm)
        is Opt.Tags -> TagsRow(opt, vm)
        is Opt.Metadata -> MetadataEditor(vm)
    }
}

@Composable
private fun ValueTile(label: String, value: String, onClick: () -> Unit) {
    Surface(
        shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(CardShape).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PickerSheet(title: String, items: List<Item>, selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val idx = items.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 2).coerceAtLeast(0))
    AppSheet(onDismiss) { close ->
        Text(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(state = list, modifier = Modifier.weight(1f, fill = false).navigationBarsPadding()) {
            itemsIndexed(items, key = { _, it -> it.key }) { _, it ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(it.key); close() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = it.key == selected, onClick = null)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(it.label, style = MaterialTheme.typography.bodyLarge)
                        it.desc?.let { d -> Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

private fun clock(sec: Double): String {
    val t = (sec * 10).toLong() / 10.0
    val m = (t / 60).toInt()
    val s = t - m * 60
    return String.format(Locale.US, "%d:%04.1f", m, s)
}

@Composable
private fun RangeCard(opt: Opt.Range, vm: ToolViewModel) {
    val ctx = LocalContext.current
    val first = vm.picked[opt.slot].orEmpty().firstOrNull()
    var dur by remember(first?.uri) { mutableStateOf(0.0) }
    LaunchedEffect(first?.uri) {
        dur = if (first == null) 0.0 else withContext(Dispatchers.IO) { durationSec(ctx, first.uri) } ?: 0.0
    }
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(opt.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (first == null || dur <= 0.0) {
                Text(
                    if (first == null) "Choose a file first, then pick the part you want." else "Couldn't read the length of this file. The whole file will be used.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val parts = (vm.values[opt.id] ?: "").split(",")
            val s = (parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, dur)
            val e = (parts.getOrNull(1)?.toDoubleOrNull() ?: dur).coerceIn(s, dur)
            fun set(ns: Double, ne: Double) {
                vm.values[opt.id] = String.format(Locale.US, "%.1f,%.1f", ns.coerceIn(0.0, dur), ne.coerceIn(ns.coerceIn(0.0, dur), dur))
            }
            if (opt.modes) {
                val mode = vm.values["${opt.id}.mode"] ?: "keep"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "keep", onClick = { vm.values["${opt.id}.mode"] = "keep" }, label = { Text("Keep selection") })
                    FilterChip(selected = mode == "remove", onClick = { vm.values["${opt.id}.mode"] = "remove" }, label = { Text("Remove selection") })
                }
            }
            RangeSlider(
                value = s.toFloat()..e.toFloat(),
                onValueChange = { set(it.start.toDouble(), it.endInclusive.toDouble()) },
                valueRange = 0f..dur.toFloat(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Stepper(clock(s), { set(s - 0.1, e) }, { set(s + 0.1, e) })
                Text(clock(e - s), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                Stepper(clock(e), { set(s, e - 0.1) }, { set(s, e + 0.1) })
            }
            Text("Total length ${clock(dur)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stepper(text: String, minus: () -> Unit, plus: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = minus) { Icon(Icons.Rounded.Remove, "Earlier") }
            Text(text, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = plus) { Icon(Icons.Rounded.Add, "Later") }
        }
    }
}

@Composable
private fun TagsRow(opt: Opt.Tags, vm: ToolViewModel) {
    var open by remember { mutableStateOf(false) }
    val summary = listOf("title", "artist", "album").mapNotNull { vm.values["tag.$it"]?.takeIf(String::isNotBlank) }.joinToString(" · ")
    ValueTile(opt.label, summary.ifEmpty { "Edit file details such as title, artist and album" }) { open = true }
    if (open) {
        var genreOpen by remember { mutableStateOf(false) }
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        AppSheet({ open = false }) { close ->
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Edit tags", style = MaterialTheme.typography.titleLarge)
                Text("Fields show what the file has now. Change one to edit it, or empty it to remove that tag.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().clickable { vm.values["tag.useFilename"] = (vm.values["tag.useFilename"] != "true").toString() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = vm.values["tag.useFilename"] == "true", onCheckedChange = { vm.values["tag.useFilename"] = it.toString() })
                    Text("Use file name as title")
                }
                tagKeys.forEach { (k, label) ->
                    val v = vm.values["tag.$k"].orEmpty()
                    if (k == "genre") {
                        OutlinedTextField(
                            value = v, onValueChange = { vm.values["tag.$k"] = it }, label = { Text(label) }, singleLine = true,
                            trailingIcon = { IconButton(onClick = { genreOpen = true }) { Icon(Icons.AutoMirrored.Rounded.List, "Choose genre") } },
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = v, onValueChange = { vm.values["tag.$k"] = it }, label = { Text(label) }, singleLine = k != "comment",
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Button(onClick = { focus.clearFocus(); close() }, Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text("Done") }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (genreOpen) PickerSheet("Genre", genres.map { Item(it, it) }, vm.values["tag.genre"].orEmpty(), { genreOpen = false }) { vm.values["tag.genre"] = it }
    }
}
