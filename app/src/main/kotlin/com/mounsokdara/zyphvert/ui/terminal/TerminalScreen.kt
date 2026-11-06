@file:OptIn(ExperimentalMaterial3Api::class)

package com.mounsokdara.zyphvert.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mounsokdara.zyphvert.tools.InKind
import com.mounsokdara.zyphvert.ui.components.AppSheet
import com.mounsokdara.zyphvert.ui.picker.FilePicker

// Windows Terminal "Campbell" colours.
private val Bg = Color(0xFF0C0C0C)
private val TabBg = Color(0xFF1F1F1F)
private val KeyBg = Color(0xFF2B2B2B)
private val Fg = Color(0xFFCCCCCC)
private val Dim = Color(0xFF767676)
private val Yellow = Color(0xFFF9F1A5)
private val Cyan = Color(0xFF61D6D6)
private val Green = Color(0xFF16C60C)
private val Red = Color(0xFFE74856)
private val Blue = Color(0xFF3A96DD)
private val Magenta = Color(0xFFB4009E)

private val Mono = FontFamily.Monospace
private val TermText = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp, color = Fg)

private fun highlight(text: String): AnnotatedString = buildAnnotatedString {
    var first = true
    for (m in Regex("\"[^\"]*\"?|'[^']*'?|\\s+|[^\\s\"']+").findAll(text)) {
        val t = m.value
        val color = when {
            t.isBlank() -> Fg
            t.startsWith("\"") || t.startsWith("'") -> Green
            first -> if (t.lowercase() in TerminalViewModel.knownCommands || t.startsWith("-")) Yellow else Red
            t.startsWith("-") && t.length > 1 -> Cyan
            t.toDoubleOrNull() != null -> Magenta
            else -> Fg
        }
        if (t.isNotBlank()) first = false
        withStyle(SpanStyle(color = color)) { append(t) }
    }
}

private fun styledPrompt(prompt: String): AnnotatedString = buildAnnotatedString {
    if (prompt.startsWith("PS ") && prompt.endsWith("> ")) {
        withStyle(SpanStyle(color = Fg)) { append("PS ") }
        withStyle(SpanStyle(color = Blue)) { append(prompt.removePrefix("PS ").removeSuffix("> ")) }
        withStyle(SpanStyle(color = Fg)) { append("> ") }
    } else append(prompt)
}

private object HighlightTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(highlight(text.text), OffsetMapping.Identity)
}

private val examples = listOf(
    "Convert video to MP3" to "ffmpeg -i video.mp4 -vn -c:a libmp3lame -q:a 2 audio.mp3",
    "Trim without re-encoding" to "ffmpeg -ss 00:00:10 -to 00:00:20 -i video.mp4 -c copy clip.mp4",
    "Video to GIF" to "ffmpeg -i video.mp4 -vf fps=12,scale=480:-1 out.gif",
    "Hardware H.264 encode" to "ffmpeg -i video.mp4 -c:v h264_mediacodec -b:v 4M -c:a aac out.mp4",
    "Strip all metadata" to "ffmpeg -i in.mp4 -map_metadata -1 -c copy clean.mp4",
    "Inspect a file" to "ffprobe -show_format -show_streams video.mp4",
    "List formats" to "ffmpeg -formats",
    "List encoders" to "ffmpeg -encoders",
)

@Composable
fun TerminalScreen(vm: TerminalViewModel, onBack: () -> Unit) {
    val s = vm.current
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val saved by vm.saved.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }

    // Keep the prompt in view as output arrives.
    LaunchedEffect(s.id, s.lines.size, s.running) {
        val last = (s.lines.size - 1 + if (!s.running) 1 else 0).coerceAtLeast(0)
        listState.scrollToItem(last)
    }
    LaunchedEffect(s.id, s.running) { if (!s.running) runCatching { focus.requestFocus() } }

    fun type(chars: String) {
        val t = s.input.text + chars
        vm.setInput(TextFieldValue(t, TextRange(t.length)))
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // ---- tab bar
        Row(Modifier.fillMaxWidth().background(TabBg).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Fg) }
            LazyRow(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                itemsIndexed(vm.sessions, key = { _, it -> it.id }) { i, sess ->
                    val active = i == vm.selected
                    Row(
                        Modifier.padding(end = 2.dp).height(36.dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(if (active) Bg else Color.Transparent).clickable { vm.select(i) }.padding(start = 12.dp, end = if (active && vm.sessions.size > 1) 2.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Terminal, null, Modifier.size(14.dp), tint = if (active) Fg else Dim)
                        Text(
                            sess.cwd.name.ifEmpty { "/" }, Modifier.padding(start = 8.dp).widthIn(max = 120.dp),
                            style = TextStyle(fontSize = 12.sp, color = if (active) Fg else Dim), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (active && vm.sessions.size > 1) IconButton(onClick = { vm.closeTab(i) }, Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, "Close tab", Modifier.size(14.dp), tint = Fg)
                        }
                    }
                }
            }
            IconButton(onClick = vm::newTab) { Icon(Icons.Rounded.Add, "New tab", tint = Fg) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Fg) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Saved commands") }, leadingIcon = { Icon(Icons.Rounded.Bookmarks, null) }, onClick = { menu = false; sheet = "saved" })
                    DropdownMenuItem(text = { Text("Examples") }, leadingIcon = { Icon(Icons.Rounded.Lightbulb, null) }, onClick = { menu = false; sheet = "examples" })
                    DropdownMenuItem(text = { Text("Copy output") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; clipboard.setText(AnnotatedString(vm.outputText())) })
                    DropdownMenuItem(text = { Text("Clear screen") }, leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) }, onClick = { menu = false; vm.clear() })
                }
            }
        }

        // ---- screen
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { runCatching { focus.requestFocus() } },
        ) {
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
                if (!vm.engineReady) item(key = "warn") {
                    Text("FFmpeg isn't part of this build, so only built-in commands work.", style = TermText.copy(color = Yellow))
                }
                items(s.lines.size) { i ->
                    val l = s.lines[i]
                    when (l.kind) {
                        LineKind.Command -> Text(buildAnnotatedString { append(styledPrompt(l.prompt ?: "")); append(highlight(l.text)) }, style = TermText)
                        LineKind.Error -> Text(l.text, style = TermText.copy(color = Red))
                        LineKind.Info -> Text(l.text, style = TermText.copy(color = Cyan))
                        LineKind.Dir -> Text(l.text, style = TermText.copy(color = Blue))
                        LineKind.Output -> Text(l.text, style = TermText)
                    }
                }
                if (!s.running) item(key = "prompt") {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(styledPrompt(s.prompt), style = TermText)
                        BasicTextField(
                            value = s.input,
                            onValueChange = { v ->
                                if (v.text.contains('\n')) { vm.setInput(v.copy(text = v.text.replace("\n", ""))); vm.submit() } else vm.setInput(v)
                            },
                            modifier = Modifier.weight(1f).focusRequester(focus),
                            textStyle = TermText,
                            cursorBrush = SolidColor(Fg),
                            visualTransformation = HighlightTransformation,
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(onSend = { vm.submit() }),
                        )
                    }
                } else item(key = "running") {
                    Text("running…  press ^C to stop", style = TermText.copy(color = Dim))
                }
            }
        }

        // ---- key bar
        Row(Modifier.fillMaxWidth().background(TabBg).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyButton("Tab") { vm.complete() }
            KeyButton("↑") { vm.historyStep(-1) }
            KeyButton("↓") { vm.historyStep(1) }
            KeyButton("^C", tint = if (s.running) Red else Fg) { vm.interrupt() }
            KeyButton("File") { picker = true }
            KeyButton("-") { type("-") }
            KeyButton("/") { type("/") }
            KeyButton("\"") { type("\"") }
            KeyButton("~") { type("~") }
            KeyButton("Paste") { clipboard.getText()?.text?.let { type(it.replace("\n", " ")) } }
        }
    }

    if (picker) {
        FilePicker(
            kind = InKind.Any, multiple = false, title = "Insert a file path",
            onDismiss = { picker = false }, onUseSystem = { picker = false },
        ) { picked ->
            picker = false
            picked.firstOrNull()?.path?.let { vm.insert(if (' ' in it) "\"$it\"" else it) }
        }
    }

    when (sheet) {
        "saved" -> AppSheet({ sheet = null }) { close ->
            Text("Saved commands", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            if (saved.isEmpty()) Text(
                "Type save after a command to keep it here.", Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll2().navigationBarsPadding()) {
                saved.forEach { cmd ->
                    Row(Modifier.fillMaxWidth().clickable { s.input = TextFieldValue(cmd, TextRange(cmd.length)); close() }.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cmd, Modifier.weight(1f).padding(vertical = 14.dp), fontFamily = Mono, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { vm.deleteSaved(cmd) }) { Icon(Icons.Rounded.Close, "Delete") }
                    }
                }
            }
        }
        "examples" -> AppSheet({ sheet = null }) { close ->
            Text("Examples", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f, fill = false).verticalScroll2().navigationBarsPadding()) {
                examples.forEach { (title, cmd) ->
                    Column(Modifier.fillMaxWidth().clickable { s.input = TextFieldValue(cmd, TextRange(cmd.length)); close() }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(cmd, fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.verticalScroll2(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

@Composable
private fun KeyButton(label: String, tint: Color = Fg, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).widthIn(min = 42.dp).clip(RoundedCornerShape(6.dp)).background(KeyBg).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = tint)) }
}
