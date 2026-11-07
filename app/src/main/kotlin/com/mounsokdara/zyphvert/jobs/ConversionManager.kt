package com.mounsokdara.zyphvert.jobs

import android.content.Context
import android.media.MediaScannerConnection
import com.mounsokdara.zyphvert.data.ConversionJob
import com.mounsokdara.zyphvert.data.JobHistory
import com.mounsokdara.zyphvert.data.JobStatus
import com.mounsokdara.zyphvert.engine.EngineEvent
import com.mounsokdara.zyphvert.engine.EngineFactory
import com.mounsokdara.zyphvert.engine.MediaEngine
import com.mounsokdara.zyphvert.service.ConversionForegroundService
import com.mounsokdara.zyphvert.tools.ToolJob
import com.mounsokdara.zyphvert.util.DeveloperLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Runs queued tool jobs one at a time in the background and records progress in [JobHistory]. */
@Singleton
class ConversionManager @Inject constructor(@ApplicationContext private val ctx: Context) {
    val engine: MediaEngine = EngineFactory.create()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val payloads = ConcurrentHashMap<String, ToolJob>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val lock = Any()
    private var worker: kotlinx.coroutines.Job? = null
    @Volatile private var runningId: String? = null

    fun submit(toolTitle: String, jobs: List<ToolJob>) {
        jobs.forEach { j ->
            val rec = ConversionJob(
                toolTitle = toolTitle,
                inputLabel = j.input,
                outputLabel = j.output?.let { File(it).name } ?: "details",
                command = j.command ?: "(native)",
                status = JobStatus.Queued,
                outputPath = j.output,
            )
            payloads[rec.id] = j
            JobHistory.add(rec)
        }
        synchronized(lock) {
            if (worker == null) {
                ConversionForegroundService.start(ctx)
                worker = scope.launch { loop() }
            }
        }
    }

    fun cancel(id: String) {
        val job = JobHistory.jobs.value.firstOrNull { it.id == id } ?: return
        when (job.status) {
            JobStatus.Queued -> {
                cancelled += id
                JobHistory.patch(id) { it.copy(status = JobStatus.Cancelled, message = "Cancelled", finishedAt = System.currentTimeMillis()) }
                payloads.remove(id)
            }
            JobStatus.Running -> { cancelled += id; if (runningId == id) engine.cancel() }
            else -> Unit
        }
    }

    fun cancelAll() {
        JobHistory.jobs.value.filter { it.status == JobStatus.Queued || it.status == JobStatus.Running }.forEach { cancel(it.id) }
    }

    private suspend fun loop() {
        while (true) {
            val next = synchronized(lock) {
                // history is newest-first, so the oldest queued job is the last one
                val n = JobHistory.jobs.value.lastOrNull { it.status == JobStatus.Queued }
                if (n == null) worker = null
                n
            } ?: return
            runItem(next)
        }
    }

    private suspend fun runItem(item: ConversionJob) {
        val job = payloads[item.id] ?: run {
            JobHistory.patch(item.id) { it.copy(status = JobStatus.Cancelled, finishedAt = System.currentTimeMillis()) }
            return
        }
        runningId = item.id
        DeveloperLog.job("start ${item.toolTitle}: ${job.command ?: "(native)"}")
        JobHistory.patch(item.id) { it.copy(status = JobStatus.Running, startedAt = System.currentTimeMillis()) }

        var ok = false
        var wasCancelled = false
        val full = StringBuilder()
        val tail = ArrayDeque<String>()
        var error: String? = null
        try {
            var handled = false
            val native = job.native
            if (native != null) {
                try { native(); ok = true; handled = true }
                catch (e: CancellationException) { throw e }
                catch (e: Throwable) { if (job.command == null) { error = e.message ?: "Couldn't convert this file"; handled = true } }
            }
            if (!handled) {
                val cmd = job.command
                // The first "Duration:" line in the log is the input; with the trim options it gives the output length.
                var inputMs: Long? = null
                var probed = false
                // FFmpeg's log reaches us in small pieces ("Duration: " and "00:00:23.15" separately), so keep the start of it.
                val head = StringBuilder()
                var lastPush = 0L
                val outFile = job.output?.let { File(it) }
                val onProgress: (Long, Long) -> Unit = { timeMs, bytes ->
                    val now = System.currentTimeMillis()
                    if (now - lastPush >= 300) {
                        lastPush = now
                        // Second source for the length, in case the log didn't give it.
                        if (inputMs == null && !probed && cmd != null) { probed = true; inputMs = probeMs(cmd) }
                        val total = effectiveMs(cmd.orEmpty(), inputMs)
                        val frac = total?.let { (timeMs.toFloat() / it).coerceIn(0f, 0.99f) }
                        val written = maxOf(bytes, outFile?.takeIf { it.isFile }?.length() ?: 0L)
                        JobHistory.patch(item.id) { it.copy(progress = frac, outputBytes = written) }
                    }
                }
                if (cmd == null) error = "Nothing to run"
                else if (!engine.isAvailable) error = "The FFmpeg engine isn't part of this build."
                else engine.run(cmd, job.probe, if (job.probe) null else onProgress).collect { ev ->
                    when (ev) {
                        is EngineEvent.Log -> {
                            if (!job.probe && inputMs == null && head.length < 30_000) { head.append(ev.text); inputMs = durationMs(head) }
                            if (job.probe) { if (full.length < 40_000) full.append(ev.text) }
                            ev.text.split('\n').filter { it.isNotBlank() }.forEach {
                                tail.addLast(it); if (tail.size > 30) tail.removeFirst()
                            }
                        }
                        is EngineEvent.Done -> { ok = ev.success; wasCancelled = ev.cancelled }
                    }
                }
                val finish = job.after
                if (ok && !wasCancelled && finish != null) {
                    try { finish() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Throwable) { ok = false; error = e.message ?: "Couldn't finish the file" }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.message ?: "Something went wrong"
        }
        if (cancelled.remove(item.id)) wasCancelled = true

        val out = job.output?.let { File(it) }
        val produced = out == null || (if (out.isDirectory) out.list()?.isNotEmpty() == true else out.length() > 0)
        val status = when {
            wasCancelled -> JobStatus.Cancelled
            ok && produced -> JobStatus.Success
            else -> JobStatus.Failed
        }
        if (status != JobStatus.Success && out != null && out.isFile) out.delete()
        if (status == JobStatus.Success && out != null) scan(out)

        val message = when (status) {
            JobStatus.Success -> if (job.probe) "Done" else "Saved"
            JobStatus.Cancelled -> "Cancelled"
            else -> error ?: explain(tail.joinToString("\n"))
        }
        DeveloperLog.job("end ${item.toolTitle}: $status, $message" + if (status == JobStatus.Failed && tail.isNotEmpty()) "\n" + tail.joinToString("\n") else "")
        JobHistory.patch(item.id) {
            it.copy(
                status = status, message = message, finishedAt = System.currentTimeMillis(),
                details = if (job.probe) full.toString().trim().ifEmpty { null } else tail.joinToString("\n").ifEmpty { null },
            )
        }
        payloads.remove(item.id)
        runningId = null
    }

    private val durationRe = Regex("""Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)""")

    private fun durationMs(log: CharSequence): Long? = durationRe.find(log)?.let {
        val (h, m, s) = it.destructured
        ((h.toLong() * 3600 + m.toLong() * 60 + s.toDouble()) * 1000).toLong()
    }

    /** Length of the first input file, read straight from the file. Null for streams, images and SAF files. */
    private fun probeMs(cmd: String): Long? {
        val m = Regex("""(?:^|\s)-i\s+(?:"([^"]+)"|(\S+))""").find(cmd) ?: return null
        val path = m.groupValues[1].ifEmpty { m.groupValues[2] }
        if (path.startsWith("saf:") || !File(path).isFile) return null
        return runCatching {
            val r = android.media.MediaMetadataRetriever()
            try {
                r.setDataSource(path)
                r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally { r.release() }
        }.getOrNull()?.takeIf { it > 0 }
    }

    private fun seconds(v: String): Double = v.split(':').fold(0.0) { acc, p -> acc * 60 + (p.toDoubleOrNull() ?: 0.0) }

    /**
     * How long the output will be, so progress can be a percentage: the input length,
     * cut down by -ss, -to and -t. Unknown (null) for slideshows built from still images.
     */
    private fun effectiveMs(cmd: String, inputMs: Long?): Long? {
        if (inputMs == null || inputMs <= 0 || " -loop " in cmd) return null
        fun opt(name: String) = Regex("""(?:^|\s)-$name\s+"?([0-9:.]+)"?(?=\s|$)""").find(cmd)?.groupValues?.get(1)?.let(::seconds)
        val start = opt("ss") ?: 0.0
        var len = inputMs / 1000.0 - start
        opt("to")?.let { len = minOf(len, it - start) }
        opt("t")?.let { len = minOf(len, it) }
        return if (len > 0) (len * 1000).toLong() else null
    }

    private fun scan(out: File) {
        val paths = if (out.isDirectory) out.listFiles()?.map { it.path }?.toTypedArray() else arrayOf(out.path)
        if (!paths.isNullOrEmpty()) MediaScannerConnection.scanFile(ctx, paths, null, null)
    }

    /** Plain-words version of the most common FFmpeg failures. */
    private fun explain(log: String): String {
        val l = log.lowercase()
        return when {
            "unknown encoder" in l || "encoder not found" in l -> "This build doesn't include the codec needed for that format. Try another format."
            "no such file" in l || "permission denied" in l -> "Couldn't open the file. Check that Zyphvert has file access."
            "invalid data found" in l || "moov atom not found" in l -> "The file looks damaged or isn't a media file FFmpeg understands."
            "does not contain any stream" in l || "stream map" in l && "matches no streams" in l -> "The file doesn't contain the kind of audio or video this needs."
            "no space left" in l -> "The phone is out of storage space."
            "could not write header" in l || "error initializing the muxer" in l -> "That format can't hold this kind of audio or video."
            "error while opening encoder" in l -> "The encoder couldn't start with these settings. Try a lower resolution or another format."
            log.isBlank() -> "Conversion failed"
            else -> log.lines().last { it.isNotBlank() }.take(160)
        }
    }
}
