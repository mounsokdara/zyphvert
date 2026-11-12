package com.mounsokdara.zyphvert.tools

import android.graphics.Color
import com.mounsokdara.zyphvert.tools.svg.SvgRenderer
import com.mounsokdara.zyphvert.tools.svg.TraceOptions
import com.mounsokdara.zyphvert.tools.svg.Vectorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val animatedSvg = setOf("mp4", "webm", "gif", "apng", "awebp", "frames")
private val stillSvg = setOf("png", "jpg", "webp", "svg_static")

private fun it2(vararg p: Triple<String, String, String?>) = p.map { Item(it.first, it.second, it.third) }

private val svgTargets = it2(
    Triple("png", "PNG", "Sharp, keeps transparency"),
    Triple("jpg", "JPG", null),
    Triple("webp", "WebP", null),
    Triple("mp4", "MP4 video", "Plays CSS, SMIL and script animations"),
    Triple("webm", "WebM video", "Keeps transparency"),
    Triple("gif", "Animated GIF", null),
    Triple("apng", "Animated PNG", null),
    Triple("awebp", "Animated WebP", null),
    Triple("frames", "PNG frames", "One image per frame, in a folder"),
    Triple("svg_static", "Static SVG", "Bakes CSS and scripts into plain shapes at one moment"),
    Triple("svg_min", "Minified SVG", "Removes comments, metadata and extra whitespace"),
    Triple("trace", "Trace picture to SVG", "Pick a PNG, JPG or any picture to get a scalable SVG"),
)

private val svgWidths = listOf(Item("0", "Auto (at least 512 px)")) +
    listOf(256, 512, 1024, 1920, 2048, 3840).map { Item("$it", "$it px wide") }

private fun hexColor(s: String, fallback: Int): Int =
    runCatching { Color.parseColor(if (s.startsWith("#")) s.trim() else "#" + s.trim()) }.getOrDefault(fallback)

private fun svgJobs(r: ToolRequest, f: PickedFile): List<ToolJob> {
    val fmt = r.str("sfmt").ifEmpty { "png" }
    val scripts = r.str("sjs") != "false"
    val bgKey = r.str("sbg").ifEmpty { "transparent" }
    val opaque = fmt == "jpg" || fmt == "mp4"
    val bg = when {
        bgKey == "black" -> Color.BLACK
        bgKey == "custom" -> hexColor(r.str("sbgc"), Color.WHITE)
        bgKey == "white" || opaque -> Color.WHITE
        else -> Color.TRANSPARENT
    }
    val fps = r.str("sfps").toIntOrNull() ?: 30
    val dur = r.int("sdur")
    val atMs = r.int("sat") * 1000.0
    val ow = r.int("sw")
    val qual = r.int("sq").takeIf { it > 0 }?.coerceAtMost(100) ?: 90
    val loop = r.str("sloop") != "false"

    return when (fmt) {
        "png", "jpg", "webp" -> {
            val out = r.out("Images", f, fmt)
            listOf(ToolJob(r.toolTitle, f.name, out.path, native = {
                SvgRenderer.stillFile(r.ctx, f.uri, out, fmt, qual, ow, bg, scripts, atMs)
            }))
        }
        "svg_static" -> {
            val out = r.out("SVG", f, "svg", "_static")
            listOf(ToolJob(r.toolTitle, f.name, out.path, native = {
                SvgRenderer.bakeFile(r.ctx, f.uri, out, ow, scripts, atMs)
            }))
        }
        "svg_min" -> {
            val out = r.out("SVG", f, "svg", "_min")
            listOf(ToolJob(r.toolTitle, f.name, out.path, native = {
                val text = SvgRenderer.minify(SvgRenderer.readText(r.ctx, f.uri))
                withContext(Dispatchers.IO) { out.parentFile?.mkdirs(); out.writeText(text) }
            }))
        }
        "frames" -> {
            val dir = OutputPaths.uniqueDir(OutputPaths.dir(r.ctx, "Frames"), baseName(f.name) + "_frames")
            listOf(ToolJob(r.toolTitle, f.name, dir.path, native = {
                SvgRenderer.framesTo(r.ctx, f.uri, dir, "png", ow, bg, scripts, fps, dur, 100, false, 2048f)
            }))
        }
        else -> {
            val ext = if (fmt == "awebp") "webp" else if (fmt == "apng") "png" else fmt
            val out = r.out(if (fmt in setOf("gif", "apng", "awebp")) "Images" else "Video", f, ext)
            val tmp = File(r.ctx.cacheDir, "svgframes/" + System.nanoTime())
            val frameExt = if (fmt == "mp4") "jpg" else "png"
            val pattern = q(tmp.path + "/f_%05d." + frameExt)
            val even = fmt == "mp4" || fmt == "webm"
            val render = ToolJob(r.toolTitle, f.name, null, native = {
                SvgRenderer.framesTo(r.ctx, f.uri, tmp, frameExt, ow, bg, scripts, fps, dur, if (fmt == "mp4") 95 else 100, even, 1920f)
            })
            val inp = "-framerate $fps -i $pattern"
            val cmd = when (fmt) {
                "mp4" -> "$inp -vf ${q(VID_TAIL)} -c:v h264_mediacodec -b:v 8M -movflags +faststart ${q(out.path)}"
                "webm" -> "$inp -c:v libvpx-vp9 -b:v 0 -crf 32 -pix_fmt ${if (bg == Color.TRANSPARENT) "yuva420p" else "yuv420p"} -auto-alt-ref 0 ${q(out.path)}"
                "gif" -> "$inp -vf ${q("split[s0][s1];[s0]palettegen=reserve_transparent=1[p];[s1][p]paletteuse")} -loop ${if (loop) 0 else -1} ${q(out.path)}"
                "apng" -> "$inp -f apng -plays ${if (loop) 0 else 1} ${q(out.path)}"
                else -> "$inp -c:v libwebp -lossless 0 -q:v 80 -loop ${if (loop) 0 else 1} -pix_fmt yuva420p ${q(out.path)}"
            }
            val encode = ToolJob(r.toolTitle, f.name, out.path, command = cmd,
                after = { withContext(Dispatchers.IO) { tmp.deleteRecursively() } })
            listOf(render, encode)
        }
    }
}

private val traceItems = listOf(8, 2, 4, 6, 12, 16, 24, 32).map { Item("$it", "$it colors") }

private val tracePresets = listOf(
    Preset("logo", "Logo or icon", "Few flat colors, clean edges, shapes redrawn",
        mapOf("vcol" to "6", "vdet" to "75", "vmode" to "spline", "vlayer" to "stacked", "vshape" to "true", "vmax" to "1024")),
    Preset("illus", "Illustration", "Balanced colors and detail",
        mapOf("vcol" to "16", "vdet" to "60", "vmode" to "spline", "vlayer" to "stacked", "vshape" to "false", "vmax" to "1024")),
    Preset("photo", "Photo (artistic)", "Many colors, painterly look",
        mapOf("vcol" to "32", "vdet" to "45", "vmode" to "spline", "vlayer" to "stacked", "vshape" to "false", "vmax" to "768")),
    Preset("bw", "Black and white", "Two colors, great for line art and signatures",
        mapOf("vcol" to "2", "vdet" to "80", "vmode" to "spline", "vlayer" to "stacked", "vshape" to "false", "vmax" to "1536")),
    Preset("pixel", "Pixel art", "Sharp corners, no smoothing",
        mapOf("vcol" to "16", "vdet" to "100", "vmode" to "polygon", "vlayer" to "cutout", "vshape" to "false", "vmax" to "512")),
)

val svgToolSpecs: List<ToolSpec> = listOf(
    ToolSpec(
        id = "svg",
        intro = "One place for everything SVG. Turn an SVG, even an animated one with CSS, SMIL or JavaScript, into an image, video, GIF or clean static SVG. Or trace any picture into an SVG. All offline.",
        hint = "Scripts run in a sandbox with no network access. Turn them off under Advanced if you don't trust the file. Redraw shapes is experimental and uses on-device geometry, not a neural network.",
        inputs = listOf(InputSlot("media", "SVG files or pictures", InKind.Svg, multiple = true)),
        options = listOf(
            Opt.Name(),
            Opt.Choice("sfmt", "Convert to", svgTargets),
            Opt.Choice("sw", "Size", svgWidths, showIf = { it["sfmt"] != "svg_min" && it["sfmt"] != "trace" }),
            Opt.Choice("sbg", "Background", listOf(
                Item("transparent", "Transparent", "JPG and MP4 use white"), Item("white", "White"), Item("black", "Black"), Item("custom", "Custom color"),
            ), showIf = { it["sfmt"] != "svg_min" && it["sfmt"] != "svg_static" && it["sfmt"] != "trace" }),
            Opt.Text("sbgc", "Color (hex)", "#ffcc00", showIf = { it["sbg"] == "custom" && it["sfmt"] != "svg_min" && it["sfmt"] != "trace" }),
            Opt.Choice("sfps", "Frame rate", listOf(Item("30", "30 fps"), Item("24", "24 fps"), Item("60", "60 fps"), Item("12", "12 fps")),
                showIf = { it["sfmt"] in animatedSvg }),
            Opt.Slider("sdur", "Duration (0 = detect automatically)", 0, 60, 0, " s", showIf = { it["sfmt"] in animatedSvg }),
            Opt.Slider("sat", "Capture at", 0, 60, 0, " s", showIf = { it["sfmt"] in stillSvg }),
            Opt.Slider("sq", "Quality", 1, 100, 90, "%", showIf = { it["sfmt"] == "jpg" || it["sfmt"] == "webp" }),
            Opt.Toggle("sloop", "Loop forever", true, showIf = { it["sfmt"] == "gif" || it["sfmt"] == "apng" || it["sfmt"] == "awebp" }, advanced = true),
            Opt.Toggle("sjs", "Run scripts inside the SVG", true, "Needed for JavaScript animations", showIf = { it["sfmt"] != "svg_min" && it["sfmt"] != "trace" }, advanced = true),
            Opt.Presets("vpreset", "Quick preset", tracePresets, showIf = { it["sfmt"] == "trace" }),
            Opt.Choice("vcol", "Colors", traceItems, showIf = { it["sfmt"] == "trace" }),
            Opt.Slider("vdet", "Detail", 1, 100, 60, "%", showIf = { it["sfmt"] == "trace" }),
            Opt.Choice("vmode", "Edges", listOf(Item("spline", "Smooth curves"), Item("polygon", "Straight lines")), showIf = { it["sfmt"] == "trace" }),
            Opt.Toggle("vbg", "Remove background", false, "Drops the color that touches the image border", showIf = { it["sfmt"] == "trace" }),
            Opt.Toggle("vshape", "Redraw shapes with AI (experimental)", false,
                "Offline, on-device. Rebuilds circles, ellipses, rounded and rotated rectangles and polygons as clean shapes, and straightens and smooths everything else.", showIf = { it["sfmt"] == "trace" }),
            Opt.Slider("vstr", "Redraw strength", 1, 100, 60, "%", showIf = { it["sfmt"] == "trace" && it["vshape"] == "true" }),
            Opt.Choice("vlayer", "Layering", listOf(Item("stacked", "Stacked", "No gaps between colors"), Item("cutout", "Cutout", "Separate pieces")), showIf = { it["sfmt"] == "trace" }, advanced = true),
            Opt.Slider("vspk", "Remove specks", 0, 30, 3, " px", showIf = { it["sfmt"] == "trace" }, advanced = true),
            Opt.Choice("vmax", "Trace resolution", listOf(1024, 512, 768, 1536).map { Item("$it", "$it px") }, showIf = { it["sfmt"] == "trace" }, advanced = true),
            Opt.Toggle("vmin", "Minify output", true, showIf = { it["sfmt"] == "trace" }, advanced = true),
        ),
        build = { r ->
            if (r.str("sfmt") == "trace") {
            val o = TraceOptions(
                colors = r.str("vcol").toIntOrNull() ?: 8,
                detail = r.int("vdet").takeIf { it > 0 } ?: 60,
                curves = r.str("vmode") != "polygon",
                stacked = r.str("vlayer") != "cutout",
                removeBg = r.bool("vbg"),
                shapes = r.bool("vshape"),
                strength = r.int("vstr").takeIf { it > 0 } ?: 60,
                speck = r.int("vspk"),
                maxSide = r.str("vmax").toIntOrNull() ?: 1024,
                minify = r.str("vmin") != "false",
            )
            r.files("media").map { f ->
                if (nameExt(f.name) in setOf("svg", "svgz")) throw IllegalArgumentException("Tracing needs a picture. Pick a PNG, JPG or similar, or choose another format for an SVG.")
                val out = r.out("SVG", f, "svg")
                ToolJob(r.toolTitle, f.name, out.path, native = {
                    val bmp = decodeBitmap(r.ctx, f.uri)
                    try {
                        val svg = withContext(Dispatchers.Default) { Vectorizer.trace(bmp, o) }
                        withContext(Dispatchers.IO) { out.parentFile?.mkdirs(); out.writeText(svg) }
                    } finally {
                        bmp.recycle()
                    }
                })
            }
            } else {
                r.files("media").flatMap { f ->
                    if (nameExt(f.name) !in setOf("svg", "svgz")) throw IllegalArgumentException("Pick an SVG file for this format, or choose Trace picture to SVG for a picture.")
                    svgJobs(r, f)
                }
            }
        },
    ),
)

