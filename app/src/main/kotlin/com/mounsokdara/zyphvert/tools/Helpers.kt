package com.mounsokdara.zyphvert.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

internal val imageExts = IMAGE_EXTS
internal val audioExts = AUDIO_EXTS

internal fun nameExt(name: String) = name.substringAfterLast('.', "").lowercase()
internal fun baseName(name: String) = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(40).ifEmpty { "file" }
internal fun isImageName(name: String) = nameExt(name) in imageExts
internal fun isAudioName(name: String) = nameExt(name) in audioExts

internal fun Context.displayName(uri: Uri): String {
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}

/** Quote a whole argument for the FFmpegKit command parser. */
internal fun q(s: String) = "\"$s\""

/** Keep user text safe inside a filter or metadata argument. */
internal fun clean(s: String) = s.replace(Regex("[^\\p{L}\\p{N} .@_#!&()-]"), "").trim()

internal fun num(d: Double) = String.format(Locale.US, "%.3f", d).trimEnd('0').trimEnd('.')

/** Even dimensions + yuv420p: required by the hardware H.264/HEVC encoders. */
internal const val VID_TAIL = "scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p"

/** Width/height as ffmpeg will see them (after rotation), for scaling overlays. */
internal fun mediaSize(ctx: Context, uri: Uri, image: Boolean): Pair<Int, Int>? = runCatching {
    if (image) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth > 0) o.outWidth to o.outHeight else null
    } else {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: return@runCatching null
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: return@runCatching null
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            if (rot == 90 || rot == 270) h to w else w to h
        } finally { r.release() }
    }
}.getOrNull()

/** Android Motion Photo: a JPEG with an MP4 appended. Returns false if no embedded video is found. */
internal fun extractMotionVideo(ctx: Context, uri: Uri, out: File): Boolean {
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
    // Newer files say exactly where the video starts: XMP MicroVideoOffset or the Container item length, counted from the end.
    val head = String(bytes, 0, minOf(bytes.size, 262144), Charsets.ISO_8859_1)
    val declared = Regex("MicroVideoOffset=\"(\\d+)\"").find(head)?.groupValues?.get(1)?.toLongOrNull()
        ?: Regex("<Container:Item[^>]*video/mp4[^>]*>").find(head)?.value
            ?.let { Regex("Item:Length=\"(\\d+)\"").find(it)?.groupValues?.get(1)?.toLongOrNull() }
    if (declared != null && declared in 16 until bytes.size.toLong()) {
        val s = bytes.size - declared.toInt()
        if (s + 8 <= bytes.size && bytes[s + 4] == 'f'.code.toByte() && bytes[s + 5] == 't'.code.toByte() &&
            bytes[s + 6] == 'y'.code.toByte() && bytes[s + 7] == 'p'.code.toByte()) {
            out.parentFile?.mkdirs()
            out.outputStream().use { it.write(bytes, s, bytes.size - s) }
            return true
        }
    }
    val sig = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    var from = 100
    while (from < bytes.size - 8) {
        var idx = -1
        var i = from
        while (i <= bytes.size - 4) {
            if (bytes[i] == sig[0] && bytes[i + 1] == sig[1] && bytes[i + 2] == sig[2] && bytes[i + 3] == sig[3]) { idx = i; break }
            i++
        }
        if (idx < 4) return false
        val size = ((bytes[idx - 4].toInt() and 0xFF) shl 24) or ((bytes[idx - 3].toInt() and 0xFF) shl 16) or
            ((bytes[idx - 2].toInt() and 0xFF) shl 8) or (bytes[idx - 1].toInt() and 0xFF)
        if (size in 16..128) {
            out.parentFile?.mkdirs()
            out.outputStream().use { it.write(bytes, idx - 4, bytes.size - (idx - 4)) }
            return true
        }
        from = idx + 1
    }
    return false
}

@Suppress("DEPRECATION")
/** Decode with Android's own codecs (handles HEIC/AVIF where the device supports them) and re-encode. */
internal fun convertImageNative(ctx: Context, uri: Uri, out: File, format: String, quality: Int) {
    val bmp: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, _, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("Couldn't open the image")
    }
    val fmt = when (format) {
        "png" -> Bitmap.CompressFormat.PNG
        "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                  else Bitmap.CompressFormat.WEBP
        else -> Bitmap.CompressFormat.JPEG
    }
    out.parentFile?.mkdirs()
    out.outputStream().use { if (!bmp.compress(fmt, quality, it)) throw IllegalStateException("Couldn't encode the image") }
    bmp.recycle()
}

internal val timeRegex = Regex("^\\d+(:\\d{1,2}){0,2}(\\.\\d+)?$")

/** Duration in seconds, or null if it can't be read. */
internal fun durationSec(ctx: Context, uri: Uri): Double? = runCatching {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(ctx, uri)
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()?.div(1000.0)
    } finally { r.release() }
}.getOrNull()

/** (hasVideo, hasAudio) as the platform sees the file. */
internal fun streamsOf(ctx: Context, uri: Uri): Pair<Boolean, Boolean>? = runCatching {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(ctx, uri)
        (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") to
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
    } finally { r.release() }
}.getOrNull()

/** Tag values go inside double quotes, so only quotes, backslashes and newlines are a problem. */
internal fun cleanTag(s: String) = s.filterNot { it == '"' || it == '\'' || it == '\\' || it == '\n' || it == '\r' }.trim()

/**
 * Live Photo / Motion Photo to its video: the paired MOV for an iPhone Live Photo,
 * or the MP4 hidden inside an Android Motion Photo JPEG.
 */
internal fun resolveLive(ctx: Context, f: PickedFile): PickedFile {
    if (!isImageName(f.name)) return f
    f.companion?.let { c ->
        val file = File(c)
        if (file.isFile) return PickedFile(Uri.fromFile(file), file.name)
    }
    val tmp = File(ctx.cacheDir, "live_${(f.uri.toString().hashCode().toLong() and 0xffffffffL)}.mp4")
    if (!tmp.exists() || tmp.length() == 0L) {
        if (!extractMotionVideo(ctx, f.uri, tmp)) {
            throw IllegalArgumentException("${f.name} isn't a Live Photo or Motion Photo. For an iPhone Live Photo, pick the picture that has its video next to it.")
        }
    }
    return PickedFile(Uri.fromFile(tmp), f.name.substringBeforeLast('.') + ".mp4")
}

/**
 * Android Motion Photo: the JPEG still, with Google's XMP (Motion Photo + the older MicroVideo tags) inserted
 * after the JPEG start marker, and the MP4 clip appended. Google Photos and most Android galleries then play it as a live photo.
 */
internal fun buildMotionPhoto(still: File, clip: File, out: File, presentationUs: Long = 0L) {
    val jpg = still.readBytes()
    if (jpg.size < 4 || jpg[0] != 0xFF.toByte() || jpg[1] != 0xD8.toByte()) throw IllegalStateException("The still frame isn't a JPEG.")
    val n = clip.length()
    if (n <= 0L) throw IllegalStateException("The video part is empty.")
    val xml = "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
        "<rdf:Description rdf:about=\"\" xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\" " +
        "xmlns:Container=\"http://ns.google.com/photos/1.0/container/\" xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\" " +
        "GCamera:MotionPhoto=\"1\" GCamera:MotionPhotoVersion=\"1\" GCamera:MotionPhotoPresentationTimestampUs=\"$presentationUs\" " +
        "GCamera:MicroVideo=\"1\" GCamera:MicroVideoVersion=\"1\" GCamera:MicroVideoOffset=\"$n\" GCamera:MicroVideoPresentationTimestampUs=\"$presentationUs\">" +
        "<Container:Directory><rdf:Seq>" +
        "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\" Item:Length=\"0\" Item:Padding=\"0\"/></rdf:li>" +
        "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" Item:Length=\"$n\" Item:Padding=\"0\"/></rdf:li>" +
        "</rdf:Seq></Container:Directory></rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>"
    val payload = "http://ns.adobe.com/xap/1.0/".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + xml.toByteArray(Charsets.UTF_8)
    val len = payload.size + 2
    out.parentFile?.mkdirs()
    out.outputStream().buffered().use { o ->
        o.write(jpg, 0, 2)
        o.write(0xFF); o.write(0xE1); o.write(len shr 8); o.write(len and 0xFF)
        o.write(payload)
        o.write(jpg, 2, jpg.size - 2)
        clip.inputStream().use { it.copyTo(o) }
    }
}
