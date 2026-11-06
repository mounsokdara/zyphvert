package com.mounsokdara.zyphvert.ui.terminal

import android.os.Environment
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

/** [prompt] is set for typed commands, so the history shows exactly what the live prompt looked like. */
data class TermLine(val text: String, val kind: LineKind, val prompt: String? = null)

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
    var job: Job? = null

    val prompt: String get() = "PS ${cwd.path}> "
}

private const val MAX_LINES = 3000
private val KNOWN = setOf("ffmpeg", "ffprobe", "cd", "ls", "dir", "pwd", "clear", "cls", "help", "echo", "history", "version", "exit", "save")
private val errorHint = Regex("(?i)\\b(error|invalid|no such file|failed|unable|unknown encoder|permission denied)\\b")

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

    /** Add [text] at the end of the current command (used by the file picker and "open in terminal"). */
    fun insert(text: String) {
        val s = current
        val t = s.input.text
        val joined = if (t.isBlank()) text else t.trimEnd() + " " + text
        s.input = TextFieldValue(joined, TextRange(joined.length))
    }

    fun historyStep(delta: Int) {
        val s = current
        if (s.history.isEmpty()) return
        s.historyPos = if (s.historyPos == -1) (if (delta < 0) s.history.lastIndex else -1) else (s.historyPos + delta)
        if (s.historyPos !in s.history.indices) { s.historyPos = -1; s.input = TextFieldValue(""); return }
        val h = s.history[s.historyPos]
        s.input = TextFieldValue(h, TextRange(h.length))
    }

    /** Tab completion for the last word, against the current folder. */
    fun complete() {
        val s = current
        val text = s.input.text
        val word = text.substringAfterLast(' ').trimStart('"')
        val base = if (word.startsWith("/")) File(word.substringBeforeLast('/', "/").ifEmpty { "/" }) else File(s.cwd, word.substringBeforeLast('/', ""))
        val leaf = word.substringAfterLast('/')
        val matches = FsFallback.children(base).orEmpty().filter { it.name.startsWith(leaf) && !it.name.startsWith(".").and(leaf.isEmpty()) }.sortedBy { it.name }
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
        val v = prefix + quoted
        s.input = TextFieldValue(v, TextRange(v.length))
    }

    // ---------------------------------------------------------------- run

    fun submit() {
        val s = current
        if (s.running) return
        val raw = s.input.text.trim()
        s.input = TextFieldValue("")
        s.historyPos = -1
        add(s, TermLine(raw, LineKind.Command, s.prompt))
        if (raw.isEmpty()) return
        s.history += raw
        val tokens = tokenize(raw)
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
                val last = s.history.dropLast(1).lastOrNull { tokenize(it).firstOrNull()?.lowercase() in setOf("ffmpeg", "ffprobe") }
                if (last == null) add(s, TermLine("Nothing to save yet. Run an ffmpeg command first.", LineKind.Error))
                else { saveCommand(last); add(s, TermLine("Saved: $last", LineKind.Info)) }
            }
            "cd" -> cd(s, args.firstOrNull())
            "ls", "dir" -> ls(s, args)
            "ffmpeg", "ffprobe" -> runEngine(s, cmd == "ffprobe", args)
            else -> if (cmd.startsWith("-")) runEngine(s, false, tokens)
                    else add(s, TermLine("'${tokens.first()}' is not recognized. Type help to see what works here.", LineKind.Error))
        }
    }

    fun interrupt() {
        val s = current
        if (s.running) { s.engine.cancel(); add(s, TermLine("^C", LineKind.Error)) }
        else { s.input = TextFieldValue(""); add(s, TermLine("^C", LineKind.Error, s.prompt)) }
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
            "Relative file names work: files in the current folder are found automatically.",
            "Tab completes names, the up and down keys walk through your history, ^C stops a running command.",
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
        // Make relative file names work: anything that exists in the current folder becomes an absolute path.
        val resolved = args.map { a ->
            if (!a.startsWith("-") && !a.startsWith("/") && File(s.cwd, a).exists()) File(s.cwd, a).path else a
        }
        val cmd = resolved.joinToString(" ") { if (it.any { c -> c == ' ' || c == '"' }) "\"" + it.replace("\"", "") + "\"" else it }
        s.running = true
        s.job = viewModelScope.launch {
            try {
                s.engine.run(cmd, probe).collect { ev ->
                    when (ev) {
                        is EngineEvent.Log -> ev.text.split('\n').filter { it.isNotBlank() }.forEach {
                            add(s, TermLine(it, if (errorHint.containsMatchIn(it)) LineKind.Error else LineKind.Output))
                        }
                        is EngineEvent.Done -> add(
                            s, TermLine(
                                when {
                                    ev.cancelled -> "Cancelled"
                                    ev.success -> "Done"
                                    else -> "Failed" + (ev.returnCode?.let { " (exit code $it)" } ?: "")
                                },
                                if (ev.success) LineKind.Info else LineKind.Error,
                            )
                        )
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

    private fun add(s: Session, line: TermLine) {
        s.lines.add(line)
        if (s.lines.size > MAX_LINES) s.lines.removeRange(0, s.lines.size - MAX_LINES)
    }

    /** Splits on spaces, keeping "quoted text" together. */
    private fun tokenize(raw: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        for (c in raw) {
            when {
                quote != null -> if (c == quote) quote = null else cur.append(c)
                c == '"' || c == '\'' -> quote = c
                c.isWhitespace() -> if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out.ifEmpty { listOf("") }
    }

    companion object { val knownCommands: Set<String> = KNOWN }
}
