package com.mounsokdara.zyphvert.ui.more

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Where the app's releases live; the changelog and the update check both read from here. */
object AboutInfo {
    const val NAME = "Zyphvert"
    const val AUTHOR = "Moun Sokdara"
    const val AUTHOR_ROLE = "Lead Developer"
    const val GITHUB_LOGIN = "mounsokdara"
    const val REPO_SLUG = "$GITHUB_LOGIN/zyphvert"
}

/** A string field, with JSON null read as empty (Android's optString turns null into the text "null"). */
private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

class ReleaseNote(
    val tag: String,
    val title: String,
    val body: String,
    val url: String,
    val apkUrl: String?,
    /** Epoch milliseconds, or null. */
    val publishedAt: Long?,
    val prerelease: Boolean,
) {
    val version: String get() = tag.replaceFirst(Regex("^[vV]"), "")

    fun toJson(): JSONObject = JSONObject()
        .put("tag_name", tag).put("name", title).put("body", body).put("html_url", url)
        .put("apkUrl", apkUrl ?: JSONObject.NULL).put("published_at", publishedAt ?: JSONObject.NULL).put("prerelease", prerelease)

    companion object {
        private fun iso(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        /** From GitHub's release JSON, or from our own saved copy of it. */
        fun fromJson(j: JSONObject): ReleaseNote {
            val tag = j.str("tag_name")
            val name = j.str("name").trim()
            val published = j.opt("published_at").let {
                when (it) {
                    is Number -> it.toLong()
                    is String -> runCatching { iso().parse(it)?.time }.getOrNull()
                    else -> null
                }
            }
            val assets = j.optJSONArray("assets")
            val apk = if (assets != null) pickApk(assets) else j.str("apkUrl").takeIf { it.isNotEmpty() }
            return ReleaseNote(
                tag = tag,
                title = name.ifEmpty { tag },
                body = j.str("body").replace("\r", "").trim(),
                url = j.str("html_url").ifEmpty { "https://github.com/${AboutInfo.REPO_SLUG}/releases" },
                apkUrl = apk,
                publishedAt = published,
                prerelease = j.optBoolean("prerelease", false),
            )
        }

        /** The APK built for this phone's CPU, else the universal one, else any APK. */
        private fun pickApk(assets: JSONArray): String? {
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name").lowercase().endsWith(".apk") }
            fun url(o: JSONObject) = o.optString("browser_download_url").takeIf { it.isNotEmpty() }
            for (abi in Build.SUPPORTED_ABIS) apks.firstOrNull { it.optString("name").contains(abi, ignoreCase = true) }?.let { return url(it) }
            apks.firstOrNull { it.optString("name").contains("universal", ignoreCase = true) }?.let { return url(it) }
            return apks.firstOrNull()?.let(::url)
        }
    }
}

class ReleaseFeed(
    val releases: List<ReleaseNote>,
    val fromCache: Boolean,
    val error: String? = null,
    val savedAt: Long? = null,
) {
    /** The newest version, preferring stable releases over pre-releases. */
    val latest: ReleaseNote?
        get() {
            for (stableOnly in listOf(true, false)) {
                var best: ReleaseNote? = null
                for (r in releases) {
                    if (stableOnly && r.prerelease) continue
                    if (versionParts(r.version).isEmpty()) continue
                    if (best == null || compareVersions(r.version, best.version) > 0) best = r
                }
                if (best != null) return best
            }
            return null
        }
}

private fun versionParts(v: String): List<Int> = Regex("\\d+").findAll(v).mapNotNull { it.value.toIntOrNull() }.toList()

/** Compares "1.0.4" with "v1.0.10" and so on, number by number. */
fun compareVersions(a: String, b: String): Int {
    val x = versionParts(a)
    val y = versionParts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val p = x.getOrElse(i) { 0 }
        val q = y.getOrElse(i) { 0 }
        if (p != q) return p.compareTo(q)
    }
    return 0
}

/** Release notes without the markdown noise: link text instead of links, no bold or code marks. */
fun plainNotes(md: String): String =
    md.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)")) { it.groupValues[1] }.replace(Regex("(\\*\\*|__|`)"), "")

object ReleaseNotes {
    private const val FILE = "release_notes.json"
    private const val FRESH_MS = 60L * 60 * 1000

    private fun readSaved(ctx: Context): ReleaseFeed? = runCatching {
        val f = File(ctx.cacheDir, FILE)
        if (!f.exists()) return null
        val j = JSONObject(f.readText())
        val arr = j.getJSONArray("releases")
        ReleaseFeed(
            releases = (0 until arr.length()).map { ReleaseNote.fromJson(arr.getJSONObject(it)) }.filter { it.apkUrl != null },
            fromCache = true,
            savedAt = j.optLong("fetchedAt").takeIf { it > 0 },
        )
    }.getOrNull()

    private fun save(ctx: Context, list: List<ReleaseNote>) {
        runCatching {
            val arr = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }
            File(ctx.cacheDir, FILE).writeText(JSONObject().put("fetchedAt", System.currentTimeMillis()).put("releases", arr).toString())
        }
    }

    /** Releases from GitHub, or the saved copy when offline. A copy under an hour old is used without asking GitHub. */
    suspend fun load(ctx: Context, refresh: Boolean = false): ReleaseFeed = withContext(Dispatchers.IO) {
        val saved = readSaved(ctx)
        if (!refresh && saved?.savedAt != null && System.currentTimeMillis() - saved.savedAt < FRESH_MS) return@withContext saved
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("https://api.github.com/repos/${AboutInfo.REPO_SLUG}/releases?per_page=100").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "zyphvert-app")
            }
            if (conn.responseCode != 200) throw java.io.IOException("GitHub answered ${conn.responseCode}")
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !it.optBoolean("draft", false) }.map(ReleaseNote::fromJson)
                // Only releases that carry an APK count as app versions (the FFmpeg library release has none).
                .filter { it.apkUrl != null }
            save(ctx, list)
            ReleaseFeed(list, fromCache = false, savedAt = System.currentTimeMillis())
        } catch (e: Exception) {
            val msg = if (e is UnknownHostException || e is SocketTimeoutException) "No connection to GitHub" else "Could not read GitHub releases"
            if (saved != null) ReleaseFeed(saved.releases, fromCache = true, error = msg, savedAt = saved.savedAt)
            else ReleaseFeed(emptyList(), fromCache = false, error = msg)
        } finally {
            conn?.disconnect()
        }
    }
}
