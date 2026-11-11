package com.mounsokdara.zyphvert.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
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

internal fun num(d: Double) = String.format(Locale.US, "%.3f", d).trimEnd('0').trimEnd('.')

/** Even dimensions + yuv420p: required by the hardware H.264/HEVC encoders. */
internal const val VID_TAIL = "scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p"

/**
 * Android Motion Photo: a JPEG with an MP4 appended. Works on the file in chunks, so a Motion Photo
 * with a very long video (hundreds of MB) doesn't need to fit in memory. Returns false if no video is found.
 */
internal fun extractMotionVideo(ctx: Context, uri: Uri, out: File): Boolean {
    // Seekable file: the picked file itself, or a streamed copy in the cache for content:// documents.
    val src: File = if (uri.scheme == "file") File(uri.path ?: return false) else {
        val tmp = File(ctx.cacheDir, "motion_src_${System.nanoTime()}")
        ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } ?: return false
        tmp
    }
    val isCopy = uri.scheme != "file"
    try {
        java.io.RandomAccessFile(src, "r").use { raf ->
            val total = raf.length()
            fun isFtyp(pos: Long): Boolean {
                if (pos < 0 || pos + 8 > total) return false
                val b = ByteArray(8); raf.seek(pos); raf.readFully(b)
                return b[4] == 'f'.code.toByte() && b[5] == 't'.code.toByte() && b[6] == 'y'.code.toByte() && b[7] == 'p'.code.toByte()
            }
            fun copyFrom(start: Long): Boolean {
                out.parentFile?.mkdirs()
                raf.seek(start)
                out.outputStream().use { o ->
                    val buf = ByteArray(1 shl 20)
                    while (true) { val n = raf.read(buf); if (n <= 0) break; o.write(buf, 0, n) }
                }
                return true
            }
            // Newer files say exactly where the video starts: XMP MicroVideoOffset or the container item length, counted from the end.
            val headLen = minOf(total, 262144L).toInt()
            val headBytes = ByteArray(headLen); raf.seek(0); raf.readFully(headBytes)
            val head = String(headBytes, Charsets.ISO_8859_1)
            val declared = Regex("MicroVideoOffset=\"(\\d+)\"").find(head)?.groupValues?.get(1)?.toLongOrNull()
                ?: Regex("<Container:Item[^>]*video/mp4[^>]*>").find(head)?.value
                    ?.let { Regex("Item:Length=\"(\\d+)\"").find(it)?.groupValues?.get(1)?.toLongOrNull() }
            if (declared != null && declared in 16 until total && isFtyp(total - declared)) return copyFrom(total - declared)

            // Otherwise scan for an "ftyp" box with a plausible size, a megabyte at a time.
            val sig = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
            val buf = ByteArray((1 shl 20) + 8)
            var pos = 100L
            while (pos < total - 8) {
                raf.seek(pos)
                val n = raf.read(buf)
                if (n < 8) break
                var i = 0
                while (i <= n - 4) {
                    if (buf[i] == sig[0] && buf[i + 1] == sig[1] && buf[i + 2] == sig[2] && buf[i + 3] == sig[3] && pos + i >= 4) {
                        val b = ByteArray(4); raf.seek(pos + i - 4); raf.readFully(b)
                        val size = ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
                        if (size in 16..128) return copyFrom(pos + i - 4)
                    }
                    i++
                }
                pos += n - 3
            }
            return false
        }
    } finally {
        if (isCopy) src.delete()
    }
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

// ------------------------------------------------------------------ smart bitrate

/** What the platform can tell us about a video, so the output is never bigger than it needs to be. */
internal data class VideoInfo(
    val width: Int,
    val height: Int,
    val fps: Float?,
    val videoKbps: Int?,
    val videoMime: String?,
    val audioMime: String?,
    val audioKbps: Int?,
)

internal const val AAC_MIME = "audio/mp4a-latm"
internal const val AVC_MIME = "video/avc"

/** Size (after rotation), frame rate, bitrates and codecs of the first video and audio track, or null if unreadable. */
internal fun videoInfo(ctx: Context, uri: Uri): VideoInfo? = runCatching {
    val ex = MediaExtractor()
    try {
        ex.setDataSource(ctx, uri, null)
        var w = 0; var h = 0; var rot = 0
        var fps: Float? = null; var vKbps: Int? = null; var vMime: String? = null
        var aMime: String? = null; var aKbps: Int? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            fun int(key: String): Int? = if (f.containsKey(key)) runCatching { f.getInteger(key) }.getOrNull() else null
            if (mime.startsWith("video/") && vMime == null) {
                vMime = mime
                w = int(MediaFormat.KEY_WIDTH) ?: 0
                h = int(MediaFormat.KEY_HEIGHT) ?: 0
                rot = int(MediaFormat.KEY_ROTATION) ?: 0
                vKbps = int(MediaFormat.KEY_BIT_RATE)?.div(1000)
                if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    fps = runCatching { f.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrNull()
                        ?: int(MediaFormat.KEY_FRAME_RATE)?.toFloat()
                }
            } else if (mime.startsWith("audio/") && aMime == null) {
                aMime = mime
                aKbps = int(MediaFormat.KEY_BIT_RATE)?.div(1000)
            }
        }
        if (vMime == null || w <= 0 || h <= 0) return@runCatching null
        if (vKbps == null) {
            // Many files only report one overall bitrate: take the audio share off it.
            val overall = MediaMetadataRetriever().let { r ->
                try { r.setDataSource(ctx, uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() }
                finally { r.release() }
            }
            if (overall != null && overall > 0) vKbps = ((overall / 1000) - (aKbps ?: if (aMime != null) 128 else 0)).toInt().takeIf { it > 100 }
        }
        val (rw, rh) = if (rot == 90 || rot == 270) h to w else w to h
        VideoInfo(rw, rh, fps?.takeIf { it in 1f..240f }, vKbps, vMime, aMime, aKbps)
    } finally { ex.release() }
}.getOrNull()

/** How many bits a codec needs for the same picture, relative to H.264. */
private fun codecNeed(mime: String?): Double = when (mime) {
    "video/hevc", "video/x-vnd.on2.vp9" -> 0.65
    "video/av01" -> 0.5
    "video/mp4v-es", "video/3gpp", "video/mpeg2", "video/x-ms-wmv" -> 1.6
    else -> 1.0
}

/**
 * Video bitrate (kb/s) for the hardware encoder. It follows the picture size and frame rate and never goes
 * above what the source needed, so a 1.5 Mb/s clip is not blown up to 10 Mb/s for no gain. [fallback] is used
 * when the source can't be read.
 */
internal fun hwVideoKbps(info: VideoInfo?, outHeight: Int, outFps: Int, qual: String, hevc: Boolean, fallback: Int): Int {
    if (info == null) return fallback
    val oh = if (outHeight in 1 until info.height) outHeight else info.height
    val ow = info.width.toDouble() * oh / info.height
    val srcFps = info.fps ?: 30f
    val f = (if (outFps > 0) minOf(outFps.toFloat(), srcFps) else srcFps).coerceIn(10f, 60f)
    val bpp = when (qual) { "high" -> 0.13; "low" -> 0.055; "tiny" -> 0.03; else -> 0.09 }
    val target = codecNeed(if (hevc) "video/hevc" else AVC_MIME)
    val estimate = ow * oh * f * bpp * target / 1000.0
    val cap = info.videoKbps?.let { it * target / codecNeed(info.videoMime) * (if (qual == "high") 1.25 else 1.0) }
    return minOf(estimate, cap ?: estimate).toInt().coerceAtLeast(250)
}

/** AAC bitrate that matches the source instead of always forcing 192 kb/s. */
internal fun audioKbpsFor(info: VideoInfo?): Int = (info?.audioKbps ?: 128).coerceIn(64, 192)
