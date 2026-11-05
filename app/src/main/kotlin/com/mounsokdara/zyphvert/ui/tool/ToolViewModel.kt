package com.mounsokdara.zyphvert.ui.tool

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mounsokdara.zyphvert.data.ConversionJob
import com.mounsokdara.zyphvert.data.JobHistory
import com.mounsokdara.zyphvert.data.JobStatus
import com.mounsokdara.zyphvert.engine.EngineEvent
import com.mounsokdara.zyphvert.engine.EngineFactory
import com.mounsokdara.zyphvert.engine.MediaEngine
import com.mounsokdara.zyphvert.ui.home.Formats
import com.mounsokdara.zyphvert.ui.home.MediaKind
import com.mounsokdara.zyphvert.ui.home.OutputChoice
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
    var extraUri by mutableStateOf<Uri?>(null)
        private set
    var extraPath by mutableStateOf<String?>(null)
        private set
    var extraLabel by mutableStateOf<String?>(null)
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
    var gain by mutableStateOf("1.0")
    var mute by mutableStateOf(false)

    private var job: Job? = null
    private var currentJobId: String? = null

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

    fun setExtra(uri: Uri?, label: String?) {
        extraUri = uri
        extraPath = null
        extraLabel = label
    }

    /** Picking a format renames a filesystem output so the extension matches. */
    fun selectFormat(toolId: String, formatId: String) {
        outputFormat = formatId
        val choice = Formats.resolve(toolId, formatId) ?: return
        val path = outputPath ?: return
        val file = File(path)
        val parent = file.parentFile ?: return
        val stem = file.nameWithoutExtension
            .replace(Regex("_?%0\\d+d$"), "")
            .ifBlank { "output" }
        val name = if (choice.kind == MediaKind.Sequence) "${stem}_%04d.${choice.ext}" else "$stem.${choice.ext}"
        val next = File(parent, name)
        if (next.absolutePath != file.absolutePath) setOutputPath(next.absolutePath)
    }

    /** Place a sensible output next to the input file (File Manager flow). */
    fun suggestOutputBesideInput(toolId: String) {
        val inPath = inputPath ?: return
        val parent = File(inPath).parentFile ?: return
        val base = File(inPath).nameWithoutExtension
        val choice = Formats.resolve(toolId, outputFormat.ifBlank { Formats.defaultId(toolId) })
        val ext = when {
            choice != null -> choice.ext
            toolId == "ringtone" -> "mp3"
            toolId == "sticker" -> "jpg"
            toolId == "subs" -> "srt"
            else -> "mp4"
        }
        val leaf = if (choice?.kind == MediaKind.Sequence) "${base}_zyphvert_%04d.$ext" else "${base}_zyphvert.$ext"
        var candidate = File(parent, leaf)
        var n = 1
        while (candidate.exists()) {
            val numbered = if (choice?.kind == MediaKind.Sequence) {
                "${base}_zyphvert_${n}_%04d.$ext"
            } else {
                "${base}_zyphvert_$n.$ext"
            }
            candidate = File(parent, numbered)
            n++
        }
        if (outputFormat.isBlank()) outputFormat = choice?.id ?: Formats.defaultId(toolId)
        setOutputPath(candidate.absolutePath)
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

        val extraArg = when {
            tool.id != "merge" -> null
            extraPath != null -> quote(extraPath!!)
            extraUri != null -> engine.safRead(context, extraUri!!)
            else -> null
        }
        if (tool.id == "merge" && extraArg == null) {
            add(LogLine("Pick a second file to merge", LogKind.Error))
            return
        }

        val cmd = buildCommand(tool, inArg, outArg, extraArg)
        if (cmd == null) {
            add(LogLine("This tool needs more options or an output file", LogKind.Error))
            return
        }

        val historyJob = ConversionJob(
            toolTitle = tool.title,
            inputLabel = inputLabel ?: "input",
            outputLabel = outputLabel ?: "output",
            command = cmd,
            status = JobStatus.Running,
        )
        currentJobId = historyJob.id
        JobHistory.add(historyJob)

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
                        is EngineEvent.Done -> {
                            val msg = when {
                                ev.cancelled -> "Cancelled"
                                ev.success -> "Finished successfully"
                                else -> "Failed" + (ev.returnCode?.let { " (exit $it)" } ?: "")
                            }
                            add(LogLine(msg, if (ev.success) LogKind.Info else LogKind.Error))
                            currentJobId?.let { id ->
                                JobHistory.update(
                                    id,
                                    when {
                                        ev.cancelled -> JobStatus.Cancelled
                                        ev.success -> JobStatus.Success
                                        else -> JobStatus.Failed
                                    },
                                    msg,
                                )
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                add(LogLine(t.message ?: "Engine error", LogKind.Error))
                currentJobId?.let { JobHistory.update(it, JobStatus.Failed, t.message) }
            } finally {
                running = false
                currentJobId = null
            }
        }
    }

    private fun buildCommand(tool: Tool, input: String, output: String?, extra: String?): String? {
        val out = output ?: return null
        val ss = startTime.trim().takeIf { it.isNotEmpty() }?.let { "-ss $it " } ?: ""
        val to = endTime.trim().takeIf { it.isNotEmpty() }?.let { "-to $it " } ?: ""
        val meta = if (stripMetadata) "-map_metadata -1 " else ""
        val choice = Formats.resolve(tool.id, outputFormat.ifBlank { Formats.defaultId(tool.id) })

        return when (tool.id) {
            "video" -> choice?.let { videoConvert(it, input, out, ss, to, meta) }
            "audio" -> choice?.let { audioConvert(it, input, out, ss, to, meta) }
            "image" -> choice?.let { imageConvert(it, input, out, meta) }
            "compress" -> choice?.let { compress(it, input, out, meta) }
            "merge" -> {
                val second = extra ?: return null
                val kind = choice?.kind ?: MediaKind.Video
                merge(kind, input, second, out, meta)
            }
            "volume" -> volume(input, out, meta)
            "subs" -> "-i $input -map 0:s:0 -c:s srt $out".trim()
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
            "meta" -> {
                if (stripMetadata) "-i $input -map_metadata -1 -c copy $out".trim()
                else "-i $input -c copy $out".trim()
            }
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

    private fun videoConvert(choice: OutputChoice, input: String, out: String, ss: String, to: String, meta: String): String {
        val ab = audioBitrate.ifBlank { "192k" }
        return when (choice.kind) {
            MediaKind.Audio -> "${ss}${to}-i $input -vn ${audioEncode(choice.ext, ab)} $meta$out".trim()
            MediaKind.Sequence -> {
                val f = fps.takeIf { it != "original" && it.isNotBlank() } ?: "1"
                "${ss}${to}-i $input -vf fps=$f $meta$out".trim()
            }
            MediaKind.Image -> gifLike(choice.ext, input, out, ss, to, meta)
            MediaKind.Video -> {
                if (codec == "copy") return "${ss}${to}-i $input -c copy $meta$out".trim()
                val filters = buildList {
                    scaleFilter()?.let { add(it) }
                    if (fps != "original" && fps.isNotBlank()) add("fps=$fps")
                }
                val vf = if (filters.isNotEmpty()) "-vf ${filters.joinToString(",")} " else ""
                val (vc, ac, extra) = videoCodecs(choice.ext)
                "${ss}${to}-i $input -c:v $vc ${rateArgs(vc)} -c:a $ac $extra${vf}$meta$out".trim()
            }
        }
    }

    private fun audioConvert(choice: OutputChoice, input: String, out: String, ss: String, to: String, meta: String): String {
        if (choice.kind == MediaKind.Video) {
            return "-f lavfi -i color=c=0x111318:s=1280x720 -i $input -shortest -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a ${audioBitrate.ifBlank { "192k" }} $meta$out".trim()
        }
        return "${ss}${to}-i $input -vn ${audioEncode(choice.ext, audioBitrate.ifBlank { "192k" })} $meta$out".trim()
    }

    private fun imageConvert(choice: OutputChoice, input: String, out: String, meta: String): String {
        val vf = scaleFilter()?.let { "-vf $it " } ?: ""
        return when (choice.kind) {
            MediaKind.Video -> {
                val dur = endTime.trim().ifBlank { "5" }
                "-loop 1 -i $input -c:v ${videoCodec()} -t $dur ${vf}-pix_fmt yuv420p $meta$out".trim()
            }
            else -> "-i $input ${vf}-q:v 2 $meta$out".trim()
        }
    }

    private fun compress(choice: OutputChoice, input: String, out: String, meta: String): String {
        val ab = when (quality) {
            "low" -> "96k"
            "high" -> "160k"
            else -> "128k"
        }
        return when (choice.kind) {
            MediaKind.Audio -> "-i $input -vn ${audioEncode(choice.ext, ab)} $meta$out".trim()
            MediaKind.Image -> {
                val (width, q) = when (quality) {
                    "low" -> "1280" to "8"
                    "high" -> "2048" to "3"
                    else -> "1600" to "5"
                }
                "-i $input -vf scale=$width:-1 -q:v $q $meta$out".trim()
            }
            else -> {
                val (crfValue, height) = when (quality) {
                    "low" -> "34" to "480"
                    "high" -> "23" to null
                    else -> "28" to "720"
                }
                val vf = height?.let { "-vf scale=-2:$it " } ?: ""
                "-i $input ${vf}-c:v libx264 -crf $crfValue -c:a aac -b:a $ab $meta$out".trim()
            }
        }
    }

    private fun merge(kind: MediaKind, first: String, second: String, out: String, meta: String): String {
        return if (kind == MediaKind.Audio) {
            "-i $first -i $second -filter_complex \"[0:a:0][1:a:0]concat=n=2:v=0:a=1[a]\" -map \"[a]\" ${audioEncode("mp3", audioBitrate.ifBlank { "192k" })} $meta$out".trim()
        } else {
            "-i $first -i $second -filter_complex \"[0:v:0][0:a:0][1:v:0][1:a:0]concat=n=2:v=1:a=1[v][a]\" -map \"[v]\" -map \"[a]\" -c:v libx264 -crf ${crf()} -c:a aac $meta$out".trim()
        }
    }

    private fun volume(input: String, out: String, meta: String): String {
        val vol = if (mute) "0" else (gain.toDoubleOrNull()?.coerceIn(0.0, 4.0) ?: 1.0).toString()
        return "-i $input -af volume=$vol -c:v copy $meta$out".trim()
    }

    private fun gifLike(ext: String, input: String, out: String, ss: String, to: String, meta: String): String {
        val f = gifFps.ifBlank { "12" }
        val w = gifWidth.ifBlank { "480" }
        return if (ext == "webp") {
            "${ss}${to}-i $input -vf fps=$f,scale=$w:-1:flags=lanczos -c:v libwebp -loop 0 $meta$out".trim()
        } else {
            "${ss}${to}-i $input -vf fps=$f,scale=$w:-1:flags=lanczos $meta$out".trim()
        }
    }

    private fun audioEncode(ext: String, bitrate: String): String = when (ext) {
        "mp3" -> "-c:a libmp3lame -b:a $bitrate"
        "m4a", "aac" -> "-c:a aac -b:a $bitrate"
        "wav" -> "-c:a pcm_s16le"
        "flac" -> "-c:a flac"
        "ogg" -> "-c:a libvorbis -q:a 5"
        "opus" -> "-c:a libopus -b:a $bitrate"
        else -> "-c:a aac -b:a $bitrate"
    }

    /** video codec, audio codec, extra flags (with trailing space if non-empty). */
    private fun videoCodecs(ext: String): Triple<String, String, String> = when (ext) {
        "webm" -> Triple("libvpx-vp9", "libopus", "")
        "avi" -> Triple("mpeg4", "libmp3lame", "")
        else -> {
            val vc = videoCodec()
            val tag = if (vc == "libx265" && ext != "mkv") "-tag:v hvc1 " else ""
            Triple(vc, "aac", tag)
        }
    }

    private fun rateArgs(vcodec: String): String = when (vcodec) {
        "libvpx-vp9" -> "-crf ${crf()} -b:v 0"
        "mpeg4" -> "-q:v 5"
        else -> "-crf ${crf()}"
    }

    private fun scaleFilter(): String? = when (scale) {
        "1080" -> "scale=-2:1080"
        "720" -> "scale=-2:720"
        "480" -> "scale=-2:480"
        "360" -> "scale=-2:360"
        else -> null
    }

    private fun videoCodec(): String = when (codec) {
        "h264" -> "libx264"
        "h265" -> "libx265"
        "vp9" -> "libvpx-vp9"
        "copy" -> "copy"
        else -> "libx264"
    }

    private fun crf(): String = when (quality) {
        "low" -> "28"
        "medium" -> "23"
        "high" -> "18"
        else -> "23"
    }

    private fun add(line: LogLine) {
        lines.add(line)
        if (lines.size > 1500) lines.removeRange(0, lines.size - 1500)
    }
}
