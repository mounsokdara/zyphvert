package com.mounsokdara.zyphvert.tools.svg

import android.graphics.Bitmap
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class TraceOptions(
    val colors: Int = 8,
    val detail: Int = 60,
    val curves: Boolean = true,
    val stacked: Boolean = true,
    val removeBg: Boolean = false,

    val shapes: Boolean = false,

    val strength: Int = 60,
    val speck: Int = 3,
    val maxSide: Int = 1024,
    val minify: Boolean = true,
)

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
            val st = o.strength.coerceIn(1, 100) / 100f
            for (lp in loops) {
                val fit = if (o.shapes) fitShape(lp, st, rgb, scale) else null
                if (fit != null) {
                    if (lp.area > 0f && !holeInside(lp, holes)) { shapes.append(fit.elem).append(nl); continue }
                    if (fit.pathD != null) { path.append(fit.pathD); continue }
                }
                appendLoop(path, lp, if (o.shapes) eps * (1f + 1.8f * st) else eps, o.curves, scale, o.shapes)
            }
            sb.append("<g fill=\"").append(rgb).append("\">").append(nl)
            if (path.isNotEmpty()) sb.append("<path fill-rule=\"evenodd\" d=\"").append(path).append("\"/>").append(nl)
            sb.append(shapes).append("</g>").append(nl)
        }
        sb.append("</svg>")
        return sb.toString()
    }

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

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        if (l2 == 0f) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        val t = (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        val qx = ax + t * dx; val qy = ay + t * dy
        return sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy))
    }

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

    private fun snapAxes(x: FloatArray, y: FloatArray, tolDeg: Float, minLen: Float): BooleanArray {
        val n = x.size
        val flat = BooleanArray(n)
        val t = tan(tolDeg * PI.toFloat() / 180f)
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = x[j] - x[i]; val dy = y[j] - y[i]
            if (sqrt(dx * dx + dy * dy) < minLen) continue
            if (abs(dy) <= t * abs(dx)) { val m = (y[i] + y[j]) / 2f; y[i] = m; y[j] = m; flat[i] = true }
            else if (abs(dx) <= t * abs(dy)) { val m = (x[i] + x[j]) / 2f; x[i] = m; x[j] = m; flat[i] = true }
        }
        return flat
    }

    private fun appendLoop(sb: StringBuilder, lp: Loop, eps: Float, curves: Boolean, s: Float, snap: Boolean) {
        val (sx, sy) = simplify(lp.x, lp.y, eps)
        val x = sx.copyOf(); val y = sy.copyOf()
        val n = x.size
        if (n < 3) return
        val flat = if (snap) snapAxes(x, y, 4f, 6f) else BooleanArray(n)
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
            if (la == 0f || lb == 0f) true
            else {
                val c = (ax * bx + ay * by) / (la * lb)
                c < 0.5f || ((flat[p] || flat[i]) && c < 0.94f)
            }
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

    private class Fit(val elem: String, val pathD: String?)

    private class Frame(val ang: Float, val w: Float, val h: Float, val cu: Float, val cv: Float) {
        val c = cos(ang); val s = sin(ang)
        fun u(x: Float, y: Float) = x * c + y * s
        fun v(x: Float, y: Float) = -x * s + y * c
        fun wx(u: Float, v: Float) = u * c - v * s
        fun wy(u: Float, v: Float) = u * s + v * c
        val cx get() = wx(cu, cv)
        val cy get() = wy(cu, cv)
    }

    private fun frameOf(lp: Loop, ang: Float): Frame {
        val c = cos(ang); val s = sin(ang)
        var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE; var minV = Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
        for (i in lp.x.indices) {
            val u = lp.x[i] * c + lp.y[i] * s; val v = -lp.x[i] * s + lp.y[i] * c
            minU = min(minU, u); maxU = max(maxU, u); minV = min(minV, v); maxV = max(maxV, v)
        }
        return Frame(ang, maxU - minU, maxV - minV, (minU + maxU) / 2f, (minV + maxV) / 2f)
    }

    private fun pcaAngle(lp: Loop): Float {
        val n = lp.x.size
        var a = 0f; var sx = 0f; var sy = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            val cr = lp.x[i] * lp.y[j] - lp.x[j] * lp.y[i]
            a += cr; sx += (lp.x[i] + lp.x[j]) * cr; sy += (lp.y[i] + lp.y[j]) * cr
        }
        if (abs(a) < 1e-3f) return 0f
        val cx = sx / (3f * a); val cy = sy / (3f * a)
        var ixx = 0f; var iyy = 0f; var ixy = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            val x0 = lp.x[i] - cx; val y0 = lp.y[i] - cy; val x1 = lp.x[j] - cx; val y1 = lp.y[j] - cy
            val cr = x0 * y1 - x1 * y0
            iyy += (x0 * x0 + x0 * x1 + x1 * x1) * cr
            ixx += (y0 * y0 + y0 * y1 + y1 * y1) * cr
            ixy += (x0 * y1 + 2f * x0 * y0 + 2f * x1 * y1 + x1 * y0) * cr
        }
        val varX = iyy / 12f; val varY = ixx / 12f; val cov = ixy / 24f
        return 0.5f * atan2(2f * cov, varX - varY)
    }

    private fun holeInside(lp: Loop, holes: List<Loop>): Boolean {
        if (holes.isEmpty()) return false
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in lp.x.indices) { minX = min(minX, lp.x[i]); maxX = max(maxX, lp.x[i]); minY = min(minY, lp.y[i]); maxY = max(maxY, lp.y[i]) }
        for (hl in holes) {
            var hx = 0f; var hy = 0f
            for (i in hl.x.indices) { hx += hl.x[i]; hy += hl.y[i] }
            hx /= hl.x.size; hy /= hl.y.size
            if (hx in minX..maxX && hy in minY..maxY) return true
        }
        return false
    }

    private fun rot(fr: Frame, s: Float): String {
        val deg = Math.round(fr.ang * 1800f / PI.toFloat()) / 10f
        return if (abs(deg) < 0.05f) "" else " transform=\"rotate(${f(deg)} ${f(fr.cx * s)} ${f(fr.cy * s)})\""
    }

    private fun fitShape(lp: Loop, st: Float, rgb: String, s: Float): Fit? {
        val n = lp.x.size
        val absA = abs(lp.area)
        if (absA < 30f || n < 4) return null
        val frames = ArrayList<Frame>()
        frames.add(frameOf(lp, 0f))
        val ang0 = pcaAngle(lp)
        val dm = (((ang0 * 180f / PI.toFloat()) % 90f) + 90f) % 90f
        if (dm > 2f && dm < 88f) frames.add(frameOf(lp, ang0))
        val tolR = 0.025f + 0.045f * st
        val tolW = 0.015f + 0.03f * st

        for (fr in frames) {
            if (fr.w < 4f || fr.h < 4f) continue
            val ratio = absA / (fr.w * fr.h)
            if (ratio > 0.965f) {
                val x0 = fr.cu - fr.w / 2f; val x1 = fr.cu + fr.w / 2f; val y0 = fr.cv - fr.h / 2f; val y1 = fr.cv + fr.h / 2f
                val d = StringBuilder("M")
                for ((i, uv) in listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1).withIndex()) {
                    if (i > 0) d.append('L')
                    d.append(f(fr.wx(uv.first, uv.second) * s)).append(' ').append(f(fr.wy(uv.first, uv.second) * s))
                }
                d.append('Z')
                val el = "<rect x=\"${f((fr.cx - fr.w / 2f) * s)}\" y=\"${f((fr.cy - fr.h / 2f) * s)}\" width=\"${f(fr.w * s)}\" height=\"${f(fr.h * s)}\"${rot(fr, s)}/>"
                return Fit(el, d.toString())
            }
            if (ratio in 0.72f..0.84f && n >= 8) {
                val rx = fr.w / 2f; val ry = fr.h / 2f
                var sum = 0f; var worst = 0f
                for (i in 0 until n) {
                    val du = (fr.u(lp.x[i], lp.y[i]) - fr.cu) / rx; val dv = (fr.v(lp.x[i], lp.y[i]) - fr.cv) / ry
                    val e = abs(sqrt(du * du + dv * dv) - 1f)
                    sum += e; worst = max(worst, e)
                }
                if (sum / n < tolR && worst < 0.1f + 0.15f * st) {
                    val round = abs(rx - ry) < 0.06f * max(rx, ry)
                    val rr = (rx + ry) / 2f
                    val ax = if (round) rr else rx; val ay = if (round) rr else ry
                    val p1x = fr.cx + ax * fr.c; val p1y = fr.cy + ax * fr.s
                    val p2x = fr.cx - ax * fr.c; val p2y = fr.cy - ax * fr.s
                    val deg = f(fr.ang * 180f / PI.toFloat())
                    val arc = "A${f(ax * s)} ${f(ay * s)} $deg 0 1 "
                    val d = "M${f(p1x * s)} ${f(p1y * s)}" + arc + "${f(p2x * s)} ${f(p2y * s)}" + arc + "${f(p1x * s)} ${f(p1y * s)}Z"
                    val el = if (round) "<circle cx=\"${f(fr.cx * s)}\" cy=\"${f(fr.cy * s)}\" r=\"${f(rr * s)}\"/>"
                    else "<ellipse cx=\"${f(fr.cx * s)}\" cy=\"${f(fr.cy * s)}\" rx=\"${f(rx * s)}\" ry=\"${f(ry * s)}\"${rot(fr, s)}/>"
                    return Fit(el, d)
                }
            }
            if (ratio in 0.78f..0.965f) {
                val r = sqrt((1f - ratio) * fr.w * fr.h / (4f - PI.toFloat()))
                if (r >= 1.5f && r <= min(fr.w, fr.h) / 2f + 0.5f) {
                    val hw = fr.w / 2f - r; val hh = fr.h / 2f - r
                    var sum = 0f
                    for (i in 0 until n) {
                        val qx = abs(fr.u(lp.x[i], lp.y[i]) - fr.cu) - hw; val qy = abs(fr.v(lp.x[i], lp.y[i]) - fr.cv) - hh
                        val sd = sqrt(max(qx, 0f) * max(qx, 0f) + max(qy, 0f) * max(qy, 0f)) + min(max(qx, qy), 0f) - r
                        sum += abs(sd)
                    }
                    if (sum / n < tolW * min(fr.w, fr.h)) {
                        val el = "<rect x=\"${f((fr.cx - fr.w / 2f) * s)}\" y=\"${f((fr.cy - fr.h / 2f) * s)}\" width=\"${f(fr.w * s)}\" height=\"${f(fr.h * s)}\" rx=\"${f(r * s)}\"${rot(fr, s)}/>"
                        return Fit(el, null)
                    }
                }
            }
        }

        val diag = sqrt(frames[0].w * frames[0].w + frames[0].h * frames[0].h)
        val (sx, sy) = simplify(lp.x, lp.y, diag * (0.008f + 0.03f * st))
        val maxV = 6 + (st * 6f).toInt()
        if (sx.size in 3..maxV) {
            val px = sx.copyOf(); val py = sy.copyOf()
            snapAxes(px, py, 4f, 4f)
            if (abs(abs(area(px, py)) - absA) / absA < 0.1f) {
                val pts = StringBuilder(); val d = StringBuilder("M")
                for (i in px.indices) {
                    if (i > 0) { pts.append(' '); d.append('L') }
                    pts.append(f(px[i] * s)).append(',').append(f(py[i] * s))
                    d.append(f(px[i] * s)).append(' ').append(f(py[i] * s))
                }
                d.append('Z')
                return Fit("<polygon points=\"$pts\"/>", d.toString())
            }
        }
        return null
    }
}
