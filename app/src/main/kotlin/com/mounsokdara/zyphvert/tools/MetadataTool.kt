package com.mounsokdara.zyphvert.tools

import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

private fun fail(msg: String): Nothing = throw IllegalArgumentException(msg)

private class Op(val key: String, val value: String?)

private val mp4Family = setOf("mp4", "m4a", "m4v", "m4b", "m4r", "mov", "3gp", "3g2")

private val gpsTags = listOf(
    "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef", "GPSAltitude", "GPSAltitudeRef",
    "GPSTimeStamp", "GPSDateStamp", "GPSProcessingMethod",
)

private fun parseOps(json: String): List<Op> {
    if (json.isBlank()) return emptyList()
    val arr = JSONArray(json)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Op(o.getString("k"), if (o.has("v") && !o.isNull("v")) o.getString("v") else null)
    }
}

internal fun buildMetadataJobs(r: ToolRequest): List<ToolJob> {
    val files = r.files("media")
    if (files.isEmpty()) fail("Choose a file first.")
    files.firstOrNull { !isMetaEditable(r.ctx, it) }?.let {
        fail("${it.name} can't be edited here. Photos, audio and video can be changed; for other files you can read every detail and copy it.")
    }
    val photo = isImageName(files.first().name)
    if (files.any { isImageName(it.name) != photo }) fail("Pick photos, or audio and video files, but not both at once.")
    val ops = parseOps(r.str("meta.ops"))
    val useName = r.bool("meta.useFilename") && !photo
    val cover = r.file("cover")
    val removeCover = r.bool("meta.coverRemove")
    if (ops.isEmpty() && !useName && cover == null && !removeCover) fail("Change a tag or the cover art first.")
    return files.map { f -> if (photo) photoJob(r, f, ops) else mediaJob(r, f, ops, useName, cover, removeCover) }
}

private fun mediaJob(r: ToolRequest, f: PickedFile, ops: List<Op>, useName: Boolean, cover: PickedFile?, removeCover: Boolean): ToolJob {
    val audio = isAudioName(f.name)
    val all = if (useName) ops.filter { it.key != "title" } + Op("title", f.name.substringBeforeLast('.')) else ops
    val ext = nameExt(f.name).ifEmpty { if (audio) "m4a" else "mp4" }
    val out = r.out("Metadata", f, ext, "_tagged")
    val tags = all.joinToString(" ") { "-metadata ${q("${cleanMetaKey(it.key)}=${cleanTag(it.value.orEmpty())}")}" }
    val flags = buildString {
        if (ext == "mp3") append("-id3v2_version 3 ")
        if (ext in mp4Family && all.any { it.value != null && cleanMetaKey(it.key).lowercase() !in standardMediaTags }) append("-movflags use_metadata_tags ")
    }
    val input = r.saf(f)
    val cmd = when {
        audio && cover != null -> {
            val enc = if (nameExt(cover.name) in setOf("jpg", "jpeg", "png")) "-c:v copy" else "-c:v mjpeg -pix_fmt yuvj420p -q:v 2"
            "-i $input -i ${r.saf(cover)} -map 0:a -map 1:v -c:a copy $enc -disposition:v:0 attached_pic $flags-map_metadata 0 $tags ${q(out.path)}"
        }
        audio && removeCover -> "-i $input -map 0:a -c copy $flags-map_metadata 0 $tags ${q(out.path)}"
        else -> "-i $input -map 0 -c copy $flags-map_metadata 0 $tags ${q(out.path)}"
    }
    return r.job(f, out, cmd)
}

private fun photoJob(r: ToolRequest, f: PickedFile, ops: List<Op>): ToolJob {
    if (ops.isEmpty()) fail("Change a tag first.")
    val ext = nameExt(f.name).ifEmpty { "jpg" }
    val out = r.out("Metadata", f, ext, "_tagged")
    return ToolJob(r.toolTitle, f.name, out.path, native = { withContext(Dispatchers.IO) { writeExif(r, f, out, ops) } })
}

private fun writeExif(r: ToolRequest, f: PickedFile, out: File, ops: List<Op>) {
    val input = r.ctx.contentResolver.openInputStream(f.uri) ?: throw IllegalStateException("Couldn't open ${f.name}.")
    input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
    val exif = ExifInterface(out.path)
    for (op in ops) {
        val v = op.value?.trim()?.takeIf { it.isNotEmpty() }
        if (op.key == "GPS") {
            if (v == null) gpsTags.forEach { exif.setAttribute(it, null) }
            else {
                val p = v.split(',').map { it.trim().toDoubleOrNull() }
                val lat = p.getOrNull(0)
                val lon = p.getOrNull(1)
                if (p.size != 2 || lat == null || lon == null) throw IllegalArgumentException("Location must look like 12.345678, 98.765432")
                exif.setLatLong(lat, lon)
            }
        } else exif.setAttribute(op.key, v)
    }
    exif.saveAttributes()
}
