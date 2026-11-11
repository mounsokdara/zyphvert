package com.mounsokdara.zyphvert.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import com.mounsokdara.zyphvert.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

class ProtectedEntry(val name: String, val isDir: Boolean, val size: Long, val modified: Long)

sealed interface ProtectedListing {
    data class Ok(val entries: List<ProtectedEntry>) : ProtectedListing
    /** Neither a folder permission nor Shizuku is available yet. */
    data object Locked : ProtectedListing
}

/**
 * Android 11+ keeps apps out of Android/data and Android/obb, even with All files access. Two ways in:
 * a folder permission the user grants through the system dialog (Android 11 to 12), or the Shizuku app (any Android 11+).
 */
@Singleton
class ProtectedStorage @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: SettingsRepository,
) {
    private val base = Environment.getExternalStorageDirectory().path
    private val docProvider = "com.android.externalstorage.documents"

    /** Android 13+ no longer lets apps ask for these folders through the system dialog. */
    val folderPermissionPossible: Boolean
        get() = Build.VERSION.SDK_INT in Build.VERSION_CODES.R..Build.VERSION_CODES.S_V2

    private fun normalize(path: String): String = when {
        path == "/sdcard" || path.startsWith("/sdcard/") -> base + path.removePrefix("/sdcard")
        path == "/storage/self/primary" || path.startsWith("/storage/self/primary/") -> base + path.removePrefix("/storage/self/primary")
        else -> path
    }

    /** Which of the two locked folders [path] is in ("data" or "obb"), or null. */
    private fun lockedRoot(path: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val p = normalize(path)
        return listOf("data", "obb").firstOrNull { p == "$base/Android/$it" || p.startsWith("$base/Android/$it/") }
    }

    fun isProtected(path: String): Boolean = lockedRoot(path) != null

    private fun docId(path: String) = "primary:" + normalize(path).removePrefix("$base/")

    /** The granted folder permission that covers [path], if any, as (tree, document id). */
    private suspend fun treeFor(path: String): Pair<Uri, String>? {
        val id = docId(path)
        for (t in settings.safTrees.first()) {
            val tree = Uri.parse(t)
            val root = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
            if (id == root || id.startsWith("$root/")) return tree to id
        }
        return null
    }

    private fun children(tree: Uri, id: String): List<ProtectedEntry>? = runCatching {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        ctx.contentResolver.query(uri, cols, null, null, null)?.use { c ->
            val out = ArrayList<ProtectedEntry>()
            while (c.moveToNext()) {
                out += ProtectedEntry(
                    name = c.getString(0) ?: continue,
                    isDir = c.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = if (c.isNull(2)) 0L else c.getLong(2),
                    modified = if (c.isNull(3)) 0L else c.getLong(3),
                )
            }
            out
        }
    }.getOrNull()

    suspend fun list(path: String): ProtectedListing = withContext(Dispatchers.IO) {
        treeFor(path)?.let { (tree, id) -> children(tree, id)?.let { return@withContext ProtectedListing.Ok(it) } }
        ShizukuFs.list(ctx, normalize(path))?.let { return@withContext ProtectedListing.Ok(it) }
        ProtectedListing.Locked
    }

    /** Something a tool can read for the locked file at [path]: a content link, or a copy in the app's cache. */
    suspend fun readableUri(path: String): Uri? = withContext(Dispatchers.IO) {
        treeFor(path)?.let { (tree, id) -> return@withContext DocumentsContract.buildDocumentUriUsingTree(tree, id) }
        ShizukuFs.copyToCache(ctx, normalize(path))?.let { Uri.fromFile(it) }
    }

    /** Where the system folder dialog should start for [path]. */
    fun initialTreeUri(path: String): Uri =
        DocumentsContract.buildDocumentUri(docProvider, "primary:Android/${lockedRoot(path) ?: "data"}")

    /** Keeps a folder permission the user just granted. False if it isn't Android/data or Android/obb itself. */
    suspend fun addTree(tree: Uri): Boolean {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
        if (id != "primary:Android/data" && id != "primary:Android/obb") return false
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settings.addSafTree(tree.toString())
        return true
    }
}
