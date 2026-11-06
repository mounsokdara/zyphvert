package com.mounsokdara.zyphvert.ui.terminal

import android.os.Environment
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mounsokdara.zyphvert.data.SettingsRepository
import com.mounsokdara.zyphvert.engine.EngineEvent
import com.mounsokdara.zyphvert.engine.EngineFactory
import com.mounsokdara.zyphvert.engine.MediaEngine
import com.mounsokdara.zyphvert.util.FsFallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class LineKind { Command, Output, Error, Info, Dir }

/**
 * [prompt] is set for typed commands, so the history shows exactly what the live prompt looked like.
 * [progress] marks an FFmpeg "frame= ... time= ..." status line; the next one replaces it instead of piling up.
 */
data class TermLine(
    val text: String,
    val kind: LineKind,
    val prompt: String? = null,
    val progress: Boolean = false,
)

/** One terminal tab: its own history, folder, input and running command. */
class Session(val id: Int, startDir: File) {
    val engine: MediaEngine = EngineFactory.create()
    var title by mutableStateOf("Terminal $id")
    var cwd by mutableStateOf(startDir)
    var input by mutableStateOf(TextFieldValue(""))
    var running by mutableStateOf(false)
    val lines = mutableStateListOf<TermLine>()
    val history = mutableListOf<String>()
    var historyPos = -1
    /** What was typed before the user started walking through history, so the down key can bring it back. */
    var draft = ""
    var job: Job? = null

    val prompt: String get() = "PS ${cwd.path}> "
}

private const val MAX_LINES = 3000
private const val MAX_HISTORY = 200
private val KNOWN = setOf("ffmpeg", "ffprobe", "cd", "ls", "dir", "pwd", "clear", "cls", "help", "echo", "history", "version", "exit", "save")
private val errorHint = Regex("(?i)\\b(error|invalid|no such file|failed|unable|unknown encoder|permission denied)\\b")
private val progressLine = Regex("^\\s*(frame|size|time)=\\s*\\S+.*")
private val splitLogLines = Regex("[\\r\\n]+")

/** `\` or a backtick at the end of a line, then the newline: shell line continuation. */
private val continuation = Regex("[\\\\`][ \\t]*\\r?\\n")
private val anyNewline = Regex("\\s*\\r?\\n\\s*")

/** Result of [tokenize]: the words, plus the quote character that was never closed (if any). */
data class Tokenized(val tokens: List<String>, val unclosedQuote: Char?)

@HiltViewModel
class TerminalViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    private val home: File = Environment.getExternalStorageDirectory()
    private var nextId = 1

    val sessions = mutableStateListOf<Session>()
    var selected by mutableIntStateOf(0)
        private set

    val current: Session get() = sessions[selected.coerceIn(0, sessions.lastIndex)]
    val engineVersion: String get() = current.engine.version()
    val engineReady: Boolean get() = current.engine.isAvailable

    val saved: StateFlow<List<String>> =
        repo.savedCommands.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { newTab() }

    // ---------------------------------------------------------------- tabs

    fun newTab() {
        val s = Session(nextId++, if (sessions.isEmpty()) home else current.cwd)
        s.lines += TermLine("Zyphvert terminal. Type help for commands.", LineKind.Info)
        sessions += s
        selected = sessions.lastIndex
    }

    fun select(i: Int) { selected = i.coerceIn(0, sessions.lastIndex) }

    fun closeTab(i: Int) {
        if (sessions.size == 1) { clear(); return }
        sessions[i].let { it.engine.cancel(); it.job?.cancel() }
        sessions.removeAt(i)
        selected = selected.coerceAtMost(sessions.lastIndex)
    }

    // ---------------------------------------------------------------- input

    fun setInput(v: TextFieldValue) { current.input = v }

    /**
     * Every change from the text field goes through here.
     * - A single Enter submits the command.
     * - A pasted block of several lines is joined into one command (backslash and backtick line
     *   continuations are removed) and is NOT submitted, so you can check it before running.
     */
    fun onInputChanged(v: TextFieldValue) {
        val t = v.text
        if ('\n' !in t && '\r' !in t) { current.input = v; return }
        val body = t.trimEnd('\n', '\r')
        val multiLine = '\n' in body || '\r' in body
        setText(current, joinLines(body))
        if (!multiLine) submit()
    }

    /** Text from the clipboard, made safe to put on one command line. */
    fun cleanPaste(text: String): String = joinLines(text)

    /** Add [text] at the end of the current command (used by the file picker and "open in terminal"). */
    fun insert(text: String) {
        val s = current
        val t = s.input.text
        setText(s, if (t.isBlank()) text else t.trimEnd() + " " + text)
    }

    fun historyStep(delta: Int) {
        val s = current
        if (s.history.isEmpty()) return
        if (s.historyPos == -1) {
            if (delta > 0) return
            s.draft = s.input.text
            s.historyPos = s.history.lastIndex
        } else {
            s.historyPos += delta
        }
        when {
            s.historyPos > s.history.lastIndex -> { s.historyPos = -1; setText(s, s.draft) }
            else -> { s.historyPos = s.historyPos.coerceAtLeast(0); setText(s, s.history[s.historyPos]) }
        }
    }

    /** Tab completion: command names for the first word, file and folder names for the rest. */
    fun complete() {
        val s = current
        val text = s.input.text

        if (text.isNotEmpty() && ' ' !in text) {
            val cmds = KNOWN.filter { it.startsWith(text.lowercase()) }.sorted()
            if (cmds.size == 1) { setText(s, cmds[0] + " "); return }
            if (cmds.size > 1) {
                val common = cmds.reduce { a, b -> a.commonPrefixWith(b) }
                if (common.length > text.length) setText(s, common)
                add(s, TermLine(cmds.joinToString("   "), LineKind.Dir))
                return
            }
        }

        val word = text.substringAfterLast(' ').trimStart('"')
        val base = if (word.startsWith("/")) File(word.substringBeforeLast('/', "/").ifEmpty { "/" }) else File(s.cwd, word.substringBeforeLast('/', ""))
        val leaf = word.substringAfterLast('/')
        val matches = FsFallback.children(base).orEmpty()
            .filter { it.name.startsWith(leaf) && (leaf.isNotEmpty() || !it.name.startsWith(".")) }
            .sortedBy { it.name }
        when {
            matches.isEmpty() -> return
            matches.size == 1 -> replaceLastWord(word, matches[0], matches[0].isDirectory)
            else -> {
                val common = matches.map { it.name }.reduce { a, b -> a.commonPrefixWith(b) }
                if (common.length > leaf.length) replaceLastWord(word, File(matches[0].parentFile, common), false)
                add(s, TermLine(matches.joinToString("   ") { it.name + if (it.isDirectory) "/" else "" }, LineKind.Dir))
            }
        }
    }

    private fun replaceLastWord(word: String, f: File, dir: Boolean) {
        val s = current
        val text = s.input.text
        val prefix = text.substring(0, text.length - word.length).trimEnd('"')
        val path = (if (word.startsWith("/")) f.path else f.relativeToOrSelf(s.cwd).path) + if (dir) "/" else ""
        val quoted = if (path.contains(' ')) "\"$path\"" else path
        setText(s, prefix + quoted)
    }

    // ---------------------------------------------------------------- run

    fun submit() {
        val s = current
        if (s.running) return
        val raw = joinLines(s.input.text)
        setText(s, "")
        s.historyPos = -1
        s.draft = ""
        add(s, TermLine(raw, LineKind.Command, s.prompt))
        if (raw.isEmpty()) return
        if (s.history.lastOrNull() != raw) s.history += raw
        if (s.history.size > MAX_HISTORY) s.history.removeAt(0)

        val parsed = tokenize(raw)
        parsed.unclosedQuote?.let {
            add(s, TermLine("Unclosed $it quote. Close it, or put the whole name in double quotes.", LineKind.Error))
            setText(s, raw)
            return
        }
        val tokens = parsed.tokens
        val cmd = tokens.first().lowercase()
        val args = tokens.drop(1)
        when (cmd) {
            "clear", "cls" -> s.lines.clear()
            "help" -> help(s)
            "pwd" -> add(s, TermLine(s.cwd.path, LineKind.Output))
            "echo" -> add(s, TermLine(args.joinToString(" "), LineKind.Output))
            "version" -> add(s, TermLine(s.engine.version(), LineKind.Output))
            "history" -> s.history.forEachIndexed { i, h -> add(s, TermLine("${i + 1}  $h", LineKind.Output)) }
            "exit" -> closeTab(sessions.indexOf(s))
            "save" -> {
                val last = s.history.lastOrNull { tokenize(it).tokens.firstOrNull()?.lowercase() in setOf("ffmpeg", "ffprobe") }
                if (last == null) add(s, TermLine("Nothing to save yet. Run an ffmpeg command first.", LineKind.Error))
                else { saveCommand(last); add(s, TermLine("Saved: $last", LineKind.Info)) }
            }
            "cd" -> cd(s, args.firstOrNull())
            "ls", "dir" -> ls(s, args)
            "ffmpeg", "ffprobe" -> runEngine(s, cmd == "ffprobe", args)
            else -> if (cmd.startsWith("-")) runEngine(s, false, tokens) else unknown(s, tokens)
        }
    }

    /**
     * Not a command we know. If ffmpeg/ffprobe appears later in the line (a stray word or two in front
     * of a pasted command), put the cleaned-up command in the prompt so one Enter runs it.
     */
    private fun unknown(s: Session, tokens: List<String>) {
        add(s, TermLine("'${tokens.first()}' is not recognized. Type help to see what works here.", LineKind.Error))
        val idx = tokens.indexOfFirst { it.lowercase() == "ffmpeg" || it.lowercase() == "ffprobe" }
        if (idx > 0) {
            add(s, TermLine("Found ${tokens[idx]} further along. The command is in the prompt without the words before it; press Enter to run it.", LineKind.Info))
            setText(s, tokens.drop(idx).joinToString(" ") { quoteArg(it) })
        }
    }

    fun interrupt() {
        val s = current
        if (s.running) { s.engine.cancel(); add(s, TermLine("^C", LineKind.Error)) }
        else { setText(s, ""); s.historyPos = -1; add(s, TermLine("^C", LineKind.Error, s.prompt)) }
    }

    fun clear() { current.lines.clear() }

    fun outputText(): String = current.lines.joinToString("\n") { (it.prompt ?: "") + it.text }

    fun saveCommand(cmd: String) { viewModelScope.launch { repo.saveCommand(cmd) } }
    fun deleteSaved(cmd: String) { viewModelScope.launch { repo.deleteCommand(cmd) } }

    // ---------------------------------------------------------------- built-ins

    private fun help(s: Session) {
        listOf(
            "ffmpeg <args>      run FFmpeg, e.g. ffmpeg -i in.mp4 out.mp3",
            "ffprobe <args>     inspect a file, e.g. ffprobe -show_streams in.mp4",
            "cd <folder>        change folder (cd .. goes up, cd ~ goes home)",
            "ls [-l] [-a]       list the current folder",
            "pwd                show the current folder",
            "echo <text>        print text",
            "history            commands you typed in this tab",
            "save               save your last ffmpeg command for later",
            "version            FFmpeg library version",
            "clear              clear the screen",
            "exit               close this tab",
            "",
            "Relative file names work, for input and output, in the current folder.",
            "Pasted commands are cleaned up: line breaks and a trailing \\ or ` are removed.",
            "Tab completes commands and names, ↑ ↓ walk through your history, ^C stops a running command.",
        ).forEach { add(s, TermLine(it, LineKind.Output)) }
    }

    private fun cd(s: Session, target: String?) {
        val dir = when {
            target == null || target == "~" -> home
            target == ".." -> s.cwd.parentFile ?: s.cwd
            target.startsWith("/") -> File(target)
            target.startsWith("~/") -> File(home, target.drop(2))
            else -> File(s.cwd, target)
        }.let { runCatching { it.canonicalFile }.getOrDefault(it) }
        if (dir.isDirectory) s.cwd = dir else add(s, TermLine("cd: no such folder: ${target ?: ""}", LineKind.Error))
    }

    private fun ls(s: Session, args: List<String>) {
        val long = args.any { it.startsWith("-") && 'l' in it }
        val all = args.any { it.startsWith("-") && 'a' in it }
        val items = FsFallback.children(s.cwd)
        if (items == null) { add(s, TermLine("ls: cannot open ${s.cwd.path} (permission denied)", LineKind.Error)); return }
        val fmt = SimpleDateFormat("MMM dd HH:mm", Locale.US)
        items.filter { all || !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .forEach { f ->
                val name = f.name + if (f.isDirectory) "/" else ""
                val text = if (long) String.format(Locale.US, "%s %10s  %s  %s", if (f.isDirectory) "d" else "-", if (f.isDirectory) "-" else human(f.length()), fmt.format(Date(f.lastModified())), name) else name
                add(s, TermLine(text, if (f.isDirectory) LineKind.Dir else LineKind.Output))
            }
    }

    private fun human(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", b / 1024.0)
        b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
        else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
    }

    // ---------------------------------------------------------------- ffmpeg

    private fun runEngine(s: Session, probe: Boolean, args: List<String>) {
        if (!s.engine.isAvailable) {
            add(s, TermLine("The FFmpeg library is not part of this build.", LineKind.Error))
            return
        }
        if (args.isEmpty()) {
            add(s, TermLine("Nothing to run. Example: ffmpeg -i in.mp4 out.mp3", LineKind.Error))
            return
        }

        // Relative names resolve against the folder shown in the prompt. Inputs that exist are found
        // automatically; the last argument (the output file) is resolved even though it doesn't exist yet.
        val last = args.lastIndex
        val outputIdx = if (!probe && looksLikeOutputFile(args[last]) && (last == 0 || args[last - 1] != "-i")) last else null
        val resolved = args.mapIndexed { i, raw ->
            val a = expandTilde(raw)
            when {
                a.startsWith("-") || a.startsWith("/") || "://" in a -> a
                i == outputIdx -> File(s.cwd, a).path
                File(s.cwd, a).exists() -> File(s.cwd, a).path
                else -> a
            }
        }

        // FFmpeg would stop and ask "Overwrite? [y/N]", and nobody can answer here.
        if (outputIdx != null && "-y" !in args && "-n" !in args && File(resolved[outputIdx]).exists()) {
            add(s, TermLine("${File(resolved[outputIdx]).name} already exists. Add -y to overwrite it, or choose another name.", LineKind.Error))
            return
        }

        // Single quotes must survive (filter expressions use them to protect commas), so any word with
        // a space or a quote in it goes to the engine wrapped in double quotes.
        val cmd = resolved.joinToString(" ") { a ->
            if (a.any { c -> c == ' ' || c == '"' || c == '\'' }) "\"" + a.replace("\"", "") + "\"" else a
        }
        val started = SystemClock.elapsedRealtime()
        s.running = true
        s.job = viewModelScope.launch {
            try {
                s.engine.run(cmd, probe).collect { ev ->
                    when (ev) {
                        is EngineEvent.Log -> ev.text.split(splitLogLines).filter { it.isNotBlank() }.forEach { line ->
                            when {
                                progressLine.matches(line) -> addProgress(s, line.trim())
                                else -> add(s, TermLine(line, if (errorHint.containsMatchIn(line)) LineKind.Error else LineKind.Output))
                            }
                        }
                        is EngineEvent.Done -> {
                            val secs = (SystemClock.elapsedRealtime() - started) / 1000.0
                            add(
                                s, TermLine(
                                    when {
                                        ev.cancelled -> "Cancelled"
                                        ev.success -> String.format(Locale.US, "Done in %.1fs", secs)
                                        else -> "Failed" + (ev.returnCode?.let { " (exit code $it)" } ?: "")
                                    },
                                    if (ev.success) LineKind.Info else LineKind.Error,
                                )
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                add(s, TermLine(t.message ?: "Engine error", LineKind.Error))
            } finally {
                s.running = false
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun setText(s: Session, t: String) { s.input = TextFieldValue(t, TextRange(t.length)) }

    private fun add(s: Session, line: TermLine) {
        s.lines.add(line)
        if (s.lines.size > MAX_LINES) s.lines.removeRange(0, s.lines.size - MAX_LINES)
    }

    /** Keeps one live status line instead of hundreds of "frame=" lines. */
    private fun addProgress(s: Session, text: String) {
        val line = TermLine(text, LineKind.Output, progress = true)
        if (s.lines.lastOrNull()?.progress == true) s.lines[s.lines.lastIndex] = line else add(s, line)
    }

    private fun expandTilde(a: String): String = when {
        a == "~" -> home.path
        a.startsWith("~/") -> File(home, a.drop(2)).path
        else -> a
    }

    /** "out.mp4" yes; "64k", "-", "pipe:1" and URLs no. */
    private fun looksLikeOutputFile(a: String): Boolean {
        if (a.startsWith("-") || a.startsWith("/") || "://" in a || a.startsWith("pipe:")) return false
        val ext = a.substringAfterLast('.', "")
        return ext.length in 1..5 && ext.all { it.isLetterOrDigit() } && a.substringBeforeLast('.').isNotEmpty()
    }

    private fun quoteArg(a: String): String = when {
        a.isEmpty() -> "\"\""
        a.none { it == ' ' || it == '"' || it == '\'' } -> a
        '"' !in a -> "\"$a\""
        else -> "'$a'"
    }

    companion object {
        val knownCommands: Set<String> = KNOWN

        /**
         * Turns pasted multi-line text into one line: `\` or a backtick before a line break is a
         * continuation (dropped), any other line break becomes a space.
         */
        fun joinLines(text: String): String =
            text.replace(continuation, " ").replace(anyNewline, " ").trim()

        /**
         * Splits on spaces, keeping "quoted text" together. Everything inside quotes is kept exactly as
         * typed. Outside quotes, a `\` or backtick that sits before a space, a dash or the end of the line is
         * a leftover line continuation (from `\-vf` after copying a multi-line command) and is dropped.
         */
        fun tokenize(raw: String): Tokenized {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var started = false
            var quote: Char? = null
            for ((i, c) in raw.withIndex()) {
                when {
                    quote != null -> if (c == quote) quote = null else cur.append(c)
                    c == '"' || c == '\'' -> { quote = c; started = true }
                    (c == '\\' || c == '`') && (i + 1 >= raw.length || raw[i + 1].isWhitespace() || raw[i + 1] == '-') -> Unit
                    c.isWhitespace() -> if (cur.isNotEmpty() || started) { out += cur.toString(); cur.clear(); started = false }
                    else -> cur.append(c)
                }
            }
            if (cur.isNotEmpty() || started) out += cur.toString()
            return Tokenized(out.ifEmpty { listOf("") }, quote)
        }
    }
}
