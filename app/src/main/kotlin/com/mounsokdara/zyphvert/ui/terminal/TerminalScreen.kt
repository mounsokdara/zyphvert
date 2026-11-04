package com.mounsokdara.zyphvert.ui.terminal

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold

private val examples = listOf(
    "Convert video to WebM" to "-i INPUT -c:v libvpx-vp9 -crf 32 -b:v 0 -c:a libopus OUTPUT.webm",
    "Extract MP3 audio" to "-i INPUT -vn -c:a libmp3lame -q:a 2 OUTPUT.mp3",
    "Trim without re-encoding" to "-ss 00:00:10 -to 00:00:20 -i INPUT -c copy OUTPUT.mp4",
    "Video to GIF" to "-i INPUT -vf fps=12,scale=480:-1:flags=lanczos OUTPUT.gif",
    "Hardware H.264 encode" to "-i INPUT -c:v h264_mediacodec -b:v 4M -c:a aac OUTPUT.mp4",
    "Strip all metadata" to "-i INPUT -map_metadata -1 -c copy OUTPUT.mp4",
    "List supported formats" to "-formats",
    "List codecs" to "-codecs",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(vm: TerminalViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val saved by vm.saved.collectAsState()
    val listState = rememberLazyListState()
    var help by remember { mutableStateOf(false) }

    val pickInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val saf = vm.engine.safRead(ctx, uri)
            if (saf != null) vm.insert(saf) else Toast.makeText(ctx, "Needs the FFmpeg engine", Toast.LENGTH_SHORT).show()
        }
    }
    val pickOutput = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            val saf = vm.engine.safWrite(ctx, uri)
            if (saf != null) vm.insert(saf) else Toast.makeText(ctx, "Needs the FFmpeg engine", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(vm.lines.size) {
        if (vm.lines.isNotEmpty()) listState.scrollToItem(vm.lines.lastIndex)
    }

    SubScreenScaffold(
        title = "FFmpeg terminal",
        onBack = onBack,
        actions = {
            IconButton(onClick = { clipboard.setText(AnnotatedString(vm.logText())) }, enabled = vm.lines.isNotEmpty()) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy log")
            }
            IconButton(onClick = vm::clear, enabled = vm.lines.isNotEmpty()) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear log")
            }
            IconButton(onClick = { help = true }) { Icon(Icons.Rounded.HelpOutline, contentDescription = "Help") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                vm.engine.version(),
                style = MaterialTheme.typography.labelMedium,
                color = if (vm.engine.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !vm.probe, onClick = { vm.probe = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("ffmpeg") }
                SegmentedButton(selected = vm.probe, onClick = { vm.probe = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("ffprobe") }
            }

            // Log
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (vm.lines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Output appears here.\nTap ? for example commands.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            items(vm.lines.size) { i ->
                                val line = vm.lines[i]
                                Text(
                                    line.text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = when (line.kind) {
                                        LineKind.Command -> MaterialTheme.colorScheme.primary
                                        LineKind.Error -> MaterialTheme.colorScheme.error
                                        LineKind.Info -> MaterialTheme.colorScheme.tertiary
                                        LineKind.Output -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Saved commands
            if (saved.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(saved, key = { it }) { cmd ->
                        InputChip(
                            selected = false,
                            onClick = { vm.command = cmd },
                            label = { Text(cmd, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 2.dp)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close, contentDescription = "Delete saved command",
                                    modifier = Modifier.size(18.dp).clickable { vm.deleteSaved(cmd) },
                                )
                            },
                        )
                    }
                }
            }

            // Input
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vm.command,
                    onValueChange = { vm.command = it },
                    modifier = Modifier.weight(1f),
                    prefix = { Text(if (vm.probe) "ffprobe " else "ffmpeg ", fontFamily = FontFamily.Monospace) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    placeholder = { Text("-i INPUT OUTPUT") },
                    minLines = 1,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.large,
                )
                if (vm.running) {
                    FilledIconButton(
                        onClick = vm::stop,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                        modifier = Modifier.size(56.dp),
                    ) { Icon(Icons.Rounded.Stop, contentDescription = "Stop") }
                } else {
                    FilledIconButton(onClick = vm::run, enabled = vm.command.isNotBlank(), modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Run")
                    }
                }
            }
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { pickInput.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp)); Text("  Input file")
                }
                TextButton(onClick = { pickOutput.launch("output") }) {
                    Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp)); Text("  Output file")
                }
                TextButton(onClick = vm::saveCurrent, enabled = vm.command.isNotBlank()) {
                    Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp)); Text("  Save")
                }
            }
        }
    }

    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text("Example commands") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Type only the arguments, without the leading \"ffmpeg\". Use the Input file and Output file buttons to insert paths FFmpeg can access. Tap an example to load it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    examples.forEach { (title, cmd) ->
                        Column(Modifier.fillMaxWidth().clickable { vm.probe = false; vm.command = cmd; help = false }) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("For ffprobe, try: -show_format -show_streams -of json INPUT", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { help = false }) { Text("Close") } },
        )
    }
}
