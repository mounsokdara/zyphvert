package com.mounsokdara.zyphvert.ui.files

import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mounsokdara.zyphvert.util.FsFallback
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Files waiting to be pasted. [cut] moves them, otherwise they are copied. */
data class Clip(val files: List<File>, val cut: Boolean)

data class FileStats(val bytes: Long, val files: Int, val folders: Int)

enum class SortMode { NameAsc, NameDesc, SizeDesc, DateDesc, Type }
enum class ViewMode { List, Grid }

data class FileEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
) {
    val sizeLabel: String
        get() = if (isDirectory) "" else humanSize(sizeBytes)
    val dateLabel: String
        get() = DATE_FMT.format(Date(lastModified))

    companion object {
        private val DATE_FMT = SimpleDateFormat("M/d/yy h:mm a", Locale.getDefault())
        fun humanSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
            return String.format(Locale.US, "%.2f GB", mb / 1024.0)
        }
    }
}

@HiltViewModel
class FileManagerViewModel @Inject constructor() : ViewModel() {
    var currentPath by mutableStateOf(defaultRoot())
        private set
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** True only for a pull-down refresh, so opening a folder never shows the pull indicator. */
    var refreshing by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var sort by mutableStateOf(SortMode.NameAsc)
    var viewMode by mutableStateOf(ViewMode.List)
    var showHidden by mutableStateOf(false)
    var selected by mutableStateOf<FileEntry?>(null)
    var clip by mutableStateOf<Clip?>(null)
        private set
    /** One-shot text for a Toast; the screen shows it and clears it. */
    var message by mutableStateOf<String?>(null)

    val breadcrumbs: List<Pair<String, String>>
        get() {
            val parts = currentPath.trim('/').split('/').filter { it.isNotEmpty() }
            val list = mutableListOf("Root" to "/")
            var acc = ""
            for (p in parts) {
                acc += "/$p"
                list += p to acc
            }
            return list
        }

    private var loadJob: Job? = null

    init {
        reload()
    }

    fun navigateTo(path: String) {
        currentPath = path
        selected = null
        reload()
    }

    val canGoUp: Boolean get() = currentPath != "/"

    fun goUp() {
        val parent = File(currentPath).parentFile ?: return
        navigateTo(parent.absolutePath)
    }

    fun open(entry: FileEntry) {
        if (entry.isDirectory) navigateTo(entry.file.absolutePath)
        else selected = entry
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) { listDir(currentPath) }
            entries = result.entries
            error = result.error
            loading = false
        }
    }

    // ------------------------------------------------------------ copy, cut, paste, rename, delete

    fun copy(e: FileEntry) { clip = Clip(listOf(e.file), cut = false); message = "Copied ${e.name}" }

    fun cut(e: FileEntry) { clip = Clip(listOf(e.file), cut = true); message = "Cut ${e.name}" }

    fun clearClip() { clip = null }

    /** Pastes the clipboard into [into] (the open folder by default). */
    fun paste(into: File = File(currentPath)) {
        val c = clip ?: return
        viewModelScope.launch {
            val (done, failed) = withContext(Dispatchers.IO) { pasteInto(c, into) }
            message = when {
                failed == 0 -> "Pasted $done item" + if (done == 1) "" else "s"
                done == 0 -> "Couldn't paste. Check All files access."
                else -> "Pasted $done, $failed failed"
            }
            if (c.cut && failed == 0) clip = null
            reload()
        }
    }

    private fun pasteInto(c: Clip, into: File): Pair<Int, Int> {
        var done = 0
        var failed = 0
        val intoPath = runCatching { into.canonicalPath }.getOrDefault(into.path)
        for (src in c.files) {
            val ok = runCatching {
                if (!src.exists()) return@runCatching false
                val srcPath = src.canonicalPath
                // A folder can't go inside itself.
                if (src.isDirectory && (intoPath == srcPath || intoPath.startsWith("$srcPath/"))) return@runCatching false
                // Cutting into the folder it is already in changes nothing.
                if (c.cut && src.parentFile?.canonicalPath == intoPath) return@runCatching true
                val target = uniqueTarget(into, src)
                if (c.cut) {
                    if (!src.renameTo(target)) {
                        copyAny(src, target)
                        if (!src.deleteRecursively()) return@runCatching false
                    }
                } else copyAny(src, target)
                true
            }.getOrDefault(false)
            if (ok) done++ else failed++
        }
        return done to failed
    }

    private fun copyAny(src: File, target: File) {
        if (src.isDirectory) {
            if (!src.copyRecursively(target, overwrite = false)) throw java.io.IOException("copy failed")
        } else src.copyTo(target, overwrite = false)
    }

    /** `name.ext`, or `name_2.ext`, `name_3.ext`... when the name is taken. */
    private fun uniqueTarget(dir: File, src: File): File {
        var f = File(dir, src.name)
        if (!f.exists()) return f
        val base = if (src.isDirectory) src.name else src.nameWithoutExtension
        val ext = if (src.isDirectory || src.extension.isEmpty()) "" else "." + src.extension
        var n = 2
        while (f.exists()) f = File(dir, "${base}_${n++}$ext")
        return f
    }

    fun rename(e: FileEntry, newName: String) {
        val name = newName.trim()
        when {
            name.isEmpty() || name == "." || name == ".." || '/' in name -> { message = "That name isn't allowed"; return }
            name == e.name -> return
        }
        viewModelScope.launch {
            val target = File(e.file.parentFile, name)
            val ok = withContext(Dispatchers.IO) { !target.exists() && runCatching { e.file.renameTo(target) }.getOrDefault(false) }
            message = if (ok) "Renamed to $name" else if (target.exists()) "$name already exists" else "Couldn't rename"
            selected = null
            reload()
        }
    }

    fun delete(e: FileEntry) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { if (e.file.isDirectory) e.file.deleteRecursively() else e.file.delete() }.getOrDefault(false)
            }
            message = if (ok) "Deleted ${e.name}" else "Couldn't delete ${e.name}"
            if (clip?.files?.contains(e.file) == true) clip = null
            selected = null
            reload()
        }
    }

    suspend fun stats(f: File): FileStats = withContext(Dispatchers.IO) {
        if (!f.isDirectory) return@withContext FileStats(f.length(), 1, 0)
        var bytes = 0L
        var files = 0
        var folders = 0
        runCatching {
            f.walkTopDown().drop(1).forEach { if (it.isDirectory) folders++ else { files++; bytes += it.length() } }
        }
        FileStats(bytes, files, folders)
    }

    /** Hex digest of a file: "MD5" or "SHA-256". */
    suspend fun checksum(f: File, algo: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val md = java.security.MessageDigest.getInstance(algo)
            f.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("Couldn't read this file")
    }

    fun pullRefresh() {
        if (refreshing) return
        refreshing = true
        reload()
        viewModelScope.launch {
            // Listing a folder takes a few ms; hold the indicator long enough to finish its animation.
            kotlinx.coroutines.delay(600)
            loadJob?.join()
            refreshing = false
        }
    }

    fun filtered(): List<FileEntry> {
        var list = entries
        if (!showHidden) list = list.filter { !it.name.startsWith('.') }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { it.name.lowercase().contains(q) }
        }
        list = when (sort) {
            SortMode.NameAsc -> list.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            SortMode.NameDesc -> list.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
            SortMode.SizeDesc -> list.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenByDescending { it.sizeBytes })
            SortMode.DateDesc -> list.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenByDescending { it.lastModified })
            SortMode.Type -> list.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.extension }.thenBy { it.name.lowercase() })
        }
        return list
    }

    private data class ListResult(val entries: List<FileEntry>, val error: String?)

    private fun listDir(path: String): ListResult {
        val dir = File(path)
        if (!dir.exists()) return ListResult(emptyList(), "Path does not exist")
        if (!dir.isDirectory) return ListResult(emptyList(), "Not a directory")
        val files = FsFallback.children(dir)
            ?: return ListResult(emptyList(), if (!dir.canRead()) "Permission denied. Grant All files access in Settings." else "Cannot list this folder")
        val mapped = files.map { f ->
            FileEntry(
                file = f,
                name = f.name,
                isDirectory = f.isDirectory,
                sizeBytes = if (f.isFile) f.length() else 0L,
                lastModified = f.lastModified(),
                extension = f.extension.lowercase(),
            )
        }
        return ListResult(mapped, null)
    }

    private fun defaultRoot(): String {
        val storage = Environment.getExternalStorageDirectory()
        return if (storage.exists()) storage.absolutePath else "/"
    }
}
