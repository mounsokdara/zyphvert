@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.mounsokdara.zyphvert.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import com.mounsokdara.zyphvert.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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

private val Bg = Color(0xFF0A0A10)
private val TabBg = Color(0xFF14141D)
private val KeyBg = Color(0xFF1E1F2E)
private val Fg = Color(0xFFD5D9E8)
private val Dim = Color(0xFF636A88)
private val Yellow = Color(0xFFE0AF68)
private val Cyan = Color(0xFF7DCFFF)
private val Green = Color(0xFF9ECE6A)
private val Red = Color(0xFFF7768E)
private val Blue = Color(0xFF7AA2F7)
private val Magenta = Color(0xFFBB9AF7)

private val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
private const val MIN_TEXT = 8f
private const val MAX_TEXT = 32f

private fun termStyle(size: Float) = TextStyle(
    fontFamily = Mono, fontSize = size.sp, lineHeight = (size * 1.45f).sp, color = Fg,
    fontFeatureSettings = "'liga' 0, 'calt' 0",
)

private fun highlight(text: String): AnnotatedString = buildAnnotatedString {
    var first = true
    for (m in Regex("\"[^\"]*\"?|'[^']*'?|\\s+|[^\\s\"']+").findAll(text)) {
        var t = m.value
        if (t.isBlank()) { append(t); continue }
        if ((t[0] == '\\' || t[0] == '`') && (t.length == 1 || t[1] == '-')) {
            withStyle(SpanStyle(color = Dim)) { append(t[0]) }
            t = t.drop(1)
            if (t.isEmpty()) continue
        }
        val color = when {
            t.startsWith("\"") || t.startsWith("'") -> Green
            first -> if (t.lowercase() in TerminalViewModel.knownCommands || t.startsWith("-")) Yellow else Red
            t.startsWith("-") && t.length > 1 -> Cyan
            t.toDoubleOrNull() != null -> Magenta
            else -> Fg
        }
        first = false
        withStyle(SpanStyle(color = color)) { append(t) }
    }
}

private fun promptText(prompt: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = Blue)) { append(prompt.removeSuffix("> ")) }
    withStyle(SpanStyle(color = Fg)) { append("> ") }
}

private class PromptTransformation(private val prompt: AnnotatedString) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val n = prompt.length
        val shown = buildAnnotatedString { append(prompt); append(highlight(text.text)) }
        return TransformedText(shown, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + n
            override fun transformedToOriginal(offset: Int) = (offset - n).coerceAtLeast(0)
        })
    }
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
    val tabState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val saved by vm.saved.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    var textSize by rememberSaveable { mutableFloatStateOf(13f) }
    val term = remember(textSize) { termStyle(textSize) }
    var folderPicker by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val itemCount = (if (!vm.engineReady) 1 else 0) + s.lines.size + 1
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(s.id, itemCount, s.running, imeOpen, textSize) {
        listState.scrollToItem((itemCount - 1).coerceAtLeast(0), scrollOffset = Int.MAX_VALUE / 2)
    }
    LaunchedEffect(s.id, s.running) {
        if (!s.running) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
    LaunchedEffect(vm.selected, vm.sessions.size) { tabState.animateScrollToItem(vm.selected.coerceAtLeast(0)) }

    fun openKeyboard() {
        if (s.running) return
        scope.launch {
            if (runCatching { focus.requestFocus() }.isFailure) {
                listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0), scrollOffset = Int.MAX_VALUE / 2)
                androidx.compose.runtime.withFrameNanos { }
                runCatching { focus.requestFocus() }
            }
            keyboard?.show()
        }
    }

    fun type(chars: String) {
        val t = s.input.text + chars
        vm.setInput(TextFieldValue(t, TextRange(t.length)))
    }

    Column(
        Modifier.fillMaxSize().background(Bg)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().background(TabBg).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Fg) }
            LazyRow(Modifier.weight(1f), state = tabState, verticalAlignment = Alignment.Bottom) {
                itemsIndexed(vm.sessions, key = { _, it -> it.id }) { i, sess ->
                    val active = i == vm.selected
                    Row(
                        Modifier.padding(end = 2.dp).height(36.dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(if (active) Bg else Color.Transparent).clickable { vm.select(i) }
                            .padding(start = 12.dp, end = if (active && vm.sessions.size > 1) 2.dp else 12.dp),
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
                    DropdownMenuItem(text = { Text("Insert folder path") }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) }, onClick = { menu = false; folderPicker = true })
                    DropdownMenuItem(text = { Text("Pick a file") }, leadingIcon = { Icon(Icons.Rounded.AttachFile, null) }, onClick = { menu = false; picker = true })
                    DropdownMenuItem(text = { Text("Saved commands") }, leadingIcon = { Icon(Icons.Rounded.Bookmarks, null) }, onClick = { menu = false; sheet = "saved" })
                    DropdownMenuItem(text = { Text("Examples") }, leadingIcon = { Icon(Icons.Rounded.Lightbulb, null) }, onClick = { menu = false; sheet = "examples" })
                    DropdownMenuItem(text = { Text("Copy output") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; clipboard.setText(AnnotatedString(vm.outputText())) })
                    DropdownMenuItem(text = { Text("Clear screen") }, leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) }, onClick = { menu = false; vm.clear() })
                    DropdownMenuItem(text = { Text("Text bigger") }, leadingIcon = { Icon(Icons.Rounded.ZoomIn, null) }, enabled = textSize < MAX_TEXT, onClick = { textSize = (textSize + 1f).coerceAtMost(MAX_TEXT) })
                    DropdownMenuItem(text = { Text("Text smaller") }, leadingIcon = { Icon(Icons.Rounded.ZoomOut, null) }, enabled = textSize > MIN_TEXT, onClick = { textSize = (textSize - 1f).coerceAtLeast(MIN_TEXT) })
                }
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            if (ev.changes.size > 1) {
                                val zoom = ev.calculateZoom()
                                if (zoom != 1f) {
                                    textSize = (textSize * zoom).coerceIn(MIN_TEXT, MAX_TEXT)
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
                .pointerInput(s.id, s.running) { detectTapGestures(onTap = { openKeyboard() }) },
        ) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!vm.engineReady) item(key = "warn") {
                        Text("FFmpeg isn't part of this build, so only built-in commands work.", style = term.copy(color = Yellow))
                    }
                    items(s.lines.size) { i ->
                        val l = s.lines.getOrNull(i) ?: return@items
                        when (l.kind) {
                            LineKind.Command -> Text(buildAnnotatedString {
                                append(promptText(l.prompt.orEmpty()))
                                append(highlight(l.text))
                            }, style = term, modifier = Modifier.padding(top = 6.dp))
                            LineKind.Error -> Text(l.text, style = term.copy(color = Red))
                            LineKind.Info -> Text(l.text, style = term.copy(color = Cyan))
                            LineKind.Dir -> Text(l.text, style = term.copy(color = Blue))
                            LineKind.Output -> Text(l.text, style = if (l.progress) term.copy(color = Dim) else term)
                        }
                    }
                    if (!s.running) item(key = "prompt") {
                        val promptStr = remember(s.cwd) { promptText(s.prompt) }
                        val transformation = remember(promptStr) { PromptTransformation(promptStr) }
                        BasicTextField(
                            value = s.input,
                            onValueChange = vm::onInputChanged,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).focusRequester(focus),
                            textStyle = term,
                            cursorBrush = SolidColor(Fg),
                            visualTransformation = transformation,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(onSend = { vm.submit() }),
                        )
                    } else item(key = "running") {
                        Text("running…  press ^C to stop", style = term.copy(color = Dim), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(TabBg).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Key("Tab") { vm.complete() }
                Key("←", repeat = true) { vm.moveCursor(-1) }
                Key("↑", repeat = true) { vm.historyStep(-1) }
                Key("↓", repeat = true) { vm.historyStep(1) }
                Key("→", repeat = true) { vm.moveCursor(1) }
                Key("^C", tint = if (s.running) Red else Fg) { vm.interrupt() }
                Key(icon = Icons.Rounded.ContentPaste, desc = "Paste") {
                    clipboard.getText()?.text?.let { type(vm.cleanPaste(it)) }
                }
            }
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Key(icon = Icons.Rounded.AttachFile, desc = "Insert a file path") { picker = true }
                Key(icon = Icons.Rounded.FolderOpen, desc = "Insert folder path") { folderPicker = true }
                Key("-") { type("-") }
                Key("/") { type("/") }
                Key("\"") { type("\"") }
                Key("'") { type("'") }
                Key("~") { type("~") }
            }
        }
    }

    val launchFolderPicker = com.mounsokdara.zyphvert.ui.files.rememberFilePicker(
        onUseSystem = {},
        onPicked = { picked -> picked.firstOrNull()?.uri?.path?.let { vm.insert(if (' ' in it) "\"$it\"" else it) } },
    )
    if (folderPicker) {
        LaunchedEffect(Unit) {
            folderPicker = false
            launchFolderPicker(
                com.mounsokdara.zyphvert.ui.files.PickRequest(InKind.Any, false, "Insert folder path", folder = true, startPath = s.cwd.path),
            )
        }
    }

    val launchPicker = com.mounsokdara.zyphvert.ui.files.rememberFilePicker(
        onUseSystem = {},
        onPicked = { picked -> picked.firstOrNull()?.path?.let { vm.insert(if (' ' in it) "\"$it\"" else it) } },
    )
    if (picker) {
        LaunchedEffect(Unit) {
            picker = false
            launchPicker(com.mounsokdara.zyphvert.ui.files.PickRequest(InKind.Any, false, "Insert a file path"))
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
private fun RowScope.Key(
    label: String? = null,
    icon: ImageVector? = null,
    desc: String = label.orEmpty(),
    tint: Color = Fg,
    repeat: Boolean = false,
    onClick: () -> Unit,
) {
    val action by rememberUpdatedState(onClick)
    val base = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp)).background(KeyBg)
    val modifier = if (repeat) base.pointerInput(Unit) {
        detectTapGestures(onPress = {
            action()
            coroutineScope {
                val held = launch { delay(380); while (true) { action(); delay(55) } }
                tryAwaitRelease()
                held.cancel()
            }
        })
    } else base.clickable(onClick = onClick)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, desc, Modifier.size(18.dp), tint = tint)
        else Text(label.orEmpty(), style = TextStyle(fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint), maxLines = 1)
    }
}
