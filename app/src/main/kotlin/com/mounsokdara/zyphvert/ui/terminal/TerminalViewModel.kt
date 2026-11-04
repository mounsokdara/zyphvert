package com.mounsokdara.zyphvert.ui.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mounsokdara.zyphvert.data.SettingsRepository
import com.mounsokdara.zyphvert.engine.EngineEvent
import com.mounsokdara.zyphvert.engine.EngineFactory
import com.mounsokdara.zyphvert.engine.MediaEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LineKind { Command, Output, Error, Info }
data class TerminalLine(val text: String, val kind: LineKind)

private const val MAX_LINES = 2000
private val errorHint = Regex("(?i)\\b(error|invalid|no such file|failed|unable|unknown encoder|permission denied)\\b")

@HiltViewModel
class TerminalViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val engine: MediaEngine = EngineFactory.create()

    var command by mutableStateOf("")
    var probe by mutableStateOf(false)
    var running by mutableStateOf(false)
        private set
    val lines = mutableStateListOf<TerminalLine>()

    val saved: StateFlow<List<String>> =
        repo.savedCommands.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var job: Job? = null

    fun run() {
        val cmd = command.trim()
        if (cmd.isEmpty() || running) return
        running = true
        add(TerminalLine("$ ${if (probe) "ffprobe" else "ffmpeg"} $cmd", LineKind.Command))
        job = viewModelScope.launch {
            try {
                engine.run(cmd, probe).collect { ev ->
                    when (ev) {
                        is EngineEvent.Log -> ev.text.split('\n').filter { it.isNotBlank() }.forEach {
                            add(TerminalLine(it, if (errorHint.containsMatchIn(it)) LineKind.Error else LineKind.Output))
                        }
                        is EngineEvent.Done -> add(
                            TerminalLine(
                                when {
                                    ev.cancelled -> "Cancelled"
                                    ev.success -> "Finished successfully"
                                    else -> "Failed" + (ev.returnCode?.let { " (exit code $it)" } ?: "")
                                },
                                if (ev.success) LineKind.Info else LineKind.Error,
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                add(TerminalLine(t.message ?: "Engine error", LineKind.Error))
            } finally {
                running = false
            }
        }
    }

    fun stop() = engine.cancel()
    fun clear() = lines.clear()

    fun insert(text: String) {
        command = if (command.isBlank()) text else command.trimEnd() + " " + text
    }

    fun saveCurrent() {
        val c = command.trim()
        if (c.isNotEmpty()) viewModelScope.launch { repo.saveCommand(c) }
    }

    fun deleteSaved(cmd: String) { viewModelScope.launch { repo.deleteCommand(cmd) } }

    fun logText(): String = lines.joinToString("\n") { it.text }

    private fun add(line: TerminalLine) {
        lines.add(line)
        if (lines.size > MAX_LINES) lines.removeRange(0, lines.size - MAX_LINES)
    }
}
