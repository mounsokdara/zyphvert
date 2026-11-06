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
    var query by mutableStateOf("")
    var sort by mutableStateOf(SortMode.NameAsc)
    var viewMode by mutableStateOf(ViewMode.List)
    var showHidden by mutableStateOf(false)
    var selected by mutableStateOf<FileEntry?>(null)

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
