package com.mounsokdara.zyphvert.ui.tool

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mounsokdara.zyphvert.engine.EngineEvent
import com.mounsokdara.zyphvert.engine.EngineFactory
import com.mounsokdara.zyphvert.engine.MediaEngine
import com.mounsokdara.zyphvert.ui.home.Tool
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class LogKind { Command, Output, Error, Info }
data class LogLine(val text: String, val kind: LogKind)

@HiltViewModel
class ToolViewModel @Inject constructor() : ViewModel() {
    val engine: MediaEngine = EngineFactory.create()

    var inputUri by mutableStateOf<Uri?>(null)
        private set
    /** Absolute filesystem path when opened from File Manager (preferred over SAF). */
    var inputPath by mutableStateOf<String?>(null)
        private set
    var inputLabel by mutableStateOf<String?>(null)
        private set
    var outputUri by mutableStateOf<Uri?>(null)
        private set
    var outputPath by mutableStateOf<String?>(null)
        private set
    var outputLabel by mutableStateOf<String?>(null)
        private set

    var running by mutableStateOf(false)
        private set
    val lines = mutableStateListOf<LogLine>()

    var quality by mutableStateOf("high")
    var scale by mutableStateOf("original")
    var fps by mutableStateOf("original")
    var codec by mutableStateOf("auto")
    var startTime by mutableStateOf("")
    var endTime by mutableStateOf("")
    var speed by mutableStateOf("1.0")
    var reverse by mutableStateOf(false)
    var audioBitrate by mutableStateOf("192k")
    var gifFps by mutableStateOf("12")
    var gifWidth by mutableStateOf("480")
    var outputFormat by mutableStateOf("")
    var stripMetadata by mutableStateOf(true)
    var rotate by mutableStateOf("0")
    var flipH by mutableStateOf(false)
    var flipV by mutableStateOf(false)

    private var job: Job? = null

    fun setInput(uri: Uri?, label: String?) {
        inputUri = uri
        inputPath = null
        inputLabel = label
    }

    fun setInputPath(path: String) {
        val f = File(path)
        inputPath = path
        inputUri = Uri.fromFile(f)
        inputLabel = f.name
    }

    fun setOutput(uri: Uri?, label: String?) {
        outputUri = uri
        outputPath = null
        outputLabel = label
    }

    fun setOutputPath(path: String) {
        val f = File(path)
        outputPath = path
        outputUri = Uri.fromFile(f)
        outputLabel = f.name
    }

    fun clearLog() = lines.clear()

    fun stop() = engine.cancel()

    private fun quote(path: String): String =
        if (path.any { it.isWhitespace() || it == '"' }) "\"${path.replace("\"", "\\\"")}\"" else path

    fun run(tool: Tool, context: Context) {
        if (running) return

        val inArg = when {
            inputPath != null -> quote(inputPath!!)
            inputUri != null -> engine.safRead(context, inputUri!!)
            else -> null
        } ?: run {
            add(LogLine("Cannot open input (needs FFmpeg engine + file permission)", LogKind.Error))
            return
        }

        val outArg = when {
            outputPath != null -> quote(outputPath!!)
            outputUri != null -> engine.safWrite(context, outputUri!!)
            else -> null
        }

        val cmd = buildCommand(tool, inArg, outArg)
        if (cmd == null) {
            add(LogLine("This tool needs more options or an output file", LogKind.Error))
            return
        }

        running = true
        add(LogLine("$ ffmpeg $cmd", LogKind.Command))
        job = viewModelScope.launch {
            try {
                engine.run(cmd, probe = false).collect { ev ->
                    when (ev) {
                        is EngineEvent.Log -> ev.text.split('\n').filter { it.isNotBlank() }.forEach {
                            val kind = if (it.contains(Regex("(?i)error|failed|invalid|unable|no such"))) LogKind.Error else LogKind.Output
                            add(LogLine(it, kind))
                        }
                        is EngineEvent.Done -> add(
                            LogLine(
                                when {
                                    ev.cancelled -> "Cancelled"
                                    ev.success -> "Finished successfully"
                                    else -> "Failed" + (ev.returnCode?.let { " (exit $it)" } ?: "")
                                },
                                if (ev.success) LogKind.Info else LogKind.Error,
                            ),
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                add(LogLine(t.message ?: "Engine error", LogKind.Error))
            } finally {
                running = false
            }
        }
    }

    private fun buildCommand(tool: Tool, input: String, output: String?): String? {
        val out = output ?: return null
        val ss = startTime.trim().takeIf { it.isNotEmpty() }?.let { "-ss $it " } ?: ""
        val to = endTime.trim().takeIf { it.isNotEmpty() }?.let { "-to $it " } ?: ""
        val meta = if (stripMetadata) "-map_metadata -1 " else ""

        fun scaleFilter(): String? = when (scale) {
            "1080" -> "scale=-2:1080"
            "720" -> "scale=-2:720"
            "480" -> "scale=-2:480"
            "360" -> "scale=-2:360"
            else -> null
        }

        fun videoCodec(): String = when (codec) {
            "h264" -> "libx264"
            "h265" -> "libx265"
            "vp9" -> "libvpx-vp9"
            "copy" -> "copy"
            else -> "libx264"
        }

        fun crf(): String = when (quality) {
            "low" -> "28"
            "medium" -> "23"
            "high" -> "18"
            else -> "23"
        }

        return when (tool.id) {
            "vid2aud" -> {
                val ab = audioBitrate.ifBlank { "192k" }
                if (out.contains(".m4a", true) || outputFormat == "aac") {
                    "${ss}${to}-i $input -vn -c:a aac -b:a $ab $meta$out".trim()
                } else {
                    "${ss}${to}-i $input -vn -c:a libmp3lame -b:a $ab $meta$out".trim()
                }
            }
            "vid2gif" -> {
                val f = gifFps.ifBlank { "12" }
                val w = gifWidth.ifBlank { "480" }
                if (out.endsWith(".webp", ignoreCase = true)) {
                    "${ss}${to}-i $input -vf fps=$f,scale=$w:-1:flags=lanczos -c:v libwebp -loop 0 $meta$out".trim()
                } else {
                    "${ss}${to}-i $input -vf fps=$f,scale=$w:-1:flags=lanczos $meta$out".trim()
                }
            }
            "vid2img" -> {
                val f = fps.takeIf { it != "original" && it.isNotBlank() } ?: "1"
                "${ss}${to}-i $input -vf fps=$f $meta$out".trim()
            }
            "heic" -> "-i $input -q:v 2 $meta$out".trim()
            "img2vid" -> {
                val dur = endTime.trim().ifBlank { "5" }
                val vf = scaleFilter()?.let { "-vf $it " } ?: ""
                "-loop 1 -i $input -c:v ${videoCodec()} -t $dur ${vf}-pix_fmt yuv420p $meta$out".trim()
            }
            "aud2vid" ->
                "-f lavfi -i color=c=black:s=1280x720:d=1 -i $input -shortest -c:v libx264 -c:a aac -pix_fmt yuv420p $meta$out".trim()
            "live" ->
                "-i $input -c:v ${videoCodec()} -crf ${crf()} -c:a aac $meta$out".trim()
            "trim" -> {
                if (codec == "copy") "${ss}${to}-i $input -c copy $meta$out".trim()
                else "${ss}${to}-i $input -c:v ${videoCodec()} -crf ${crf()} -c:a aac $meta$out".trim()
            }
            "speed" -> {
                val factor = speed.toDoubleOrNull()?.coerceIn(0.25, 4.0) ?: 1.0
                val vset = if (reverse) "reverse,setpts=PTS/$factor" else "setpts=PTS/$factor"
                val at = factor.coerceIn(0.5, 2.0)
                val aset = if (reverse) "areverse,atempo=$at" else "atempo=$at"
                "-i $input -filter_complex \"[0:v]$vset[v];[0:a]$aset[a]\" -map \"[v]\" -map \"[a]\" -c:v ${videoCodec()} -crf ${crf()} $meta$out".trim()
            }
            "crop" -> {
                val filters = buildList {
                    when (rotate) {
                        "90" -> add("transpose=1")
                        "180" -> add("transpose=1,transpose=1")
                        "270" -> add("transpose=2")
                    }
                    if (flipH) add("hflip")
                    if (flipV) add("vflip")
                    scaleFilter()?.let { add(it) }
                }.joinToString(",")
                val vf = if (filters.isNotEmpty()) "-vf $filters " else ""
                "-i $input ${vf}-c:v ${videoCodec()} -crf ${crf()} -c:a copy $meta$out".trim()
            }
            "filters" -> {
                val vf = scaleFilter() ?: "null"
                "-i $input -vf $vf -c:v ${videoCodec()} -crf ${crf()} -c:a copy $meta$out".trim()
            }
            "scrub" -> "-i $input -map_metadata -1 -c copy $out".trim()
            "meta" -> if (stripMetadata) "-i $input -map_metadata -1 -c copy $out".trim()
            else "-i $input -c copy $out".trim()
            "ringtone" -> {
                val ab = audioBitrate.ifBlank { "128k" }
                "${ss}${to}-i $input -vn -c:a libmp3lame -b:a $ab $meta$out".trim()
            }
            "sticker" -> {
                val w = gifWidth.ifBlank { "512" }
                "-i $input -vf scale=$w:-1:flags=lanczos $meta$out".trim()
            }
            "wm" -> "-i $input -c:v ${videoCodec()} -crf ${crf()} -c:a copy $meta$out".trim()
            else -> null
        }
    }

    private fun add(line: LogLine) {
        lines.add(line)
        if (lines.size > 1500) lines.removeRange(0, lines.size - 1500)
    }
}
