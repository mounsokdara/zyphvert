package com.mounsokdara.zyphvert.ui.tool

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import com.mounsokdara.zyphvert.ui.home.Formats
import com.mounsokdara.zyphvert.ui.home.MediaKind
import com.mounsokdara.zyphvert.ui.home.Tool
import com.mounsokdara.zyphvert.ui.home.accent
import com.mounsokdara.zyphvert.util.StorageAccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(
    tool: Tool,
    onBack: () -> Unit,
    vm: ToolViewModel = hiltViewModel(),
) {
    val accent = tool.category.accent()
    val ctx = LocalContext.current
    val listState = rememberLazyListState()

    val pickInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) { }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
            vm.setInput(uri, name)
        }
    }
    val pickOutput = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) { }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
            vm.setOutput(uri, name)
        }
    }
    val pickExtra = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) { }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
            vm.setExtra(uri, name)
        }
    }

    LaunchedEffect(tool.id) {
        if (vm.outputFormat.isBlank()) {
            vm.outputFormat = Formats.defaultId(tool.id)
            if (tool.id == "compress") vm.quality = "medium"
        }
    }

    LaunchedEffect(vm.lines.size) {
        if (vm.lines.isNotEmpty()) listState.scrollToItem(vm.lines.lastIndex)
    }

    SubScreenScaffold(title = tool.title, onBack = onBack) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Engine status
            Text(
                vm.engine.version(),
                style = MaterialTheme.typography.labelMedium,
                color = if (vm.engine.isAvailable) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )

            if (!StorageAccess.hasAllFilesAccess()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Grant All files access for the best experience with any path.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        FilledTonalButton(onClick = { StorageAccess.openAllFilesSettings(ctx) }) {
                            Text("Grant")
                        }
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = accent.container,
                            contentColor = accent.content,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(tool.icon, null, Modifier.size(26.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(tool.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Input / Output
                FilePickCard(
                    title = "Input file",
                    label = vm.inputLabel,
                    onPick = { pickInput.launch(arrayOf("*/*")) },
                    onClear = { vm.setInput(null, null) },
                )
                FilePickCard(
                    title = "Output file",
                    label = vm.outputLabel,
                    onPick = {
                        val suggested = suggestedOutputName(tool, vm.outputFormat)
                        pickOutput.launch(suggested)
                    },
                    onClear = { vm.setOutput(null, null) },
                    pickIcon = Icons.Rounded.SaveAlt,
                    pickLabel = "Choose output",
                )
                if (tool.id == "merge") {
                    FilePickCard(
                        title = "Second file",
                        label = vm.extraLabel,
                        onPick = { pickExtra.launch(arrayOf("*/*")) },
                        onClear = { vm.setExtra(null, null) },
                        pickLabel = "Add the file to join after",
                    )
                }

                // Advanced options per tool
                OptionsCard(tool, vm)

                // Log
                if (vm.lines.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    ) {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                            ) {
                                items(vm.lines.size) { i ->
                                    val line = vm.lines[i]
                                    Text(
                                        line.text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        color = when (line.kind) {
                                            LogKind.Command -> MaterialTheme.colorScheme.primary
                                            LogKind.Error -> MaterialTheme.colorScheme.error
                                            LogKind.Info -> MaterialTheme.colorScheme.tertiary
                                            LogKind.Output -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Actions
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (vm.running) {
                    Button(
                        onClick = vm::stop,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Rounded.Stop, null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = {
                            if (!vm.engine.isAvailable) {
                                Toast.makeText(
                                    ctx,
                                    "FFmpeg engine not in this build. Run the FFmpeg AAR workflow.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            vm.run(tool, ctx)
                        },
                        enabled = vm.inputUri != null && vm.outputUri != null &&
                            (tool.id != "merge" || vm.extraUri != null),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(22.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Start ${tool.title}")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePickCard(
    title: String,
    label: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    pickIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.FileOpen,
    pickLabel: String = "Choose file",
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (label == null) {
                FilledTonalButton(
                    onClick = onPick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(pickIcon, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(pickLabel)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, "Remove", Modifier.size(18.dp))
                        }
                    }
                }
                FilledTonalButton(
                    onClick = onPick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(pickIcon, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Change")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsCard(tool: Tool, vm: ToolViewModel) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Options", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            when (tool.id) {
                "video", "audio", "image" -> {
                    val choices = Formats.forTool(tool.id)
                    val selected = vm.outputFormat.ifBlank { choices.firstOrNull()?.id.orEmpty() }
                    val choice = Formats.resolve(tool.id, selected)
                    Text(
                        "Convert to anything. Pick a format, then choose where to save it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChipRow("Convert to", choices.map { it.id }, selected, { id ->
                        choices.firstOrNull { it.id == id }?.label ?: id
                    }) { vm.selectFormat(tool.id, it) }
                    if (choice == null) {
                        Text(
                            "Pick a format to continue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else when (choice.kind) {
                        MediaKind.Audio -> {
                            ChipRow("Bitrate", listOf("96k", "128k", "192k", "256k", "320k"), vm.audioBitrate) {
                                vm.audioBitrate = it
                            }
                            if (tool.id != "image") TimeFields(vm)
                        }
                        MediaKind.Sequence -> {
                            OutlinedTextField(
                                value = if (vm.fps == "original") "1" else vm.fps,
                                onValueChange = { vm.fps = it },
                                label = { Text("Frames per second") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Text(
                                "The output name should contain %04d, for example frame_%04d.jpg.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TimeFields(vm)
                        }
                        MediaKind.Image -> {
                            if (tool.id == "video" && (choice.id == "gif" || choice.id == "webp")) {
                                OutlinedTextField(
                                    value = vm.gifFps,
                                    onValueChange = { vm.gifFps = it },
                                    label = { Text("FPS") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = vm.gifWidth,
                                    onValueChange = { vm.gifWidth = it },
                                    label = { Text("Width (px)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                                TimeFields(vm)
                            } else {
                                ChipRow("Scale", listOf("original", "1080", "720", "480"), vm.scale) { vm.scale = it }
                            }
                        }
                        MediaKind.Video -> {
                            if (tool.id == "image" || tool.id == "audio") {
                                if (tool.id == "image") {
                                    OutlinedTextField(
                                        value = vm.endTime.ifBlank { "5" },
                                        onValueChange = { vm.endTime = it },
                                        label = { Text("Duration (seconds)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                    )
                                    ChipRow("Scale", listOf("original", "1080", "720", "480"), vm.scale) { vm.scale = it }
                                } else {
                                    Text(
                                        "Builds a video from this audio.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                ChipRow("Scale", listOf("original", "1080", "720", "480", "360"), vm.scale) { vm.scale = it }
                                ChipRow("Frame rate", listOf("original", "24", "30", "60"), vm.fps) { vm.fps = it }
                                ChipRow("Quality", listOf("low", "medium", "high"), vm.quality) { vm.quality = it }
                                ChipRow("Codec", listOf("auto", "h264", "h265", "vp9", "copy"), vm.codec) { vm.codec = it }
                                TimeFields(vm)
                            }
                        }
                    }
                    choice?.let {
                        val label = vm.outputLabel
                        val ext = label?.substringAfterLast('.', "")?.lowercase().orEmpty()
                        val mismatch = label != null && it.kind != MediaKind.Sequence && ext.isNotEmpty() && ext != it.ext
                        if (mismatch) {
                            Text(
                                "Save the file as .${it.ext} so it matches ${it.label}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                "compress" -> {
                    val choices = Formats.forTool("compress")
                    ChipRow("File type", choices.map { it.id }, vm.outputFormat.ifBlank { "mp4" }, { id ->
                        choices.first { it.id == id }.label
                    }) { vm.selectFormat(tool.id, it) }
                    ChipRow("Size", listOf("low", "medium", "high"), vm.quality, {
                        when (it) {
                            "low" -> "Smaller"
                            "high" -> "Clearer"
                            else -> "Balanced"
                        }
                    }) { vm.quality = it }
                }
                "merge" -> {
                    Text(
                        "The second file is joined after the first. Both should be the same kind of media.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val choices = Formats.forTool("merge")
                    ChipRow("Output", choices.map { it.id }, vm.outputFormat.ifBlank { "mp4" }, { id ->
                        choices.first { it.id == id }.label
                    }) { vm.selectFormat(tool.id, it) }
                    if (vm.outputFormat != "mp3") {
                        ChipRow("Quality", listOf("low", "medium", "high"), vm.quality) { vm.quality = it }
                    }
                }
                "volume" -> {
                    ChipRow("Loudness", listOf("0.5", "0.75", "1.0", "1.25", "1.5", "2.0"), vm.gain) { vm.gain = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mute", modifier = Modifier.weight(1f))
                        Switch(checked = vm.mute, onCheckedChange = { vm.mute = it })
                    }
                }
                "subs" -> {
                    Text(
                        "Saves the first subtitle track as an SRT file. The video needs embedded subtitles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                "ringtone" -> {
                    ChipRow("Quality / bitrate", listOf("96k", "128k", "192k", "256k", "320k"), vm.audioBitrate) {
                        vm.audioBitrate = it
                    }
                    TimeFields(vm)
                }
                "trim" -> {
                    TimeFields(vm)
                    ChipRow("Mode", listOf("copy", "re-encode"), if (vm.codec == "copy") "copy" else "re-encode") {
                        vm.codec = if (it == "copy") "copy" else "h264"
                    }
                    if (vm.codec != "copy") {
                        ChipRow("Quality", listOf("low", "medium", "high"), vm.quality) { vm.quality = it }
                    }
                }
                "speed" -> {
                    ChipRow("Speed", listOf("0.5", "0.75", "1.0", "1.25", "1.5", "2.0"), vm.speed) {
                        vm.speed = it
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reverse", modifier = Modifier.weight(1f))
                        Switch(checked = vm.reverse, onCheckedChange = { vm.reverse = it })
                    }
                    ChipRow("Quality", listOf("low", "medium", "high"), vm.quality) { vm.quality = it }
                }
                "crop" -> {
                    ChipRow("Rotate", listOf("0", "90", "180", "270"), vm.rotate) { vm.rotate = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Flip horizontal", modifier = Modifier.weight(1f))
                        Switch(checked = vm.flipH, onCheckedChange = { vm.flipH = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Flip vertical", modifier = Modifier.weight(1f))
                        Switch(checked = vm.flipV, onCheckedChange = { vm.flipV = it })
                    }
                    ChipRow("Scale", listOf("original", "1080", "720", "480"), vm.scale) { vm.scale = it }
                    ChipRow("Quality", listOf("low", "medium", "high"), vm.quality) { vm.quality = it }
                }
                "filters", "wm" -> {
                    ChipRow("Scale", listOf("original", "1080", "720", "480"), vm.scale) { vm.scale = it }
                    ChipRow("Quality", listOf("low", "medium", "high"), vm.quality) { vm.quality = it }
                    ChipRow("Codec", listOf("auto", "h264", "h265", "vp9"), vm.codec) { vm.codec = it }
                }
                "sticker" -> {
                    ChipRow("Scale", listOf("original", "1080", "720", "480"), vm.scale) { vm.scale = it }
                }
                "scrub", "meta" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Strip all metadata", modifier = Modifier.weight(1f))
                        Switch(checked = vm.stripMetadata, onCheckedChange = { vm.stripMetadata = it })
                    }
                }
                else -> {
                    Text(
                        "Use Custom FFmpeg for full control.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Common
            if (tool.id !in listOf("scrub", "meta", "batch", "subs")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Strip metadata", modifier = Modifier.weight(1f))
                    Switch(checked = vm.stripMetadata, onCheckedChange = { vm.stripMetadata = it })
                }
            }
        }
    }
}

@Composable
private fun TimeFields(vm: ToolViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = vm.startTime,
            onValueChange = { vm.startTime = it },
            label = { Text("Start (HH:MM:SS)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("00:00:00") },
        )
        OutlinedTextField(
            value = vm.endTime,
            onValueChange = { vm.endTime = it },
            label = { Text("End (HH:MM:SS)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: String,
    optionLabel: (String) -> String = { it },
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = selected == opt,
                    onClick = { onSelect(opt) },
                    label = { Text(optionLabel(opt)) },
                )
            }
        }
    }
}

private fun suggestedOutputName(tool: Tool, formatId: String): String {
    val choice = Formats.resolve(tool.id, formatId.ifBlank { Formats.defaultId(tool.id) })
    if (choice != null) {
        return if (choice.kind == MediaKind.Sequence) "frame_%04d.${choice.ext}" else "output.${choice.ext}"
    }
    return when (tool.id) {
        "ringtone" -> "output.mp3"
        "sticker" -> "output.jpg"
        "subs" -> "output.srt"
        "scrub", "meta", "trim", "speed", "crop", "filters", "wm", "volume" -> "output.mp4"
        else -> "output"
    }
}
