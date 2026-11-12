package com.mounsokdara.zyphvert.tools.svg

import android.graphics.Bitmap
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class TraceOptions(
    val colors: Int = 8,
    val detail: Int = 60,
    val curves: Boolean = true,
    val stacked: Boolean = true,
    val removeBg: Boolean = false,
    /** Experimental: replace traced loops that look like circles, ellipses, rectangles or simple polygons with clean primitives. */
    val shapes: Boolean = false,
    val speck: Int = 3,
    val maxSide: Int = 1024,
    val minify: Boolean = true,
)

/**
 * Offline raster to SVG tracer: k-means colour quantisation, per-colour boundary tracing,
 * Douglas-Peucker simplification and Catmull-Rom to Bezier curves.
 * "Redraw shapes" is classical geometric fitting (no neural network, no download), and is experimental.
 */
object Vectorizer {

    private class Loop(val x: FloatArray, val y: FloatArray, val area: Float)

    fun trace(src: Bitmap, o: TraceOptions): String {
        val ow = src.width; val oh = src.height
        val k = min(1f, o.maxSide.toFloat() / max(ow, oh))
        val w = max(1, (ow * k).roundToInt()); val h = max(1, (oh * k).roundToInt())
        val bmp = if (k < 1f) Bitmap.createScaledBitmap(src, w, h, true) else src
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        if (bmp !== src) bmp.recycle()
        val scale = ow.toFloat() / w

        val centers = palette(px, o.colors.coerceIn(2, 48))
        val labels = IntArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            labels[i] = if ((p ushr 24) < 128) -1 else nearest(centers, (p shr 16) and 255, (p shr 8) and 255, p and 255)
        }
        if (o.detail < 80) modeFilter(labels, w, h, centers.size)

        val counts = IntArray(centers.size)
        for (l in labels) if (l >= 0) counts[l]++
        if (o.removeBg) {
            val border = IntArray(centers.size)
            for (x in 0 until w) { val a = labels[x]; val b = labels[(h - 1) * w + x]; if (a >= 0) border[a]++; if (b >= 0) border[b]++ }
            for (y in 0 until h) { val a = labels[y * w]; val b = labels[y * w + w - 1]; if (a >= 0) border[a]++; if (b >= 0) border[b]++ }
            var bg = 0
            for (c in border.indices) if (border[c] > border[bg]) bg = c
            for (i in labels.indices) if (labels[i] == bg) labels[i] = -1
            counts[bg] = 0
        }
        val order = centers.indices.filter { counts[it] > 0 }.sortedByDescending { counts[it] }
        val rank = IntArray(centers.size) { -1 }
        order.forEachIndexed { r, c -> rank[c] = r }

        val eps = 0.6f + (100 - o.detail.coerceIn(1, 100)) / 100f * 2.2f
        val minArea = (o.speck * o.speck).toFloat()
        val nl = if (o.minify) "" else "\n"
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $ow $oh\" width=\"$ow\" height=\"$oh\">").append(nl)
        val mask = BooleanArray(w * h)
        val edges = ByteArray((w + 1) * (h + 1))

        for ((j, c) in order.withIndex()) {
            for (i in labels.indices) {
                val l = labels[i]
                mask[i] = l >= 0 && (if (o.stacked) rank[l] >= j else l == c)
            }
            val loops = traceLoops(mask, w, h, edges).filter { abs(it.area) >= minArea }
            if (loops.isEmpty()) continue
            val rgb = String.format("#%02x%02x%02x", centers[c][0].roundToInt(), centers[c][1].roundToInt(), centers[c][2].roundToInt())
            val holes = loops.filter { it.area < 0f }
            val path = StringBuilder()
            val shapes = StringBuilder()
            for (lp in loops) {
                if (o.shapes && lp.area > 0f) {
                    val s = fitShape(lp, scale, holes)
                    if (s != null) { shapes.append(s).append(nl); continue }
                }
                appendLoop(path, lp, eps, o.curves, scale)
            }
            sb.append("<g fill=\"").append(rgb).append("\">").append(nl)
            if (path.isNotEmpty()) sb.append("<path fill-rule=\"evenodd\" d=\"").append(path).append("\"/>").append(nl)
            sb.append(shapes).append("</g>").append(nl)
        }
        sb.append("</svg>")
        return sb.toString()
    }

    // ---- colour quantisation -------------------------------------------------------------------

    private fun dist(c: FloatArray, r: Float, g: Float, b: Float): Float {
        val dr = c[0] - r; val dg = c[1] - g; val db = c[2] - b
        return 2f * dr * dr + 4f * dg * dg + 3f * db * db
    }

    private fun nearest(centers: List<FloatArray>, r: Int, g: Int, b: Int): Int {
        var best = 0; var bd = Float.MAX_VALUE
        for (i in centers.indices) {
            val d = dist(centers[i], r.toFloat(), g.toFloat(), b.toFloat())
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    private fun palette(px: IntArray, k: Int): List<FloatArray> {
        val rnd = Random(7)
        val n = min(6000, px.size)
        val sample = ArrayList<IntArray>(n)
        var tries = 0
        while (sample.size < n && tries < n * 3) {
            tries++
            val p = px[rnd.nextInt(px.size)]
            if ((p ushr 24) >= 128) sample.add(intArrayOf((p shr 16) and 255, (p shr 8) and 255, p and 255))
        }
        if (sample.isEmpty()) return listOf(floatArrayOf(0f, 0f, 0f))
        val centers = ArrayList<FloatArray>()
        val first = sample[0]
        centers.add(floatArrayOf(first[0].toFloat(), first[1].toFloat(), first[2].toFloat()))
        val minD = FloatArray(sample.size) { dist(centers[0], sample[it][0].toFloat(), sample[it][1].toFloat(), sample[it][2].toFloat()) }
        while (centers.size < k) {
            var bi = 0
            for (i in minD.indices) if (minD[i] > minD[bi]) bi = i
            if (minD[bi] < 30f) break
            val s = sample[bi]
            val c = floatArrayOf(s[0].toFloat(), s[1].toFloat(), s[2].toFloat())
            centers.add(c)
            for (i in minD.indices) minD[i] = min(minD[i], dist(c, sample[i][0].toFloat(), sample[i][1].toFloat(), sample[i][2].toFloat()))
        }
        val sum = Array(centers.size) { FloatArray(4) }
        repeat(8) {
            for (s in sum) s.fill(0f)
            for (p in sample) {
                val c = nearest(centers, p[0], p[1], p[2])
                sum[c][0] += p[0]; sum[c][1] += p[1]; sum[c][2] += p[2]; sum[c][3] += 1f
            }
            for (i in centers.indices) if (sum[i][3] > 0f) {
                centers[i][0] = sum[i][0] / sum[i][3]; centers[i][1] = sum[i][1] / sum[i][3]; centers[i][2] = sum[i][2] / sum[i][3]
            }
        }
        return centers
    }

    private fun modeFilter(l: IntArray, w: Int, h: Int, k: Int) {
        val src = l.copyOf()
        val cnt = IntArray(k)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val cur = src[i]
            if (cur < 0) continue
            var best = cur; var bc = 0
            for (dy in -1..1) for (dx in -1..1) { val v = src[i + dy * w + dx]; if (v >= 0) cnt[v]++ }
            for (dy in -1..1) for (dx in -1..1) {
                val v = src[i + dy * w + dx]
                if (v >= 0) { if (cnt[v] > bc || (cnt[v] == bc && v == cur)) { bc = cnt[v]; best = v } }
            }
            for (dy in -1..1) for (dx in -1..1) { val v = src[i + dy * w + dx]; if (v >= 0) cnt[v] = 0 }
            l[i] = best
        }
    }

    // ---- boundary tracing ----------------------------------------------------------------------

    private val DX = intArrayOf(1, 0, -1, 0)
    private val DY = intArrayOf(0, 1, 0, -1)

    private fun traceLoops(mask: BooleanArray, w: Int, h: Int, e: ByteArray): List<Loop> {
        val vw = w + 1
        e.fill(0)
        for (y in 0 until h) for (x in 0 until w) {
            if (!mask[y * w + x]) continue
            if (y == 0 || !mask[(y - 1) * w + x]) e[y * vw + x] = (e[y * vw + x].toInt() or 1).toByte()
            if (x == w - 1 || !mask[y * w + x + 1]) e[y * vw + x + 1] = (e[y * vw + x + 1].toInt() or 2).toByte()
            if (y == h - 1 || !mask[(y + 1) * w + x]) e[(y + 1) * vw + x + 1] = (e[(y + 1) * vw + x + 1].toInt() or 4).toByte()
            if (x == 0 || !mask[y * w + x - 1]) e[(y + 1) * vw + x] = (e[(y + 1) * vw + x].toInt() or 8).toByte()
        }
        val out = ArrayList<Loop>()
        val xs = ArrayList<Int>(); val ys = ArrayList<Int>()
        for (v0 in e.indices) {
            while (e[v0].toInt() != 0) {
                val bits = e[v0].toInt()
                var d = 0
                while (bits and (1 shl d) == 0) d++
                e[v0] = (bits and (1 shl d).inv()).toByte()
                val sx = v0 % vw; val sy = v0 / vw
                val firstDir = d
                xs.clear(); ys.clear()
                var cx = sx + DX[d]; var cy = sy + DY[d]
                var guard = 0
                while (guard++ < 4_000_000) {
                    if (cx == sx && cy == sy) {
                        if (d != firstDir) { xs.add(sx); ys.add(sy) }
                        break
                    }
                    val m = e[cy * vw + cx].toInt()
                    var nd = -1
                    for (cand in intArrayOf((d + 1) % 4, d, (d + 3) % 4)) if (m and (1 shl cand) != 0) { nd = cand; break }
                    if (nd < 0) break
                    e[cy * vw + cx] = (m and (1 shl nd).inv()).toByte()
                    if (nd != d) { xs.add(cx); ys.add(cy) }
                    d = nd
                    cx += DX[d]; cy += DY[d]
                }
                if (xs.size >= 3) {
                    val fx = FloatArray(xs.size) { xs[it].toFloat() }
                    val fy = FloatArray(xs.size) { ys[it].toFloat() }
                    out.add(Loop(fx, fy, area(fx, fy)))
                }
            }
        }
        return out
    }

    private fun area(x: FloatArray, y: FloatArray): Float {
        var s = 0f
        for (i in x.indices) { val j = (i + 1) % x.size; s += x[i] * y[j] - x[j] * y[i] }
        return s / 2f
    }

    // ---- simplification and curves -------------------------------------------------------------

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        if (l2 == 0f) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        val t = (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        val qx = ax + t * dx; val qy = ay + t * dy
        return sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy))
    }

    /** Douglas-Peucker on a closed loop. Returns kept points in order. */
    private fun simplify(x: FloatArray, y: FloatArray, eps: Float): Pair<FloatArray, FloatArray> {
        val n = x.size
        if (n <= 4) return x to y
        var b = 0; var bd = -1f
        for (i in 1 until n) { val d = (x[i] - x[0]) * (x[i] - x[0]) + (y[i] - y[0]) * (y[i] - y[0]); if (d > bd) { bd = d; b = i } }
        val rx = FloatArray(n + 1) { x[it % n] }; val ry = FloatArray(n + 1) { y[it % n] }
        val keep = BooleanArray(n + 1)
        keep[0] = true; keep[b] = true; keep[n] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, b)); stack.addLast(intArrayOf(b, n))
        while (stack.isNotEmpty()) {
            val (lo, hi) = stack.removeLast()
            var mi = -1; var md = eps
            for (i in lo + 1 until hi) {
                val d = segDist(rx[i], ry[i], rx[lo], ry[lo], rx[hi], ry[hi])
                if (d > md) { md = d; mi = i }
            }
            if (mi >= 0) { keep[mi] = true; stack.addLast(intArrayOf(lo, mi)); stack.addLast(intArrayOf(mi, hi)) }
        }
        val ox = ArrayList<Float>(); val oy = ArrayList<Float>()
        for (i in 0 until n) if (keep[i]) { ox.add(rx[i]); oy.add(ry[i]) }
        return ox.toFloatArray() to oy.toFloatArray()
    }

    private fun f(v: Float): String {
        val r = (v * 10f).roundToInt()
        val a = abs(r)
        val s = if (a % 10 == 0) "${a / 10}" else "${a / 10}.${a % 10}"
        return if (r < 0) "-$s" else s
    }

    private fun appendLoop(sb: StringBuilder, lp: Loop, eps: Float, curves: Boolean, s: Float) {
        val (x, y) = simplify(lp.x, lp.y, eps)
        val n = x.size
        if (n < 3) return
        sb.append('M').append(f(x[0] * s)).append(' ').append(f(y[0] * s))
        if (!curves) {
            for (i in 1 until n) sb.append('L').append(f(x[i] * s)).append(' ').append(f(y[i] * s))
            sb.append('Z')
            return
        }
        val corner = BooleanArray(n) { i ->
            val p = (i + n - 1) % n; val q = (i + 1) % n
            val ax = x[i] - x[p]; val ay = y[i] - y[p]; val bx = x[q] - x[i]; val by = y[q] - y[i]
            val la = sqrt(ax * ax + ay * ay); val lb = sqrt(bx * bx + by * by)
            la == 0f || lb == 0f || (ax * bx + ay * by) / (la * lb) < 0.5f
        }
        for (i in 0 until n) {
            val i0 = (i + n - 1) % n; val i1 = i; val i2 = (i + 1) % n; val i3 = (i + 2) % n
            val segLen = sqrt((x[i2] - x[i1]) * (x[i2] - x[i1]) + (y[i2] - y[i1]) * (y[i2] - y[i1]))
            var t1x = 0f; var t1y = 0f; var t2x = 0f; var t2y = 0f
            if (!corner[i1]) { t1x = (x[i2] - x[i0]) / 6f; t1y = (y[i2] - y[i0]) / 6f }
            if (!corner[i2]) { t2x = (x[i3] - x[i1]) / 6f; t2y = (y[i3] - y[i1]) / 6f }
            val lim = segLen * 0.4f
            val l1 = sqrt(t1x * t1x + t1y * t1y); if (l1 > lim && l1 > 0f) { t1x *= lim / l1; t1y *= lim / l1 }
            val l2 = sqrt(t2x * t2x + t2y * t2y); if (l2 > lim && l2 > 0f) { t2x *= lim / l2; t2y *= lim / l2 }
            sb.append('C')
                .append(f((x[i1] + t1x) * s)).append(' ').append(f((y[i1] + t1y) * s)).append(' ')
                .append(f((x[i2] - t2x) * s)).append(' ').append(f((y[i2] - t2y) * s)).append(' ')
                .append(f(x[i2] * s)).append(' ').append(f(y[i2] * s))
        }
        sb.append('Z')
    }

    // ---- experimental shape redraw -------------------------------------------------------------

    private fun fitShape(lp: Loop, s: Float, holes: List<Loop>): String? {
        if (lp.area < 40f) return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in lp.x.indices) { minX = min(minX, lp.x[i]); maxX = max(maxX, lp.x[i]); minY = min(minY, lp.y[i]); maxY = max(maxY, lp.y[i]) }
        val bw = maxX - minX; val bh = maxY - minY
        if (bw < 4f || bh < 4f) return null
        for (hl in holes) {
            var hx = 0f; var hy = 0f
            for (i in hl.x.indices) { hx += hl.x[i]; hy += hl.y[i] }
            hx /= hl.x.size; hy /= hl.y.size
            if (hx in minX..maxX && hy in minY..maxY) return null
        }
        val ratio = lp.area / (bw * bh)
        if (ratio > 0.97f) {
            return "<rect x=\"${f(minX * s)}\" y=\"${f(minY * s)}\" width=\"${f(bw * s)}\" height=\"${f(bh * s)}\"/>"
        }
        if (ratio in 0.74f..0.83f && lp.x.size >= 12) {
            val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f; val rx = bw / 2f; val ry = bh / 2f
            var sum = 0f; var worst = 0f
            for (i in lp.x.indices) {
                val ux = (lp.x[i] - cx) / rx; val uy = (lp.y[i] - cy) / ry
                val e = abs(sqrt(ux * ux + uy * uy) - 1f)
                sum += e; worst = max(worst, e)
            }
            if (sum / lp.x.size < 0.04f && worst < 0.14f) {
                return if (abs(rx - ry) < 0.06f * max(rx, ry))
                    "<circle cx=\"${f(cx * s)}\" cy=\"${f(cy * s)}\" r=\"${f((rx + ry) / 2f * s)}\"/>"
                else "<ellipse cx=\"${f(cx * s)}\" cy=\"${f(cy * s)}\" rx=\"${f(rx * s)}\" ry=\"${f(ry * s)}\"/>"
            }
        }
        val diag = sqrt(bw * bw + bh * bh)
        val (px, py) = simplify(lp.x, lp.y, diag * 0.012f)
        if (px.size in 3..8) {
            val pts = StringBuilder()
            for (i in px.indices) { if (i > 0) pts.append(' '); pts.append(f(px[i] * s)).append(',').append(f(py[i] * s)) }
            return "<polygon points=\"$pts\"/>"
        }
        return null
    }
}
