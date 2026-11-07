package com.mounsokdara.zyphvert.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug log for the Console page. It only records anything while Developer options and "Log debug" are on.
 * Lines go to a small file so they survive the app being killed, and the last crash is kept next to it.
 */
object DeveloperLog {
    @Volatile var enabled = false
        private set
    @Volatile var logJobs = false
        private set

    private var dir: File? = null
    private const val MAX_BYTES = 256 * 1024
    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    fun init(ctx: Context) {
        dir = ctx.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(ctx.filesDir, "last_crash.txt").writeText("${stamp()}  thread ${thread.name}\n${error.stackTraceToString()}") }
            previous?.uncaughtException(thread, error)
        }
    }

    fun apply(developer: Boolean, debug: Boolean, jobs: Boolean) {
        enabled = developer && debug
        logJobs = enabled && jobs
    }

    @Synchronized
    fun append(message: String) {
        if (!enabled) return
        val file = File(dir ?: return, "debug.log")
        runCatching {
            if (file.length() > MAX_BYTES) file.writeText(file.readText().takeLast(MAX_BYTES / 2))
            file.appendText("${stamp()}  $message\n")
        }
    }

    /** A line about a conversion job (command, result, FFmpeg output). */
    fun job(message: String) {
        if (logJobs) append("[JOB] $message")
    }

    @Synchronized
    fun readLog(): String = runCatching { File(dir ?: return "", "debug.log").takeIf { it.exists() }?.readText() }.getOrNull().orEmpty().trim()

    fun readCrash(): String = runCatching { File(dir ?: return "", "last_crash.txt").takeIf { it.exists() }?.readText() }.getOrNull().orEmpty().trim()

    @Synchronized
    fun clear() {
        val d = dir ?: return
        File(d, "debug.log").delete()
        File(d, "last_crash.txt").delete()
    }
}
