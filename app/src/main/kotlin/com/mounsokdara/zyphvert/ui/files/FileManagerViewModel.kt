package com.mounsokdara.zyphvert.ui.files

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import com.mounsokdara.zyphvert.tools.AUDIO_EXTS
import com.mounsokdara.zyphvert.tools.IMAGE_EXTS
import com.mounsokdara.zyphvert.tools.InKind
import com.mounsokdara.zyphvert.tools.LIVE_STILL_EXTS
import com.mounsokdara.zyphvert.tools.LIVE_VIDEO_EXTS
import com.mounsokdara.zyphvert.tools.VIDEO_EXTS
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** Folders: walk the folder tree. Media: one flat list of every picture, video and song on the phone. */
enum class BrowseMode { Folders, Media }

data class FileEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    /** Looks like a Live Photo or Motion Photo. */
    val live: Boolean = false,
    /** The motion half of an iPhone Live Photo pair. */
    val companion: File? = null,
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
class FileManagerViewModel @Inject constructor(@ApplicationContext private val appCtx: Context) : ViewModel() {
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
    var showHidden by mutableStateOf(false)
    var selected by mutableStateOf<FileEntry?>(null)
    var browseMode by mutableStateOf(BrowseMode.Folders)
        private set
    /** Picker only: the kind of file the tool wants, and whether to show everything instead. */
    var typeKind by mutableStateOf<InKind?>(null)
        private set
    var showAllTypes by mutableStateOf(true)
    private var mediaItems by mutableStateOf<List<FileEntry>>(emptyList())
    private var sortBeforeMedia: SortMode? = null
    // Picker only: going up from a whole drive stops there unless the user came in through Root.
    private var drives: Set<String> = emptySet()
    private var stopAtDrives = false
    private var fromRoot = false
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
        fromRoot = if (path == "/") true else if (path in drives) false else fromRoot
        if (browseMode == BrowseMode.Media) {
            browseMode = BrowseMode.Folders
            sortBeforeMedia?.let { sort = it }
        }
        currentPath = path
        selected = null
        reload()
    }

    val canGoUp: Boolean get() = currentPath != "/" && !(stopAtDrives && currentPath in drives && !fromRoot)

    /** Called when the browser is opened as a file picker for a tool input. */
    fun configurePick(kind: InKind, driveRoots: Set<String>) {
        typeKind = kind
        drives = driveRoots
        stopAtDrives = true
        showAllTypes = kind.exts == null
        query = ""
        selected = null
        sort = SortMode.DateDesc
        reload()
    }

    fun setMode(mode: BrowseMode) {
        if (mode == browseMode) return
        browseMode = mode
        selected = null
        query = ""
        if (mode == BrowseMode.Media) { sortBeforeMedia = sort; sort = SortMode.DateDesc } else sortBeforeMedia?.let { sort = it }
        reload()
    }

    fun goUp() {
        val parent = File(currentPath).parentFile ?: return
        navigateTo(parent.absolutePath)
    }

    fun open(entry: FileEntry) {
        if (entry.isDirectory) navigateTo(entry.file.absolutePath)
        else selected = entry
    }

    /** Quiet refresh for the auto-refresh loop: nothing changes on screen unless the folder really changed. */
    /** True while a pull-down refresh is showing its spinner. */
    var refreshing by mutableStateOf(false)
        private set

    /** Pull down: re-read the folder quietly (no list animation) and keep the spinner up for a moment. */
    fun pullRefresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            reload(silent = true)
            loadJob?.join()
            delay(400)
            refreshing = false
        }
    }

    fun autoRefresh() { if (loadJob?.isActive != true) reload(silent = true) }

    fun reload(silent: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!silent) { loading = true; error = null }
            if (browseMode == BrowseMode.Media) {
                val fresh = withContext(Dispatchers.IO) { queryMedia() }
                // Same content: keep the old list so the scroll position and item layout stay untouched.
                if (fresh != mediaItems) mediaItems = fresh
            } else {
                val result = withContext(Dispatchers.IO) { listDir(currentPath) }
                if (result.entries != entries) entries = result.entries
                if (result.error != error) error = result.error
            }
            if (!silent) loading = false
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
                val removed = runCatching { if (e.file.isDirectory) e.file.deleteRecursively() else e.file.delete() }.getOrDefault(false)
                // Trust the disk, not the return value.
                removed && !e.file.exists()
            }
            message = if (ok) "Deleted ${e.name}" else "Couldn't delete ${e.name}"
            if (ok) {
                // Take it out of the lists now. Some phones keep listing a deleted name for a moment
                // (with size 0), which looked like the file had only been emptied.
                val path = e.file.path
                entries = entries.filterNot { it.file.path == path || it.file.path.startsWith("$path/") }
                mediaItems = mediaItems.filterNot { it.file.path == path || it.file.path.startsWith("$path/") }
                // Scanning a path that no longer exists removes its row from the media database too.
                if (!e.isDirectory) android.media.MediaScannerConnection.scanFile(appCtx, arrayOf(path), null, null)
            }
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

    fun filtered(): List<FileEntry> {
        var list = if (browseMode == BrowseMode.Media) mediaItems else entries
        val kind = typeKind
        val exts = kind?.exts
        if (exts != null && !showAllTypes) list = list.filter { it.isDirectory || it.extension in exts || (kind?.acceptsLive == true && it.live) }
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
        val videoBases = files.filter { it.isFile && it.extension.lowercase() in LIVE_VIDEO_EXTS }
            .associateBy { it.nameWithoutExtension.lowercase() }
        val mapped = files.filter { it.exists() || it.lastModified() != 0L }.map { f ->
            val ext = f.extension.lowercase()
            val pair = if (f.isFile && ext in LIVE_STILL_EXTS) videoBases[f.nameWithoutExtension.lowercase()] else null
            FileEntry(
                file = f,
                name = f.name,
                isDirectory = f.isDirectory,
                sizeBytes = if (f.isFile) f.length() else 0L,
                lastModified = f.lastModified(),
                extension = ext,
                live = pair != null || (f.isFile && ext in LIVE_STILL_EXTS && motionName(f.name)),
                companion = pair,
            )
        }
        return ListResult(mapped, null)
    }

    /** Every picture, video and song the phone knows about, newest first. */
    private fun queryMedia(): List<FileEntry> {
        val out = ArrayList<FileEntry>()
        val wantPairs = typeKind?.acceptsLive == true
        runCatching {
            val args = arrayOf(FileColumns.MEDIA_TYPE_IMAGE, FileColumns.MEDIA_TYPE_AUDIO, FileColumns.MEDIA_TYPE_VIDEO).map { it.toString() }.toTypedArray()
            appCtx.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(FileColumns.DATA, FileColumns.SIZE, FileColumns.DATE_MODIFIED),
                "${FileColumns.MEDIA_TYPE} IN (?, ?, ?)", args, "${FileColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                val iData = c.getColumnIndexOrThrow(FileColumns.DATA)
                val iSize = c.getColumnIndexOrThrow(FileColumns.SIZE)
                val iDate = c.getColumnIndexOrThrow(FileColumns.DATE_MODIFIED)
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: continue
                    val size = c.getLong(iSize)
                    if (size <= 0) continue
                    out += mediaEntry(File(path), size, c.getLong(iDate) * 1000, wantPairs)
                }
            }
        }
        if (out.isEmpty()) out += walkMedia(wantPairs)
        return out
    }

    /** Fallback when the media database is empty or unreadable: scan the storage, skipping Android/ and hidden folders. */
    private fun walkMedia(wantPairs: Boolean): List<FileEntry> {
        val exts = VIDEO_EXTS + AUDIO_EXTS + IMAGE_EXTS
        val out = ArrayList<FileEntry>()
        runCatching {
            Environment.getExternalStorageDirectory().walkTopDown()
                .onEnter { it.name != "Android" && !it.name.startsWith(".") }
                .forEach { f ->
                    if (out.size < 20_000 && f.isFile && f.extension.lowercase() in exts) out += mediaEntry(f, f.length(), f.lastModified(), wantPairs)
                }
        }
        return out.sortedByDescending { it.lastModified }
    }

    private fun mediaEntry(f: File, size: Long, modified: Long, wantPairs: Boolean): FileEntry {
        val ext = f.extension.lowercase()
        val still = ext in LIVE_STILL_EXTS
        val pair = if (wantPairs && still) {
            LIVE_VIDEO_EXTS.firstNotNullOfOrNull { e -> File(f.parentFile, f.nameWithoutExtension + "." + e).takeIf { it.isFile } }
        } else null
        return FileEntry(f, f.name, false, size, modified, ext, live = pair != null || (still && motionName(f.name)), companion = pair)
    }

    private fun motionName(n: String): Boolean {
        val l = n.lowercase()
        return l.startsWith("mvimg_") || ".mp." in l || "_mp." in l || l.contains("motionphoto")
    }

    private fun defaultRoot(): String {
        val storage = Environment.getExternalStorageDirectory()
        return if (storage.exists()) storage.absolutePath else "/"
    }
}

/** Quick locations for the title menu: internal storage, the usual folders, Root and any SD card. */
internal fun roots(ctx: Context): List<Pair<String, File>> {
    val base = Environment.getExternalStorageDirectory()
    val list = mutableListOf("Internal storage" to base)
    for ((label, dir) in listOf(
        "Camera" to Environment.DIRECTORY_DCIM, "Downloads" to Environment.DIRECTORY_DOWNLOADS,
        "Movies" to Environment.DIRECTORY_MOVIES, "Music" to Environment.DIRECTORY_MUSIC,
        "Pictures" to Environment.DIRECTORY_PICTURES, "Documents" to Environment.DIRECTORY_DOCUMENTS,
    )) {
        val f = Environment.getExternalStoragePublicDirectory(dir)
        if (f.isDirectory) list += label to f
    }
    File(base, "Zyphvert").takeIf { it.isDirectory }?.let { list += "Zyphvert" to it }
    list += "Root (/)" to File("/")
    // SD cards and USB drives show up as extra app-files dirs; walk up to the volume root.
    ctx.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { d ->
        val root = d.path.substringBefore("/Android/")
        if (root != d.path && File(root).isDirectory) list += "SD card" to File(root)
    }
    return list
}
