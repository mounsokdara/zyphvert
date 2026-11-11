package com.mounsokdara.zyphvert.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.mounsokdara.zyphvert.engine.EngineEvent
import com.mounsokdara.zyphvert.engine.MediaEngine
import org.json.JSONObject
import java.util.Locale

class MetaRow(val key: String, val value: String)
class MetaSection(val title: String, val rows: List<MetaRow>)

/** Everything the Metadata Editor shows for one file. */
class MetaInfo(
    val photo: Boolean,
    val audio: Boolean,
    /** Editable tags, in the order the file stores them. */
    val tags: List<MetaRow>,
    /** Read-only details, grouped (file, each stream, chapters). */
    val sections: List<MetaSection>,
    val cover: Bitmap?,
) {
    fun asText(): String = sections.joinToString("\n\n") { s ->
        s.title + "\n" + s.rows.joinToString("\n") { "${it.key}: ${it.value}" }
    }
}

/** Tags ffprobe reports for MP4/MOV files that are container facts, not something to edit. */
private val readOnlyTags = setOf("major_brand", "minor_version", "compatible_brands")

internal val mediaTagSuggestions = listOf(
    "title", "artist", "album_artist", "album", "track", "disc", "date", "composer", "genre", "comment",
    "description", "copyright", "language", "encoder", "grouping", "lyrics", "performer", "publisher",
    "show", "network", "episode_id", "synopsis", "creation_time", "location",
)

/** Tags MP4/MOV writes in the normal way; any other name needs FFmpeg's custom-tag mode. */
internal val standardMediaTags = mediaTagSuggestions.toSet()

internal val exifSuggestions = listOf(
    "ImageDescription", "Make", "Model", "Software", "Artist", "Copyright", "DateTime", "DateTimeOriginal",
    "DateTimeDigitized", "UserComment", "LensMake", "LensModel", "CameraOwnerName", "BodySerialNumber",
    "LensSerialNumber", "ExposureTime", "FNumber", "ISOSpeedRatings", "FocalLength", "FocalLengthIn35mmFilm",
    "Flash", "WhiteBalance", "ExposureProgram", "MeteringMode", "Orientation", "ExposureBiasValue",
    "OffsetTime", "OffsetTimeOriginal", "GPS",
)

/** Tag names that hold long text, so their field can grow. */
internal val longTextTags = setOf("comment", "description", "lyrics", "synopsis", "usercomment", "imagedescription")

/** A tag name that is safe inside an FFmpeg `-metadata` argument. */
internal fun cleanMetaKey(k: String) = k.filter { it.isLetterOrDigit() || it in "_.-: ©" }.trim()

internal suspend fun loadMetaInfo(ctx: Context, engine: MediaEngine, f: PickedFile): MetaInfo =
    if (isImageName(f.name)) loadPhotoInfo(ctx, f) else loadMediaInfo(ctx, engine, f)

private suspend fun loadMediaInfo(ctx: Context, engine: MediaEngine, f: PickedFile): MetaInfo {
    val src = f.path?.let { q(it) } ?: engine.safRead(ctx, f.uri)
    val text = StringBuilder()
    if (src != null && engine.isAvailable) {
        engine.run("-v error -of json -show_format -show_streams -show_chapters $src", probe = true).collect { ev ->
            if (ev is EngineEvent.Log) text.append(ev.text)
        }
    }
    val root = runCatching { JSONObject(text.substring(text.indexOf('{').coerceAtLeast(0))) }.getOrNull()
    val fmt = root?.optJSONObject("format")
    val tags = fmt?.optJSONObject("tags")?.strings().orEmpty()
        .filterKeys { it.lowercase() !in readOnlyTags }
        .map { MetaRow(it.key, it.value) }

    val sections = mutableListOf<MetaSection>()
    if (fmt != null) {
        val rows = scalarRows(fmt, setOf("tags", "filename", "probe_score"))
        if (rows.isNotEmpty()) sections += MetaSection("File", rows)
    }
    val streams = root?.optJSONArray("streams")
    for (i in 0 until (streams?.length() ?: 0)) {
        val s = streams!!.getJSONObject(i)
        val rows = scalarRows(s, setOf("index", "codec_tag", "codec_tag_string", "disposition", "tags")) +
            (s.optJSONObject("tags")?.strings().orEmpty().map { MetaRow("Tag: ${it.key}", it.value) })
        val title = "Stream ${i + 1} · ${s.optString("codec_type")} · ${s.optString("codec_name")}"
        if (rows.isNotEmpty()) sections += MetaSection(title, rows)
    }
    val chapters = root?.optJSONArray("chapters")
    if (chapters != null && chapters.length() > 0) {
        val rows = (0 until chapters.length()).map { i ->
            val c = chapters.getJSONObject(i)
            val name = c.optJSONObject("tags")?.optString("title").orEmpty()
            MetaRow("${clock(c.optString("start_time"))} → ${clock(c.optString("end_time"))}", name)
        }
        sections += MetaSection("Chapters", rows)
    }
    val audio = isAudioName(f.name)
    return MetaInfo(photo = false, audio = audio, tags = tags, sections = sections, cover = if (audio) embeddedCover(ctx, f.uri) else null)
}

private fun loadPhotoInfo(ctx: Context, f: PickedFile): MetaInfo {
    val tags = mutableListOf<MetaRow>()
    runCatching {
        ctx.contentResolver.openInputStream(f.uri)?.use { ins ->
            val exif = ExifInterface(ins)
            for (t in exifSuggestions) {
                if (t == "GPS") continue
                exif.getAttribute(t)?.trim()?.takeIf { it.isNotEmpty() }?.let { tags += MetaRow(t, it) }
            }
            exif.latLong?.let { tags += MetaRow("GPS", String.format(Locale.US, "%.6f, %.6f", it[0], it[1])) }
        }
    }
    val details = mutableListOf<MetaRow>()
    details += MetaRow("Name", f.name)
    runCatching { ctx.contentResolver.openAssetFileDescriptor(f.uri, "r")?.use { details += MetaRow("Size", size(it.length)) } }
    runCatching {
        ctx.contentResolver.openInputStream(f.uri)?.use { ins ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(ins, null, o)
            if (o.outWidth > 0) details += MetaRow("Dimensions", "${o.outWidth} × ${o.outHeight}")
            o.outMimeType?.let { details += MetaRow("Type", it) }
        }
    }
    return MetaInfo(photo = true, audio = false, tags = tags, sections = listOf(MetaSection("Image", details)), cover = null)
}

private fun JSONObject.strings(): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    val it = keys()
    while (it.hasNext()) { val k = it.next(); out[k] = optString(k) }
    return out
}

private fun scalarRows(o: JSONObject, skip: Set<String>): List<MetaRow> {
    val rows = mutableListOf<MetaRow>()
    val it = o.keys()
    while (it.hasNext()) {
        val k = it.next()
        if (k in skip) continue
        val v = o.opt(k)
        if (v == null || v == JSONObject.NULL || v is JSONObject || v is org.json.JSONArray) continue
        rows += MetaRow(label(k), pretty(k, v.toString()))
    }
    return rows
}

private fun label(k: String) = k.replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun pretty(k: String, v: String): String = when (k) {
    "size" -> v.toLongOrNull()?.let(::size) ?: v
    "duration" -> v.toDoubleOrNull()?.let { clock(v) } ?: v
    "bit_rate", "max_bit_rate" -> v.toLongOrNull()?.let { "${it / 1000} kb/s" } ?: v
    else -> v
}

private fun size(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}

private fun clock(sec: String): String {
    val t = sec.toDoubleOrNull() ?: return sec
    val ms = (t * 1000).toLong()
    val h = ms / 3_600_000
    val m = ms / 60_000 % 60
    val s = ms / 1000 % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

private fun embeddedCover(ctx: Context, uri: Uri): Bitmap? = runCatching {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(ctx, uri)
        r.embeddedPicture?.let { decodeBitmap(it, 640) }
    } finally { r.release() }
}.getOrNull()

/** A picked image, shrunk so it is cheap to show. */
internal fun loadImageBitmap(ctx: Context, uri: Uri, max: Int): Bitmap? = runCatching {
    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { decodeBitmap(it, max) }
}.getOrNull()

private fun decodeBitmap(bytes: ByteArray, max: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= max && bounds.outHeight / (sample * 2) >= max) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
