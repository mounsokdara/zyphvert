package com.mounsokdara.zyphvert.tools

import java.io.File

// ---------------------------------------------------------------- audio

internal class AFmt(
    val key: String, val label: String, val ext: String, val codec: String,
    val lossy: Boolean = true, val cover: Boolean = false, val mux: String = "",
)

internal val audioTable = listOf(
    AFmt("mp3", "MP3", "mp3", "libmp3lame", cover = true),
    AFmt("aac", "AAC", "aac", "aac", mux = "-f adts"),
    AFmt("m4a", "M4A (AAC)", "m4a", "aac", cover = true),
    AFmt("m4b", "M4B (audiobook)", "m4b", "aac", cover = true, mux = "-f ipod"),
    AFmt("m4r", "M4R (iPhone ringtone)", "m4r", "aac", mux = "-f ipod"),
    AFmt("opus", "Opus", "opus", "libopus"),
    AFmt("tgvoice", "Telegram voice (OGG Opus)", "ogg", "libopus", mux = "-application voip -f ogg"),
    AFmt("ogg", "OGG Vorbis", "ogg", "libvorbis"),
    AFmt("oga", "OGA (Ogg audio)", "oga", "libvorbis", mux = "-f ogg"),
    AFmt("mka", "MKA (Matroska audio)", "mka", "libopus", mux = "-f matroska"),
    AFmt("wav", "WAV", "wav", "pcm_s16le", lossy = false),
    AFmt("flac", "FLAC", "flac", "flac", lossy = false, cover = true),
    AFmt("alac", "ALAC (Apple Lossless)", "m4a", "alac", lossy = false, cover = true),
    AFmt("aiff", "AIFF", "aiff", "pcm_s16be", lossy = false, mux = "-f aiff"),
    AFmt("wma", "WMA", "wma", "wmav2"),
    AFmt("ac3", "AC3", "ac3", "ac3"),
    AFmt("eac3", "EAC3", "eac3", "eac3"),
    AFmt("mp2", "MP2", "mp2", "mp2"),
    AFmt("au", "AU", "au", "pcm_s16be", lossy = false, mux = "-f au"),
    AFmt("caf", "CAF", "caf", "pcm_s16le", lossy = false, mux = "-f caf"),
    AFmt("w64", "W64 (Wave64)", "w64", "pcm_s16le", lossy = false, mux = "-f w64"),
    AFmt("tta", "TTA", "tta", "tta", lossy = false),
    AFmt("wv", "WavPack", "wv", "wavpack", lossy = false),
    AFmt("spx", "Speex", "spx", "libspeex"),
)

internal fun audioFmt(key: String) = audioTable.firstOrNull { it.key == key } ?: audioTable.first()
internal val audioItems = audioTable.map { Item(it.key, it.label) }
internal val lossyKeys = audioTable.filter { it.lossy }.map { it.key }.toSet()

private val vbrKbps = mapOf(0 to 256, 2 to 192, 4 to 160, 6 to 128, 8 to 96)

internal val bitrateItems: List<Item> = buildList {
    add(Item("auto", "Auto (best for the format)"))
    for (k in listOf(32, 48, 64, 96, 128, 160, 192, 224, 256, 320)) add(Item("cbr:$k", "$k kb/s CBR", "Constant bitrate"))
    for (k in listOf(64, 96, 128, 160, 192, 256, 320)) add(Item("abr:$k", "$k kb/s ABR", "Average bitrate"))
    add(Item("vbr:0", "V0 · about 245 kb/s VBR", "Highest quality variable bitrate"))
    add(Item("vbr:2", "V2 · about 190 kb/s VBR"))
    add(Item("vbr:4", "V4 · about 165 kb/s VBR"))
    add(Item("vbr:6", "V6 · about 115 kb/s VBR"))
    add(Item("vbr:8", "V8 · about 85 kb/s VBR", "Smaller file"))
}

internal val sampleRateItems = listOf(Item("auto", "Auto (keep original)")) +
    listOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 96000).map { Item("$it", "$it Hz") }

internal val channelItems = listOf(Item("auto", "Auto (keep original)"), Item("1", "Mono (1.0)"), Item("2", "Stereo (2.0)"))

internal val compressionItems = listOf(Item("auto", "Auto (default)")) +
    (0..9).map { Item("$it", if (it == 0) "0 · fastest" else if (it == 9) "9 · smallest" else "$it") }

/** Codec options for the audio stream, including bitrate/quality choices. */
internal fun audioCodecArgs(fmt: AFmt, br: String, sr: String, ch: String, comp: String): String {
    val a = mutableListOf("-c:a ${fmt.codec}")
    if (fmt.lossy) {
        val (mode, v) = if (br == "auto") "auto" to 0 else br.split(":").let { it[0] to it[1].toInt() }
        when (mode) {
            "cbr" -> a += "-b:a ${v}k"
            "abr" -> { a += "-b:a ${v}k"; if (fmt.key == "mp3") a += "-abr 1" }
            "vbr" -> when (fmt.key) {
                "mp3" -> a += "-q:a $v"
                "ogg", "oga" -> a += "-q:a ${(10 - v).coerceIn(2, 10) / 1}"
                else -> a += "-b:a ${vbrKbps[v] ?: 160}k"
            }
            else -> a += when (fmt.key) {
                "mp3" -> "-q:a 2"; "ogg", "oga" -> "-q:a 5"; "opus", "mka" -> "-b:a 128k"; "tgvoice" -> "-b:a 32k"; "spx" -> "-q:a 6"; else -> "-b:a 192k"
            }
        }
    }
    // Telegram voice notes are mono Opus at 48 kHz, so those are the defaults for that format.
    val voice = fmt.key == "tgvoice"
    val srUse = if (sr == "auto" && voice) "48000" else sr
    val chUse = if (ch == "auto" && voice) "1" else ch
    // Opus only supports a few sample rates; let the encoder pick when asked for another.
    if (srUse != "auto" && !(fmt.codec == "libopus" && srUse.toInt() !in setOf(8000, 12000, 16000, 24000, 48000))) a += "-ar $srUse"
    if (chUse != "auto") a += "-ac $chUse"
    if (comp != "auto" && (fmt.key == "flac" || fmt.key == "mp3")) a += "-compression_level $comp"
    return a.joinToString(" ")
}

internal val audioPresets = listOf(
    Preset("hq", "High quality music", "Best sound quality for music", mapOf("afmt" to "mp3", "br" to "cbr:320", "sr" to "auto", "ch" to "auto")),
    Preset("small", "Smaller music file", "Good quality with a smaller file", mapOf("afmt" to "mp3", "br" to "vbr:4", "sr" to "auto", "ch" to "auto")),
    Preset("compat", "Maximum compatibility", "Works on almost any device", mapOf("afmt" to "mp3", "br" to "cbr:192", "sr" to "44100", "ch" to "2")),
    Preset("car", "Car stereo", "Compatible with most car audio systems", mapOf("afmt" to "mp3", "br" to "cbr:192", "sr" to "44100", "ch" to "2")),
    Preset("podcast", "Podcast / voice", "Optimized for spoken voice", mapOf("afmt" to "m4a", "br" to "cbr:64", "sr" to "44100", "ch" to "1")),
    Preset("audiobook", "Audiobook", "Optimized for long spoken audio", mapOf("afmt" to "m4b", "br" to "cbr:64", "sr" to "22050", "ch" to "1")),
    Preset("voice", "Voice recording", "Ideal for meetings and notes", mapOf("afmt" to "m4a", "br" to "cbr:96", "sr" to "44100", "ch" to "1")),
    Preset("whatsapp", "WhatsApp voice", "Small files for voice sharing", mapOf("afmt" to "opus", "br" to "cbr:32", "sr" to "16000", "ch" to "1")),
    Preset("telegram", "Telegram voice message", "Mono Opus in OGG, plays as a voice note", mapOf("afmt" to "tgvoice", "br" to "cbr:32", "sr" to "48000", "ch" to "1")),
    Preset("tiny", "Ultra small file", "Minimize file size", mapOf("afmt" to "opus", "br" to "cbr:24", "sr" to "24000", "ch" to "1")),
    Preset("ring", "Ringtone", "Ideal for phone ringtones", mapOf("afmt" to "m4a", "br" to "cbr:128", "sr" to "44100", "ch" to "2")),
    Preset("lossless", "Lossless archive", "Preserves the original audio quality", mapOf("afmt" to "flac", "sr" to "auto", "ch" to "auto")),
)

// ---------------------------------------------------------------- video

internal class VFmt(val key: String, val label: String, val desc: String?, val ext: String, val kind: Kind) {
    enum class Kind { Hw264, HwHevc, Mpeg4, Vp9, Vp8, Wmv, Mpeg2, Gif, Webp, Apng, FramesJpg, FramesPng, Remux, LivePair, MotionPhoto }
}

internal val videoTable = listOf(
    VFmt("mp4_h264", "MP4 · H.264", "Plays everywhere. Hardware encoder.", "mp4", VFmt.Kind.Hw264),
    VFmt("mp4_hevc", "MP4 · HEVC (H.265)", "Smaller files. Hardware encoder.", "mp4", VFmt.Kind.HwHevc),
    VFmt("mp4_mpeg4", "MP4 · MPEG-4", "Software encoder, works on any device", "mp4", VFmt.Kind.Mpeg4),
    VFmt("mkv_h264", "MKV · H.264", null, "mkv", VFmt.Kind.Hw264),
    VFmt("mkv_hevc", "MKV · HEVC", null, "mkv", VFmt.Kind.HwHevc),
    VFmt("mov_h264", "MOV · H.264", "QuickTime container", "mov", VFmt.Kind.Hw264),
    VFmt("webm_vp9", "WebM · VP9", "Open format, great for the web", "webm", VFmt.Kind.Vp9),
    VFmt("webm_vp8", "WebM · VP8", null, "webm", VFmt.Kind.Vp8),
    VFmt("avi_mpeg4", "AVI · MPEG-4", "Old players and TVs", "avi", VFmt.Kind.Mpeg4),
    VFmt("mkv_vp9", "MKV · VP9", null, "mkv", VFmt.Kind.Vp9),
    VFmt("m4v_h264", "M4V · H.264", "Apple video", "m4v", VFmt.Kind.Hw264),
    VFmt("3gp_h264", "3GP · H.264", "Old phones", "3gp", VFmt.Kind.Hw264),
    VFmt("ts_h264", "TS · H.264", "MPEG transport stream", "ts", VFmt.Kind.Hw264),
    VFmt("flv_h264", "FLV · H.264", "Flash video", "flv", VFmt.Kind.Hw264),
    VFmt("wmv", "WMV", "Windows Media Video", "wmv", VFmt.Kind.Wmv),
    VFmt("mpg_mpeg2", "MPG · MPEG-2", "DVD-style video", "mpg", VFmt.Kind.Mpeg2),
    VFmt("motion_photo", "Motion Photo (JPG with video)", "One file for Google Photos and Android galleries", "jpg", VFmt.Kind.MotionPhoto),
    VFmt("live_pair", "Live Photo (JPG + MOV)", "A still and a clip with the same name", "mov", VFmt.Kind.LivePair),
    VFmt("gif", "GIF animation", null, "gif", VFmt.Kind.Gif),
    VFmt("webp", "Animated WebP", null, "webp", VFmt.Kind.Webp),
    VFmt("apng", "Animated PNG", null, "png", VFmt.Kind.Apng),
    VFmt("frames_jpg", "Images · JPG", "One picture per frame or interval", "jpg", VFmt.Kind.FramesJpg),
    VFmt("frames_png", "Images · PNG", null, "png", VFmt.Kind.FramesPng),
    VFmt("remux_mp4", "Remux to MP4", "Change the container without re-encoding", "mp4", VFmt.Kind.Remux),
    VFmt("remux_mkv", "Remux to MKV", "Fast and lossless", "mkv", VFmt.Kind.Remux),
    VFmt("remux_mov", "Remux to MOV", null, "mov", VFmt.Kind.Remux),
)

internal fun videoFmt(key: String) = videoTable.firstOrNull { it.key == key } ?: videoTable.first()
internal val videoItems = videoTable.map { Item(it.key, it.label, it.desc) }

internal val resItems = listOf(Item("0", "Original size")) +
    listOf(2160, 1440, 1080, 720, 480, 360, 240).map { Item("$it", "${it}p") }
internal val fpsItems = listOf(Item("0", "Original")) + listOf(60, 30, 25, 24, 15, 10).map { Item("$it", "$it fps") }
internal val qualityItems = listOf(
    Item("auto", "Balanced"), Item("high", "High quality", "Larger file"),
    Item("low", "Small file"), Item("tiny", "Tiny file", "Lowest quality"),
)
internal val vAudioItems = listOf(
    Item("keep", "Keep audio (192 kb/s)"), Item("128", "Compress audio (128 kb/s)"),
    Item("96", "Compress audio (96 kb/s)"), Item("mute", "Remove audio"),
)

internal val videoPresets = listOf(
    Preset("orig", "Original quality", "Keep size, high quality", mapOf("vfmt" to "mp4_h264", "res" to "0", "fps" to "0", "vq" to "high", "vaud" to "keep", "target" to "")),
    Preset("small", "Smaller file", "720p, compact", mapOf("vfmt" to "mp4_h264", "res" to "720", "fps" to "0", "vq" to "low", "vaud" to "128", "target" to "")),
    Preset("whatsapp", "WhatsApp", "720p, fits chat limits", mapOf("vfmt" to "mp4_h264", "res" to "720", "fps" to "30", "vq" to "low", "vaud" to "128", "target" to "")),
    Preset("discord", "Discord (8 MB)", "Squeezed to about 8 MB", mapOf("vfmt" to "mp4_h264", "res" to "720", "fps" to "30", "vq" to "auto", "vaud" to "96", "target" to "7.5")),
    Preset("tiktok", "TikTok", "1080p, 30 fps", mapOf("vfmt" to "mp4_h264", "res" to "1080", "fps" to "30", "vq" to "high", "vaud" to "keep", "target" to "")),
    Preset("youtube", "YouTube", "1080p, high quality", mapOf("vfmt" to "mp4_h264", "res" to "1080", "fps" to "0", "vq" to "high", "vaud" to "keep", "target" to "")),
    Preset("instagram", "Instagram", "1080p, 30 fps", mapOf("vfmt" to "mp4_h264", "res" to "1080", "fps" to "30", "vq" to "auto", "vaud" to "keep", "target" to "")),
    Preset("telegram", "Telegram", "720p balanced", mapOf("vfmt" to "mp4_h264", "res" to "720", "fps" to "0", "vq" to "auto", "vaud" to "128", "target" to "")),
    Preset("gif", "Quick GIF", "12 fps, 480 px", mapOf("vfmt" to "gif", "res" to "480", "fps" to "12", "target" to "")),
    Preset("web", "Web video (WebM)", "VP9 for websites", mapOf("vfmt" to "webm_vp9", "res" to "0", "fps" to "0", "vq" to "auto", "vaud" to "128", "target" to "")),
)

// ---------------------------------------------------------------- image

internal val imageTargets = listOf(
    Item("jpg", "JPG"), Item("png", "PNG"), Item("webp", "WebP"),
    Item("bmp", "BMP"), Item("tiff", "TIFF"), Item("gif", "GIF"),
    Item("jp2", "JPEG 2000"), Item("tga", "TGA"), Item("ppm", "PPM"), Item("ico", "ICO", "Scaled to fit 256 px"), Item("qoi", "QOI"),
    Item("live_mp4", "Live Photo to video (MP4)", "Pick a Live or Motion Photo"),
    Item("live_gif", "Live Photo to GIF", "Pick a Live or Motion Photo"),
    Item("live_webp", "Live Photo to animated WebP", "Pick a Live or Motion Photo"),
)
internal val maxWidthItems = listOf(Item("0", "Original size")) + listOf(3840, 2560, 1920, 1280, 1024, 800, 640).map { Item("$it", "$it px wide") }

// ---------------------------------------------------------------- tags

internal val tagKeys = listOf(
    "title" to "Title", "artist" to "Artist", "album_artist" to "Album artist", "album" to "Album",
    "track" to "Track number", "disc" to "Disc number", "date" to "Year", "composer" to "Composer",
    "language" to "Language", "encoder" to "Encoder", "comment" to "Comment", "genre" to "Genre",
)

internal val genres = listOf(
    "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age",
    "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
    "Soundtrack", "Ambient", "Trip-Hop", "Vocal", "Fusion", "Trance", "Classical", "Instrumental", "House", "Game",
    "Gospel", "Soul", "Punk", "Folk", "Latin", "Electronic", "Podcast", "Audiobook", "Speech",
)

/** `-metadata "key=value"` arguments for every tag the user filled in. */
internal fun tagArgs(r: ToolRequest, f: PickedFile?): List<String> = buildList {
    for ((k, _) in tagKeys) {
        var v = cleanTag(r.str("tag.$k"))
        if (k == "title" && r.bool("tag.useFilename") && f != null) v = cleanTag(f.name.substringBeforeLast('.'))
        if (v.isNotEmpty()) add("-metadata ${q("$k=$v")}")
    }
}

internal fun hasTags(r: ToolRequest) = r.bool("tag.useFilename") || tagKeys.any { (k, _) -> r.str("tag.$k").isNotBlank() }

// ---------------------------------------------------------------- range

internal class Trim(val start: Double, val end: Double, val remove: Boolean)

/** The chosen start/end, or null when the whole file is selected. */
internal fun trimOf(r: ToolRequest, id: String = "range", duration: Double? = null): Trim? {
    val parts = r.str(id).split(",")
    if (parts.size != 2) return null
    val s = parts[0].toDoubleOrNull() ?: return null
    val e = parts[1].toDoubleOrNull() ?: return null
    if (e <= s) throw IllegalArgumentException("The end of the selection must come after the start.")
    val full = s <= 0.05 && (duration != null && e >= duration - 0.05)
    if (full) return null
    return Trim(s, e, r.str("$id.mode") == "remove")
}

/** Pre-input seek arguments for a "keep this part" selection. */
internal fun keepArgs(t: Trim?): String =
    if (t != null && !t.remove) "-ss ${num(t.start)} -to ${num(t.end)} " else ""

internal fun fileExt(f: File) = f.extension

/** How long the video inside a Live or Motion Photo is. 0 = the whole video. */
internal val liveLengthItems = listOf(
    Item("3", "3 seconds", "Like a phone camera"),
    Item("5", "5 seconds"), Item("10", "10 seconds"), Item("15", "15 seconds"), Item("30", "30 seconds"),
    Item("60", "1 minute"), Item("120", "2 minutes"), Item("300", "5 minutes"), Item("600", "10 minutes"),
    Item("1200", "20 minutes"), Item("1800", "30 minutes"), Item("3600", "1 hour"), Item("7200", "2 hours"),
    Item("0", "Whole video", "No limit. Use the start and end sliders to pick the part."),
)
