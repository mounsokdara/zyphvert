package com.mounsokdara.zyphvert.tools

import java.io.File
import kotlin.math.max

private fun fail(msg: String): Nothing = throw IllegalArgumentException(msg)
private fun items(vararg p: Pair<String, String>) = p.map { Item(it.first, it.second) }
private val photoExts = setOf("jpg", "jpeg", "png", "webp")

private fun atempoChain(s: Double): String = when {
    s < 0.5 -> "atempo=0.5,atempo=${num(s / 0.5)}"
    s > 2.0 -> "atempo=2.0,atempo=${num(s / 2.0)}"
    else -> "atempo=${num(s)}"
}

private fun isVideoStream(r: ToolRequest, f: PickedFile): Boolean {
    if (isAudioName(f.name)) return false
    return streamsOf(r.ctx, f.uri)?.first ?: true
}

/** Hardware H.264 encode with AAC audio: the default for edits that must re-encode. */
private const val H264 = "-c:v h264_mediacodec -b:v 6M -c:a aac -b:a 192k"

/** Same as [H264] but sized from the source file, so edits don't make the video bigger than it was. */
private fun smartH264(r: ToolRequest, f: PickedFile?): String {
    val info = f?.let { videoInfo(r.ctx, it.uri) } ?: return H264
    return "-c:v h264_mediacodec -b:v ${hwVideoKbps(info, 0, 0, "auto", false, 6000)}k -c:a aac -b:a ${audioKbpsFor(info)}k"
}

// ------------------------------------------------------------------ audio

private fun audioJobs(r: ToolRequest, files: List<PickedFile>, cover: PickedFile?, fmtKey: String): List<ToolJob> {
    val fmt = audioFmt(fmtKey)
    val br = r.str("br").ifEmpty { "auto" }
    val sr = r.str("sr").ifEmpty { "auto" }
    val ch = r.str("ch").ifEmpty { "auto" }
    val comp = r.str("comp").ifEmpty { "auto" }
    return files.map { f ->
        val dur = durationSec(r.ctx, f.uri)
        val trim = trimOf(r, "range", dur)
        val out = r.out("Audio", f, fmt.ext)
        val useCover = cover != null && fmt.cover
        val af = mutableListOf<String>()
        if (r.bool("norm")) af += "loudnorm=I=-16:TP=-1.5:LRA=11"
        if (r.bool("fade")) {
            val len = if (trim != null && !trim.remove) trim.end - trim.start else dur
            if (len != null && len > 2.5) { af += "afade=t=in:st=0:d=1"; af += "afade=t=out:st=${num(len - 1)}:d=1" }
        }
        val inputs = keepArgs(trim) + "-i ${r.saf(f)}" + (if (useCover) " -i ${r.saf(cover!!)}" else "")
        val select = if (trim != null && trim.remove) {
            val g = StringBuilder()
            val s = trim.start; val e = trim.end
            if (s < 0.05) g.append("[0:a]atrim=start=${num(e)},asetpts=PTS-STARTPTS")
            else if (dur != null && e >= dur - 0.05) g.append("[0:a]atrim=0:${num(s)},asetpts=PTS-STARTPTS")
            else g.append("[0:a]atrim=0:${num(s)},asetpts=PTS-STARTPTS[a0];[0:a]atrim=start=${num(e)},asetpts=PTS-STARTPTS[a1];[a0][a1]concat=n=2:v=0:a=1")
            if (af.isNotEmpty()) g.append(",").append(af.joinToString(","))
            g.append("[a]")
            "-filter_complex ${q(g.toString())} -map ${q("[a]")}"
        } else {
            (if (af.isNotEmpty()) "-af ${q(af.joinToString(","))} " else "") + "-map 0:a:0"
        }
        val coverArgs = if (useCover) {
            val ce = nameExt(cover!!.name)
            val enc = if (ce in setOf("jpg", "jpeg", "png")) "-c:v copy" else "-c:v mjpeg -pix_fmt yuvj420p -q:v 2"
            val id3 = if (fmt.key == "mp3") " -id3v2_version 3 -metadata:s:v title=${q("Album cover")} -metadata:s:v comment=${q("Cover (front)")}" else ""
            " -map 1:v $enc -disposition:v:0 attached_pic$id3"
        } else ""
        val tags = tagArgs(r, f).joinToString(" ")
        val cmd = "$inputs $select$coverArgs ${audioCodecArgs(fmt, br, sr, ch, comp)} ${fmt.mux} -map_metadata 0 $tags ${q(out.path)}"
        r.job(f, out, cmd)
    }
}

// ------------------------------------------------------------------ video

private fun videoKind(v: Map<String, String>) = videoFmt(v["vfmt"] ?: "mp4_h264").kind

private fun videoJobs(r: ToolRequest, files: List<PickedFile>, fmtKey: String): List<ToolJob> {
    val fmt = videoFmt(fmtKey)
    val h = r.int("res")
    val fps = r.int("fps")
    val qual = r.str("vq").ifEmpty { "auto" }
    val aKey = r.str("vaud").ifEmpty { "keep" }
    val hwK = when (qual) { "high" -> 10000; "low" -> 2500; "tiny" -> 1000; else -> 6000 }
    // Live and Motion Photo length: 0 means the whole video, with no limit.
    val liveSecs = r.str("llen").ifEmpty { "0" }.toIntOrNull() ?: 0
    val liveLen = if (liveSecs > 0) " -t $liveSecs" else ""
    return files.map { f ->
        val dur = durationSec(r.ctx, f.uri)
        val trim = trimOf(r, "range", dur)
        val pre = keepArgs(trim)
        val len = trim?.let { it.end - it.start } ?: dur
        val inp = "$pre-i ${r.saf(f)}"
        val scale = if (h > 0) "scale=-2:$h" else null
        // Size the output from the source: no upscaling, no frame-rate increase, bitrate that matches the picture.
        val info = videoInfo(r.ctx, f.uri)
        val vScale = if (h > 0 && (info == null || h < info.height)) "scale=-2:$h" else null
        val srcFps = info?.fps
        val vFps = if (fps > 0 && (srcFps == null || fps < srcFps - 0.5f)) fps else 0
        val keepAudio = aKey == "keep"
        val abr = when (aKey) { "128" -> 128; "96" -> 96; else -> audioKbpsFor(info) }
        val liveK = hwVideoKbps(info, h, fps, qual, false, hwK)
        when (fmt.kind) {
            VFmt.Kind.Remux -> {
                val out = r.out("Video", f, fmt.ext)
                r.job(f, out, "$inp -map ${q("0:v?")} -map ${q("0:a?")} -c copy ${q(out.path)}")
            }
            VFmt.Kind.LivePair -> {
                // iPhone-style pair: Zyphvert/Live Photos/<name>.mov next to <name>.jpg. Length comes from the "Live Photo length" option.
                val out = r.out("Video", f, "mov", dir = OutputPaths.dir(r.ctx, "Live Photos"))
                val id = java.util.UUID.randomUUID().toString().uppercase()
                val vf = listOfNotNull(vScale, if (vFps > 0) "fps=$vFps" else null, VID_TAIL).joinToString(",")
                r.job(f, out, "$inp$liveLen -vf ${q(vf)} -c:v h264_mediacodec -b:v ${liveK}k -c:a aac -b:a 128k " +
                    "-movflags use_metadata_tags -metadata com.apple.quicktime.content.identifier=$id -f mov ${q(out.path)}")
            }
            VFmt.Kind.MotionPhoto -> {
                // One FFmpeg run makes the clip and the still; then both are joined into a single Motion Photo JPG.
                val out = r.out("Images", f, "jpg", dir = OutputPaths.dir(r.ctx, "Live Photos"))
                val tmpClip = File(r.ctx.cacheDir, "mp_${out.nameWithoutExtension}.mp4").also { it.delete() }
                val tmpStill = File(r.ctx.cacheDir, "mp_${out.nameWithoutExtension}.jpg").also { it.delete() }
                val vf = listOfNotNull(vScale, if (vFps > 0) "fps=$vFps" else null, VID_TAIL).joinToString(",")
                ToolJob(r.toolTitle, f.name, out.path,
                    "$inp$liveLen -vf ${q(vf)} -c:v h264_mediacodec -b:v ${liveK}k -c:a aac -b:a 128k -movflags +faststart ${q(tmpClip.path)} " +
                        "-frames:v 1 -q:v 2 ${q(tmpStill.path)}",
                    after = {
                        try { buildMotionPhoto(tmpStill, tmpClip, out) } finally { tmpClip.delete(); tmpStill.delete() }
                    })
            }
            VFmt.Kind.Gif -> {
                val out = r.out("Images", f, "gif")
                val vf = "fps=${if (fps > 0) fps else 12},${scale?.replace("scale=-2", "scale=-1") ?: "scale=480:-1"}:flags=lanczos," +
                    "split[s0][s1];[s0]palettegen=stats_mode=diff[p];[s1][p]paletteuse=dither=bayer:bayer_scale=5"
                r.job(f, out, "$inp -vf ${q(vf)} -loop 0 -an ${q(out.path)}")
            }
            VFmt.Kind.Webp -> {
                val out = r.out("Images", f, "webp")
                val vf = "fps=${if (fps > 0) fps else 12},${scale ?: "scale=480:-2"}:flags=lanczos"
                r.job(f, out, "$inp -vf ${q(vf)} -c:v libwebp -q:v 70 -loop 0 -an ${q(out.path)}")
            }
            VFmt.Kind.Apng -> {
                val out = r.out("Images", f, "png")
                val vf = "fps=${if (fps > 0) fps else 12},${scale ?: "scale=480:-2"}:flags=lanczos"
                r.job(f, out, "$inp -vf ${q(vf)} -f apng -plays 0 -an ${q(out.path)}")
            }
            VFmt.Kind.FramesJpg, VFmt.Kind.FramesPng -> {
                val ext = fmt.ext
                val folder = OutputPaths.uniqueDir(OutputPaths.dir(r.ctx, "Images"), baseName(f.name) + "_frames")
                val vf = listOfNotNull(if (fps > 0) "fps=$fps" else null, scale).joinToString(",")
                val every = if (fps == 0) "-fps_mode passthrough " else ""
                val qa = if (ext == "jpg") "-q:v 2 " else ""
                ToolJob(r.toolTitle, f.name, folder.path,
                    "$inp ${if (vf.isNotEmpty()) "-vf ${q(vf)} " else ""}$every$qa${q("${folder.path}/frame_%04d.$ext")}")
            }
            else -> {
                val out = r.out("Video", f, fmt.ext)
                val vf = listOfNotNull(vScale, if (vFps > 0) "fps=$vFps" else null, VID_TAIL).joinToString(",")
                val target = r.str("target").toDoubleOrNull()
                val audioKbps = if (aKey == "mute") 0 else abr
                val vk: Int? = if (target != null && len != null && len > 0)
                    max(100, (target * 8192 / len - audioKbps).toInt()) else null
                val hw = hwVideoKbps(info, h, fps, qual, false, hwK)
                val hwHevc = hwVideoKbps(info, h, fps, qual, true, hwK * 6 / 10)
                val mq = when (qual) { "high" -> 2; "low" -> 9; "tiny" -> 15; else -> 4 }
                val crf = when (qual) { "high" -> 28; "low" -> 40; "tiny" -> 48; else -> 34 }
                val v = when (fmt.kind) {
                    VFmt.Kind.HwHevc -> "-c:v hevc_mediacodec -b:v ${vk ?: hwHevc}k"
                    VFmt.Kind.Wmv -> "-c:v wmv2 " + (if (vk != null) "-b:v ${vk}k" else "-q:v $mq")
                    VFmt.Kind.Mpeg2 -> "-c:v mpeg2video " + (if (vk != null) "-b:v ${vk}k" else "-q:v $mq")
                    VFmt.Kind.Mpeg4 -> "-c:v mpeg4 " + (if (vk != null) "-b:v ${vk}k" else "-q:v $mq")
                    VFmt.Kind.Vp9 -> "-c:v libvpx-vp9 -deadline realtime -cpu-used 8 -row-mt 1 " + (if (vk != null) "-b:v ${vk}k" else "-crf $crf -b:v 0")
                    VFmt.Kind.Vp8 -> "-c:v libvpx -deadline realtime -cpu-used 8 " + (if (vk != null) "-b:v ${vk}k" else "-crf ${crf / 3 + 4} -b:v 2M")
                    else -> "-c:v h264_mediacodec -b:v ${vk ?: hw}k"
                }
                // The source's own AAC track is copied as it is: no quality loss and no extra size.
                val copyAudio = keepAudio && info?.audioMime == AAC_MIME
                val a = when {
                    aKey == "mute" -> "-an"
                    fmt.ext == "webm" || (fmt.ext == "mkv" && fmt.kind == VFmt.Kind.Vp9) -> "-c:a libopus -b:a ${abr}k"
                    fmt.ext == "avi" -> "-c:a libmp3lame -b:a ${abr}k"
                    fmt.ext == "wmv" -> "-c:a wmav2 -b:a ${abr}k"
                    fmt.ext == "mpg" -> "-c:a mp2 -b:a ${abr}k"
                    copyAudio -> "-c:a copy"
                    else -> "-c:a aac -b:a ${abr}k"
                }
                // H.264 in, H.264 out, nothing to change: re-encoding can only make it bigger or worse. Copy the video as it is.
                val nothingToChange = fmt.kind == VFmt.Kind.Hw264 && info?.videoMime == AVC_MIME && trim == null &&
                    vScale == null && vFps == 0 && target == null && qual in setOf("auto", "high")
                if (nothingToChange) {
                    r.job(f, out, "$inp -map ${q("0:v:0")} -map ${q("0:a:0?")} -dn -c:v copy $a ${q(out.path)}")
                } else {
                    r.job(f, out, "$inp -vf ${q(vf)} $v $a -dn ${q(out.path)}")
                }
            }
        }
    }
}

val toolSpecs: Map<String, ToolSpec> = listOf(

    // ================================================================ CONVERT
    ToolSpec(
        id = "video",
        intro = "Convert any video to any format: MP4, MKV, WebM, MOV, AVI, WMV, GIF, images, even a Live Photo pair and more. Pick a preset for WhatsApp, Discord, TikTok or YouTube, or tune every setting yourself.",
        inputs = listOf(InputSlot("media", "Videos", InKind.Video, multiple = true)),
        options = listOf(
            Opt.Name(),
            Opt.Presets("preset", "Quick preset", videoPresets),
            Opt.Choice("vfmt", "Format", videoItems),
            Opt.Choice("llen", "Live Photo length", liveLengthItems, showIf = { videoKind(it) in setOf(VFmt.Kind.LivePair, VFmt.Kind.MotionPhoto) }),
            Opt.Choice("res", "Resolution", resItems),
            Opt.Choice("fps", "Frame rate", fpsItems),
            Opt.Choice("vq", "Quality", qualityItems, showIf = { videoKind(it) in setOf(VFmt.Kind.Hw264, VFmt.Kind.HwHevc, VFmt.Kind.Mpeg4, VFmt.Kind.Vp9, VFmt.Kind.Vp8, VFmt.Kind.Wmv, VFmt.Kind.Mpeg2, VFmt.Kind.LivePair, VFmt.Kind.MotionPhoto) }),
            Opt.Choice("vaud", "Audio", vAudioItems, showIf = { videoKind(it) in setOf(VFmt.Kind.Hw264, VFmt.Kind.HwHevc, VFmt.Kind.Mpeg4, VFmt.Kind.Vp9, VFmt.Kind.Vp8, VFmt.Kind.Wmv, VFmt.Kind.Mpeg2, VFmt.Kind.LivePair, VFmt.Kind.MotionPhoto) }),
            Opt.Range("range", "Start time / End time"),
            Opt.Text("target", "Target file size (MB)", "Empty = no limit", showIf = { videoKind(it) in setOf(VFmt.Kind.Hw264, VFmt.Kind.HwHevc, VFmt.Kind.Mpeg4, VFmt.Kind.Vp9, VFmt.Kind.Vp8, VFmt.Kind.Wmv, VFmt.Kind.Mpeg2, VFmt.Kind.LivePair, VFmt.Kind.MotionPhoto) }, advanced = true),
        ),
        build = { r ->
            val key = r.str("vfmt").ifEmpty { "mp4_h264" }
            val files = r.files("media")
            val jobs = videoJobs(r, files, key)
            if (videoFmt(key).kind != VFmt.Kind.LivePair) jobs
            else jobs.flatMapIndexed { i, j ->
                // the matching still, saved beside the clip so the two stay a pair
                val mov = File(j.output!!)
                val still = File(mov.parentFile, mov.nameWithoutExtension + ".jpg")
                listOf(j, ToolJob(r.toolTitle, files[i].name, still.path, "-i ${r.saf(files[i])} -frames:v 1 -q:v 2 ${q(still.path)}"))
            }
        },
    ),

    ToolSpec(
        id = "audio",
        intro = "Convert any audio or video to any audio format: MP3, AAC, M4A, FLAC, WAV, Opus, OGG and more. Choose a preset, trim the part you want, edit tags and add cover art.",
        inputs = listOf(
            InputSlot("media", "Audio or video files", InKind.Media, multiple = true),
            InputSlot("cover", "Cover artwork", InKind.Image, optional = true),
        ),
        options = listOf(
            Opt.Name(),
            Opt.Presets("preset", "Conversion template", audioPresets),
            Opt.Choice("afmt", "Format", audioItems),
            Opt.Choice("br", "Bitrate (kbps/channel)", bitrateItems, showIf = { it["afmt"] in lossyKeys }),
            Opt.Choice("sr", "Sample rate", sampleRateItems),
            Opt.Choice("ch", "Channels", channelItems),
            Opt.Range("range", "Start time / End time", modes = true),
            Opt.Tags(),
            Opt.Toggle("norm", "Normalize volume", false, "Even out loud and quiet parts", advanced = true),
            Opt.Toggle("fade", "Fade in and out", false, "One second at each end", advanced = true),
            Opt.Choice("comp", "Compression level", compressionItems, showIf = { it["afmt"] == "flac" || it["afmt"] == "mp3" }, advanced = true),
        ),
        build = { r -> audioJobs(r, r.files("media"), r.file("cover"), r.str("afmt").ifEmpty { "mp3" }) },
    ),

    ToolSpec(
        id = "image",
        intro = "Convert any image to JPG, PNG, WebP, BMP, TIFF, GIF and more. Handles HEIC, HEIF, AVIF and everything else your phone can open. Live Photos can become a video, GIF or animated WebP. Resize while you convert.",
        hint = "For a Live Photo, pick the picture that has its MOV next to it, or an Android Motion Photo.",
        inputs = listOf(InputSlot("media", "Images", InKind.Image, multiple = true)),
        options = listOf(
            Opt.Name(),
            Opt.Choice("ifmt", "Convert to", imageTargets),
            Opt.Slider("iq", "Quality", 1, 100, 90, "%", showIf = { it["ifmt"] == "jpg" || it["ifmt"] == "webp" }),
            Opt.Choice("iw", "Resize", maxWidthItems),
            Opt.Toggle("istrip", "Remove metadata (EXIF, GPS)", false, advanced = true),
        ),
        build = { r ->
            val fmt = r.str("ifmt").ifEmpty { "jpg" }
            val qual = r.int("iq").coerceIn(1, 100)
            val w = r.int("iw")
            r.files("media").map { f ->
                if (fmt.startsWith("live_")) {
                    val src = r.saf(resolveLive(r.ctx, f))
                    return@map when (fmt) {
                        "live_gif" -> {
                            val out = r.out("Images", f, "gif")
                            val g = "fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                            r.job(f, out, "-i $src -vf ${q(g)} -loop 0 -an ${q(out.path)}")
                        }
                        "live_webp" -> {
                            val out = r.out("Images", f, "webp")
                            r.job(f, out, "-i $src -vf ${q("fps=15,scale=480:-2:flags=lanczos")} -c:v libwebp -q:v 70 -loop 0 -an ${q(out.path)}")
                        }
                        else -> {
                            val out = r.out("Video", f, "mp4")
                            r.job(f, out, "-i $src -c copy ${q(out.path)}")
                        }
                    }
                }
                val ext = if (fmt == "tiff") "tif" else fmt
                val out = r.out("Images", f, ext)
                val qv = when (fmt) { "jpg" -> "-q:v ${2 + (100 - qual) * 29 / 99} "; "webp" -> "-q:v $qual "; else -> "" }
                val vf = when {
                    fmt == "ico" -> "-vf ${q("scale=w=256:h=256:force_original_aspect_ratio=decrease")} "
                    w > 0 -> "-vf ${q("scale=w=(iw+$w-abs(iw-$w))/2:h=-2")} "
                    else -> ""
                }
                val strip = if (r.bool("istrip")) "-map_metadata -1 " else ""
                val one = if (fmt in setOf("jpg", "png", "bmp", "tiff", "jp2", "tga", "ppm", "ico", "qoi")) "-frames:v 1 " else ""
                val cmd = "-i ${r.saf(f)} $vf$one$qv$strip${q(out.path)}"
                val nativeOk = fmt in setOf("jpg", "png", "webp") && w == 0
                ToolJob(r.toolTitle, f.name, out.path, cmd,
                    native = if (nativeOk) ({ convertImageNative(r.ctx, f.uri, out, fmt, qual) }) else null)
            }
        },
    ),

    // ================================================================ EDIT
    ToolSpec(
        id = "trim",
        intro = "Cut a video or audio file. Keep just the part you want, or remove a section from the middle. Fast mode doesn't re-encode, so there's no quality loss.",
        inputs = listOf(InputSlot("media", "Video or audio", InKind.Media)),
        options = listOf(
            Opt.Name(),
            Opt.Range("range", "Start time / End time", modes = true),
            Opt.Choice("tmode", "Mode", items("fast" to "Fast (no re-encode)", "precise" to "Precise (re-encode)")),
        ),
        actionLabel = "Cut",
        build = { r ->
            val f = r.file("media")!!
            val dur = durationSec(r.ctx, f.uri)
            val trim0 = trimOf(r, "range", dur) ?: fail("Move the sliders to choose what to keep or remove.")
            // Removing the very start or end is just a "keep" of the other side.
            val trim = when {
                trim0.remove && trim0.start < 0.05 && dur != null -> Trim(trim0.end, dur, false)
                trim0.remove && dur != null && trim0.end >= dur - 0.05 -> Trim(0.0, trim0.start, false)
                else -> trim0
            }
            val (hasV, hasA) = if (isAudioName(f.name)) false to true else streamsOf(r.ctx, f.uri) ?: (true to true)
            val ext = nameExt(f.name).ifEmpty { if (hasV) "mp4" else "m4a" }
            val cat = if (hasV) "Video" else "Audio"
            if (!trim.remove && r.str("tmode") == "fast") {
                val out = r.out(cat, f, ext, "_cut")
                listOf(r.job(f, out, "${keepArgs(trim)}-i ${r.saf(f)} -c copy -avoid_negative_ts make_zero ${q(out.path)}"))
            } else if (!trim.remove) {
                val out = r.out(cat, f, if (hasV) "mp4" else "m4a", "_cut")
                val enc = if (hasV) "-vf ${q(VID_TAIL)} ${smartH264(r, f)}" else "-vn -c:a aac -b:a 192k"
                listOf(r.job(f, out, "${keepArgs(trim)}-i ${r.saf(f)} $enc ${q(out.path)}"))
            } else {
                val s = num(trim.start); val e = num(trim.end)
                val g = StringBuilder()
                if (hasV) {
                    g.append("[0:v]trim=0:$s,setpts=PTS-STARTPTS[v0];[0:v]trim=start=$e,setpts=PTS-STARTPTS[v1];")
                    if (hasA) g.append("[0:a]atrim=0:$s,asetpts=PTS-STARTPTS[a0];[0:a]atrim=start=$e,asetpts=PTS-STARTPTS[a1];[v0][a0][v1][a1]concat=n=2:v=1:a=1[vc][a];")
                    else g.append("[v0][v1]concat=n=2:v=1:a=0[vc];")
                    g.append("[vc]$VID_TAIL[v]")
                } else {
                    g.append("[0:a]atrim=0:$s,asetpts=PTS-STARTPTS[a0];[0:a]atrim=start=$e,asetpts=PTS-STARTPTS[a1];[a0][a1]concat=n=2:v=0:a=1[a]")
                }
                val maps = (if (hasV) "-map ${q("[v]")} " else "") + (if (hasA) "-map ${q("[a]")} " else "")
                val out = r.out(cat, f, if (hasV) "mp4" else "m4a", "_cut")
                val enc = if (hasV) smartH264(r, f) else "-c:a aac -b:a 192k"
                listOf(r.job(f, out, "-i ${r.saf(f)} -filter_complex ${q(g.toString())} $maps$enc ${q(out.path)}"))
            }
        },
    ),

    ToolSpec(
        id = "crop",
        intro = "Crop to a ratio, rotate or flip videos and photos.",
        inputs = listOf(InputSlot("media", "Videos or photos", InKind.Visual, multiple = true)),
        options = listOf(
            Opt.Choice("ar", "Crop to", items("keep" to "Keep original", "1:1" to "Square 1:1", "16:9" to "Wide 16:9", "9:16" to "Vertical 9:16", "4:3" to "4:3", "3:4" to "3:4")),
            Opt.Choice("rot", "Rotate", items("0" to "None", "cw" to "90° right", "ccw" to "90° left", "180" to "180°")),
            Opt.Choice("flip", "Flip", items("none" to "None", "h" to "Horizontal", "v" to "Vertical")),
        ),
        actionLabel = "Apply",
        build = { r ->
            val parts = buildList {
                if (r.str("ar") != "keep") {
                    val (a, b) = r.str("ar").split(":").map { it.toInt() }
                    // min(x,y) written as (x+y-abs(x-y))/2 so the filter has no commas to escape
                    add("crop=w=(iw+ih*$a/$b-abs(iw-ih*$a/$b))/2:h=(ih+iw*$b/$a-abs(ih-iw*$b/$a))/2")
                }
                when (r.str("rot")) { "cw" -> add("transpose=1"); "ccw" -> add("transpose=2"); "180" -> { add("transpose=1"); add("transpose=1") } }
                when (r.str("flip")) { "h" -> add("hflip"); "v" -> add("vflip") }
            }
            if (parts.isEmpty()) fail("Choose a crop, rotation or flip first.")
            r.files("media").map { f ->
                if (isImageName(f.name)) {
                    val ext = nameExt(f.name).takeIf { it in photoExts } ?: "jpg"
                    val out = r.out("Images", f, ext, "_edit")
                    r.job(f, out, "-i ${r.saf(f)} -vf ${q(parts.joinToString(","))} -frames:v 1 -q:v 2 ${q(out.path)}")
                } else {
                    val out = r.out("Video", f, "mp4", "_edit")
                    r.job(f, out, "-i ${r.saf(f)} -vf ${q((parts + VID_TAIL).joinToString(","))} ${smartH264(r, f)} ${q(out.path)}")
                }
            }
        },
    ),

    ToolSpec(
        id = "speed",
        intro = "Make a video faster, slower or play it backwards. Audio pitch is kept natural.",
        inputs = listOf(InputSlot("media", "Video", InKind.Video)),
        options = listOf(
            Opt.Choice("s", "Speed", items("2" to "2× faster", "4" to "4× faster", "1.5" to "1.5× faster", "0.75" to "0.75× slower", "0.5" to "0.5× slower", "0.25" to "0.25× slower")),
            Opt.Toggle("rev", "Play in reverse", false, "Loads the whole clip into memory, so keep reversed clips short"),
        ),
        actionLabel = "Apply",
        build = { r ->
            val f = r.file("media")!!
            val s = r.str("s").toDouble()
            val rev = r.bool("rev")
            val vf = (if (rev) "reverse," else "") + "setpts=PTS/${num(s)},$VID_TAIL"
            val af = (if (rev) "areverse," else "") + atempoChain(s)
            val out = r.out("Video", f, "mp4", "_speed")
            listOf(r.job(f, out, "-i ${r.saf(f)} -vf ${q(vf)} -af ${q(af)} ${smartH264(r, f)} ${q(out.path)}"))
        },
    ),

    ToolSpec(
        id = "filters",
        intro = "Apply a colour or effect filter to videos and photos.",
        inputs = listOf(InputSlot("media", "Videos or photos", InKind.Visual, multiple = true)),
        options = listOf(
            Opt.Choice("f", "Filter", listOf(
                Item("eq=brightness=0.1:contrast=1.05", "Brighten"),
                Item("eq=contrast=1.3", "More contrast"),
                Item("eq=saturation=1.6:contrast=1.1", "Vivid"),
                Item("hue=s=0", "Black & white"),
                Item("colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131", "Sepia"),
                Item("colorbalance=bs=0.2:rs=-0.1", "Cool"),
                Item("colorbalance=rs=0.2:bs=-0.1", "Warm"),
                Item("gblur=sigma=6", "Blur"),
                Item("unsharp=5:5:1.2:5:5:0.0", "Sharpen"),
                Item("vignette=PI/4", "Vignette"),
                Item("hqdn3d=4:3:6:4.5", "Denoise"),
                Item("negate", "Negative"),
            )),
        ),
        actionLabel = "Apply filter",
        build = { r ->
            val flt = r.str("f")
            r.files("media").map { f ->
                if (isImageName(f.name)) {
                    val ext = nameExt(f.name).takeIf { it in photoExts } ?: "jpg"
                    val out = r.out("Images", f, ext, "_filter")
                    r.job(f, out, "-i ${r.saf(f)} -vf ${q(flt)} -frames:v 1 -q:v 2 ${q(out.path)}")
                } else {
                    val out = r.out("Video", f, "mp4", "_filter")
                    r.job(f, out, "-i ${r.saf(f)} -vf ${q("$flt,$VID_TAIL")} ${smartH264(r, f)} ${q(out.path)}")
                }
            }
        },
    ),

    // ================================================================ METADATA & PRIVACY
    ToolSpec(
        id = "meta",
        intro = "See everything stored in a file and change it: tags, dates, camera and location, and cover art. Pick one file or many; your changes apply to all of them.",
        inputs = listOf(InputSlot("media", "Files", InKind.Any, multiple = true)),
        options = listOf(Opt.Metadata()),
        actionLabel = "Save changes",
        build = { r -> buildMetadataJobs(r) },
    ),

    ToolSpec(
        id = "scrub",
        intro = "Remove GPS, device info, timestamps and every hidden tag before you share. No re-encoding, so quality is untouched. Originals stay as they are.",
        inputs = listOf(InputSlot("media", "Photos, videos or audio", InKind.Any, multiple = true)),
        options = listOf(Opt.Name()),
        actionLabel = "Remove metadata",
        build = { r ->
            r.files("media").map { f ->
                val ext = nameExt(f.name).ifEmpty { "bin" }
                val out = r.out("Clean", f, ext, "_clean")
                r.job(f, out, "-i ${r.saf(f)} -map 0 -map_metadata -1 -map_chapters -1 -c copy -fflags +bitexact -flags:v +bitexact -flags:a +bitexact ${q(out.path)}")
            }
        },
    ),

    // ================================================================ CREATE
    ToolSpec(
        id = "live",
        intro = "Get the video out of a Motion Photo or Live Photo, or turn it into a GIF or a still frame.",
        inputs = listOf(InputSlot("media", "Live Photo or Motion Photo", InKind.Visual)),
        options = listOf(
            Opt.Choice("action", "Action", items("video" to "Extract the video (MP4)", "gif" to "Make a GIF", "still" to "Grab a still frame (JPG)")),
        ),
        actionLabel = "Convert",
        hint = "Works with iPhone Live Photos (the picture with its MOV next to it), Android Motion Photos, and plain MOV or MP4 clips.",
        build = { r ->
            val f = r.file("media")!!
            val src = r.saf(resolveLive(r.ctx, f))
            when (r.str("action")) {
                "gif" -> {
                    val out = r.out("Images", f, "gif")
                    val g = "fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                    listOf(r.job(f, out, "-i $src -vf ${q(g)} -loop 0 -an ${q(out.path)}"))
                }
                "still" -> {
                    val out = r.out("Images", f, "jpg")
                    listOf(r.job(f, out, "-i $src -frames:v 1 -q:v 2 ${q(out.path)}"))
                }
                else -> {
                    val out = r.out("Video", f, "mp4")
                    listOf(r.job(f, out, "-i $src -c copy ${q(out.path)}"))
                }
            }
        },
    ),

    ToolSpec(
        id = "ringtone",
        intro = "Cut the best part of a song into a ringtone or notification sound, saved straight to your Ringtones folder.",
        inputs = listOf(InputSlot("media", "Song or video", InKind.Media)),
        options = listOf(
            Opt.Name(),
            Opt.Range("range", "Start time / End time"),
            Opt.Choice("rfmt", "Format", items("m4a" to "M4A (AAC)", "mp3" to "MP3", "ogg" to "OGG", "m4r" to "M4R (iPhone)")),
            Opt.Toggle("fade", "Fade in and out", true),
            Opt.Toggle("sys", "Save in the Ringtones folder", true),
        ),
        actionLabel = "Make ringtone",
        build = { r ->
            val f = r.file("media")!!
            val dur = durationSec(r.ctx, f.uri)
            val t = trimOf(r, "range", dur) ?: Trim(0.0, minOf(30.0, dur ?: 30.0), false)
            val len = t.end - t.start
            val a = audioFmt(r.str("rfmt").ifEmpty { "m4a" })
            val dir = if (r.bool("sys")) OutputPaths.ringtones(r.ctx) else OutputPaths.dir(r.ctx, "Audio")
            val out = r.out("Audio", f, a.ext, "_ring", dir = dir)
            val af = if (r.bool("fade") && len > 2.5) " -af ${q("afade=t=in:st=0:d=1,afade=t=out:st=${num(len - 1)}:d=1")}" else ""
            listOf(r.job(f, out, "-ss ${num(t.start)} -t ${num(len)} -i ${r.saf(f)} -vn$af ${audioCodecArgs(a, "auto", "auto", "auto", "auto")} ${a.mux} ${q(out.path)}"))
        },
    ),

    // ================================================================ POWER
    ToolSpec(
        id = "batch",
        intro = "Convert many files in one go. Pick the files, pick a target format, and every file becomes its own queued job.",
        inputs = listOf(InputSlot("media", "Files to convert", InKind.Any, multiple = true)),
        options = listOf(
            Opt.Choice("t", "Convert everything to", listOf(
                Item("mp4", "Video · MP4 (H.264)"), Item("webm", "Video · WebM (VP9)"), Item("gif", "Video · GIF"),
                Item("mp3", "Audio · MP3"), Item("m4a", "Audio · AAC (M4A)"), Item("flac", "Audio · FLAC"),
                Item("wav", "Audio · WAV"), Item("opus", "Audio · Opus"), Item("tgvoice", "Audio · Telegram voice (OGG)"),
                Item("jpg", "Image · JPG"), Item("png", "Image · PNG"), Item("webp", "Image · WebP"),
            )),
        ),
        actionLabel = "Add to queue",
        hint = "Pick files that match the target: videos for video targets, and so on.",
        build = { r ->
            val t = r.str("t").ifEmpty { "mp4" }
            r.files("media").map { f ->
                when (t) {
                    "jpg", "png", "webp" -> {
                        val out = r.out("Images", f, t)
                        ToolJob(r.toolTitle, f.name, out.path,
                            "-i ${r.saf(f)} -frames:v 1${if (t == "jpg") " -q:v 2" else ""} ${q(out.path)}",
                            native = { convertImageNative(r.ctx, f.uri, out, t, 92) })
                    }
                    "mp3", "m4a", "flac", "wav", "opus", "tgvoice" -> {
                        val a = audioFmt(t)
                        val out = r.out("Audio", f, a.ext)
                        r.job(f, out, "-i ${r.saf(f)} -vn -map 0:a:0 ${audioCodecArgs(a, "auto", "auto", "auto", "auto")} ${a.mux} ${q(out.path)}")
                    }
                    "gif" -> {
                        val out = r.out("Images", f, "gif")
                        val g = "fps=12,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                        r.job(f, out, "-i ${r.saf(f)} -vf ${q(g)} -loop 0 -an ${q(out.path)}")
                    }
                    else -> {
                        val out = r.out("Video", f, if (t == "webm") "webm" else "mp4")
                        val enc = if (t == "webm") "-c:v libvpx-vp9 -crf 34 -b:v 0 -deadline realtime -cpu-used 8 -row-mt 1 -c:a libopus -b:a 128k" else smartH264(r, f)
                        r.job(f, out, "-i ${r.saf(f)} -vf ${q(VID_TAIL)} $enc ${q(out.path)}")
                    }
                }
            }
        },
    ),
    // ================================================================ EXTRA EDIT TOOLS
    ToolSpec(
        id = "compress",
        intro = "Make videos, audio files and photos smaller before you share them. Pick how much to squeeze.",
        inputs = listOf(InputSlot("media", "Videos, audio or photos", InKind.Any, multiple = true)),
        options = listOf(
            Opt.Choice("level", "How much", listOf(
                Item("light", "Light", "Keep nearly the same quality"),
                Item("medium", "Medium", "Good balance of size and quality"),
                Item("strong", "Strong", "Smallest files, lower quality"),
            )),
        ),
        actionLabel = "Compress",
        build = { r ->
            val lvl = r.str("level").ifEmpty { "medium" }
            r.files("media").map { f ->
                when {
                    isImageName(f.name) -> {
                        val qv = when (lvl) { "light" -> 4; "strong" -> 14; else -> 8 }
                        val w = when (lvl) { "light" -> 2560; "strong" -> 1280; else -> 1920 }
                        val out = r.out("Images", f, "jpg", "_small")
                        r.job(f, out, "-i ${r.saf(f)} -vf ${q("scale=w=(iw+$w-abs(iw-$w))/2:h=-2")} -frames:v 1 -q:v $qv ${q(out.path)}")
                    }
                    isVideoStream(r, f) -> {
                        val (br0, h, ab0) = when (lvl) { "light" -> Triple(6000, 0, 160); "strong" -> Triple(1200, 480, 96); else -> Triple(2800, 720, 128) }
                        val info = videoInfo(r.ctx, f.uri)
                        // A "smaller" file must never end up bigger: stay under the source's own bitrate and size.
                        val br = info?.videoKbps?.let { minOf(br0, maxOf(300, (it * 0.9).toInt())) } ?: br0
                        val ab = info?.audioKbps?.let { minOf(ab0, maxOf(64, it)) } ?: ab0
                        val vf = listOfNotNull(if (h > 0 && (info == null || h < info.height)) "scale=-2:$h" else null, VID_TAIL).joinToString(",")
                        val out = r.out("Video", f, "mp4", "_small")
                        r.job(f, out, "-i ${r.saf(f)} -vf ${q(vf)} -c:v h264_mediacodec -b:v ${br}k -c:a aac -b:a ${ab}k -dn ${q(out.path)}")
                    }
                    else -> {
                        val ab = when (lvl) { "light" -> 160; "strong" -> 64; else -> 96 }
                        val out = r.out("Audio", f, "m4a", "_small")
                        r.job(f, out, "-i ${r.saf(f)} -vn -map 0:a:0 -c:a aac -b:a ${ab}k ${q(out.path)}")
                    }
                }
            }
        },
    ),

    ToolSpec(
        id = "volume",
        intro = "Make a video or audio clip louder, quieter, evened out or silent. Video quality isn't touched.",
        inputs = listOf(InputSlot("media", "Video or audio", InKind.Media, multiple = true)),
        options = listOf(
            Opt.Name(),
            Opt.Choice("vol", "Volume", listOf(
                Item("volume=2.0", "Much louder (+6 dB)"), Item("volume=1.41", "Louder (+3 dB)"),
                Item("volume=0.71", "Quieter (−3 dB)"), Item("volume=0.5", "Much quieter (−6 dB)"),
                Item("volume=0.25", "Very quiet (−12 dB)"),
                Item("loudnorm=I=-16:TP=-1.5:LRA=11", "Normalize", "Even out loud and quiet parts"),
                Item("mute", "Mute", "Remove the sound"),
            )),
        ),
        actionLabel = "Apply",
        build = { r ->
            val v = r.str("vol").ifEmpty { "volume=2.0" }
            r.files("media").map { f ->
                val ext = nameExt(f.name).ifEmpty { "mp4" }
                val hasV = isVideoStream(r, f)
                val out = r.out(if (hasV) "Video" else "Audio", f, ext, "_vol")
                val codec = when (ext) {
                    "mp3" -> "libmp3lame"; "flac" -> "flac"; "wav" -> "pcm_s16le"; "ogg", "oga" -> "libvorbis"
                    "opus", "webm" -> "libopus"; else -> "aac"
                }
                val cmd = if (v == "mute") "-i ${r.saf(f)} -map ${q("0:v?")} -c copy -an ${q(out.path)}"
                          else "-i ${r.saf(f)} -map ${q("0:v?")} -map ${q("0:a?")} -c:v copy -af ${q(v)} -c:a $codec ${q(out.path)}"
                r.job(f, out, cmd)
            }
        },
    ),
).associateBy { it.id }
