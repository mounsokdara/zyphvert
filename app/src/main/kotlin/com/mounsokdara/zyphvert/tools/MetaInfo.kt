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

class MetaInfo(
    val photo: Boolean,
    val audio: Boolean,
    val tags: List<MetaRow>,
    val sections: List<MetaSection>,
    val cover: Bitmap?,
    val readOnly: Boolean = false,
    val kind: String? = null,
) {
    fun asText(): String = sections.joinToString("\n\n") { s ->
        s.title + "\n" + s.rows.joinToString("\n") { "${it.key}: ${it.value}" }
    }
}

private val readOnlyTags = setOf("major_brand", "minor_version", "compatible_brands")

internal val mediaTagSuggestions = listOf(
    "title", "artist", "album_artist", "album", "track", "disc", "date", "composer", "genre", "comment",
    "description", "copyright", "language", "encoder", "grouping", "lyrics", "performer", "publisher",
    "show", "network", "episode_id", "synopsis", "creation_time", "location",
)

internal val standardMediaTags = mediaTagSuggestions.toSet()

internal val audioTagDefaults = listOf(
    "title", "artist", "album_artist", "album", "track", "disc", "date", "genre", "composer", "comment", "lyrics",
)

internal val audioTagSuggestions = audioTagDefaults + listOf("performer", "publisher", "copyright", "language", "grouping", "compilation")

internal fun canonAudioKey(k: String): String = when (k.lowercase().replace(" ", "")) {
    "albumartist", "album_artist" -> "album_artist"
    "tracknumber", "track" -> "track"
    "discnumber", "disc" -> "disc"
    "year", "date" -> "date"
    "unsyncedlyrics", "lyrics" -> "lyrics"
    else -> k.lowercase()
}

internal val exifSuggestions = listOf(
    "ImageDescription", "Make", "Model", "Software", "Artist", "Copyright", "DateTime", "DateTimeOriginal",
    "DateTimeDigitized", "UserComment", "LensMake", "LensModel", "CameraOwnerName", "BodySerialNumber",
    "LensSerialNumber", "ExposureTime", "FNumber", "ISOSpeedRatings", "FocalLength", "FocalLengthIn35mmFilm",
    "Flash", "WhiteBalance", "ExposureProgram", "MeteringMode", "Orientation", "ExposureBiasValue",
    "OffsetTime", "OffsetTimeOriginal", "GPS",
)

internal val longTextTags = setOf("comment", "description", "lyrics", "synopsis", "usercomment", "imagedescription")

internal fun cleanMetaKey(k: String) = k.filter { it.isLetterOrDigit() || it in "_.-: ©" }.trim()

internal fun isMetaEditable(ctx: Context, f: PickedFile): Boolean {
    val ext = nameExt(f.name)
    return isImageName(f.name) || ext in VIDEO_EXTS || ext in AUDIO_EXTS || looksLikeMedia(ctx, f)
}

internal suspend fun loadMetaInfo(ctx: Context, engine: MediaEngine, f: PickedFile): MetaInfo = when {
    isImageName(f.name) -> loadPhotoInfo(ctx, f)
    isMetaEditable(ctx, f) -> loadMediaInfo(ctx, engine, f)
    else -> loadGenericInfo(ctx, f)
}

private fun loadGenericInfo(ctx: Context, f: PickedFile): MetaInfo {
    val facts = loadFileFacts(ctx, f)
    val extra = extractorSections(ctx, f)
    val kind = facts.firstOrNull()?.rows?.firstOrNull { it.key == "Detected type" }?.value
    return MetaInfo(
        photo = false, audio = false, tags = emptyList(), sections = facts + extra, cover = null,
        readOnly = true, kind = kind,
    )
}

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
    val probed = fmt?.optJSONObject("tags")?.strings().orEmpty()
        .filterKeys { it.lowercase() !in readOnlyTags }
        .map { MetaRow(it.key, it.value) }
    val tags = if (isAudioName(f.name)) {
        val have = probed.map { canonAudioKey(it.key) }.toSet()
        probed + readAudioTags(ctx, f).filter { canonAudioKey(it.key) !in have }
    } else probed

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
    if (streams == null || streams.length() == 0) sections += extractorSections(ctx, f)
    sections += loadFileFacts(ctx, f, summaryOnly = true)
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
    val all = mutableListOf(MetaSection("Image", details))
    all += extractorSections(ctx, f)
    all += loadFileFacts(ctx, f, summaryOnly = true)
    return MetaInfo(photo = true, audio = false, tags = tags, sections = all, cover = null)
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

private fun readAudioTags(ctx: Context, f: PickedFile): List<MetaRow> {
    val out = LinkedHashMap<String, String>()
    if (nameExt(f.name) == "mp3") runCatching { readId3(ctx, f.uri) }.getOrNull()?.forEach { (k, v) -> out[k] = v }
    runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, f.uri)
            fun put(k: String, code: Int) {
                if (k in out) return
                r.extractMetadata(code)?.trim()?.takeIf { it.isNotEmpty() }?.let { out[k] = it }
            }
            put("title", MediaMetadataRetriever.METADATA_KEY_TITLE)
            put("artist", MediaMetadataRetriever.METADATA_KEY_ARTIST)
            put("album_artist", MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            put("album", MediaMetadataRetriever.METADATA_KEY_ALBUM)
            put("track", MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            put("disc", MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            put("date", MediaMetadataRetriever.METADATA_KEY_YEAR)
            put("genre", MediaMetadataRetriever.METADATA_KEY_GENRE)
            put("composer", MediaMetadataRetriever.METADATA_KEY_COMPOSER)
        } finally { r.release() }
    }
    return out.map { MetaRow(it.key, it.value) }
}

private fun readId3(ctx: Context, uri: Uri): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    ctx.contentResolver.openInputStream(uri)?.use { raw ->
        val ins = java.io.DataInputStream(raw.buffered(64 * 1024))
        val h = ByteArray(10)
        ins.readFully(h)
        if (h[0] != 'I'.code.toByte() || h[1] != 'D'.code.toByte() || h[2] != '3'.code.toByte()) return out
        val ver = h[3].toInt()
        if (ver != 3 && ver != 4) return out
        fun synch(b: ByteArray, o: Int) = (b[o].toInt() and 0x7f shl 21) or (b[o + 1].toInt() and 0x7f shl 14) or (b[o + 2].toInt() and 0x7f shl 7) or (b[o + 3].toInt() and 0x7f)
        fun plain(b: ByteArray, o: Int) = (b[o].toInt() and 0xff shl 24) or (b[o + 1].toInt() and 0xff shl 16) or (b[o + 2].toInt() and 0xff shl 8) or (b[o + 3].toInt() and 0xff)
        val total = synch(h, 6)
        if (total <= 0 || total > 24 * 1024 * 1024) return out
        val body = ByteArray(total)
        ins.readFully(body)
        var pos = 0
        if (h[5].toInt() and 0x40 != 0 && total >= 4) pos = if (ver == 4) synch(body, 0) else plain(body, 0) + 4
        fun term(d: ByteArray, from: Int, enc: Int): Int {
            var i = from
            if (enc == 1 || enc == 2) { while (i + 1 < d.size) { if (d[i].toInt() == 0 && d[i + 1].toInt() == 0) return i; i += 2 }; return d.size }
            while (i < d.size && d[i].toInt() != 0) i++
            return i
        }
        fun text(d: ByteArray, from: Int, enc: Int): String {
            if (from >= d.size) return ""
            val cs = when (enc) { 0 -> Charsets.ISO_8859_1; 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; else -> Charsets.UTF_8 }
            return String(d, from, d.size - from, cs).split('\u0000').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("; ")
        }
        while (pos + 10 <= total) {
            if (body[pos].toInt() == 0) break
            val id = String(body, pos, 4, Charsets.ISO_8859_1)
            val size = if (ver == 4) synch(body, pos + 4) else plain(body, pos + 4)
            val start = pos + 10
            if (size <= 0 || start + size > total) break
            val d = body.copyOfRange(start, start + size)
            pos = start + size
            val enc = d[0].toInt()
            val key = when (id) {
                "TIT2" -> "title"; "TPE1" -> "artist"; "TPE2" -> "album_artist"; "TALB" -> "album"
                "TRCK" -> "track"; "TPOS" -> "disc"; "TDRC", "TYER" -> "date"; "TCON" -> "genre"; "TCOM" -> "composer"
                "COMM" -> "comment"; "USLT" -> "lyrics"
                else -> continue
            }
            val v = if (id == "COMM" || id == "USLT") {
                if (d.size < 5) "" else text(d, term(d, 4, enc) + (if (enc == 1 || enc == 2) 2 else 1), enc)
            } else text(d, 1, enc)
            if (v.isNotEmpty() && key !in out) out[key] = v
        }
    }
    return out
}
