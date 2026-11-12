package com.mounsokdara.zyphvert.ui.tool

import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mounsokdara.zyphvert.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

private val RangeShape = RoundedCornerShape(24.dp)

private fun clock(sec: Double): String {
    val t = (sec * 10).toLong() / 10.0
    val m = (t / 60).toInt()
    val s = t - m * 60
    return String.format(Locale.US, "%d:%04.1f", m, s)
}

@Composable
internal fun RangeCard(opt: Opt.Range, vm: ToolViewModel) {
    val ctx = LocalContext.current
    val first = vm.picked[opt.slot].orEmpty().firstOrNull()
    val uri = first?.uri
    val video = first != null && !isAudioName(first.name)
    var dur by remember(uri) { mutableStateOf(0.0) }
    var peaks by remember(uri) { mutableStateOf<FloatArray?>(null) }
    var loading by remember(uri) { mutableStateOf(true) }
    var player by remember(uri) { mutableStateOf<VideoView?>(null) }
    var playing by remember(uri) { mutableStateOf(false) }
    var pos by remember(uri) { mutableStateOf(0.0) }
    LaunchedEffect(uri) {
        loading = true
        if (uri == null) { dur = 0.0; loading = false; return@LaunchedEffect }
        dur = withContext(Dispatchers.IO) { durationSec(ctx, uri) } ?: 0.0
        peaks = withContext(Dispatchers.IO) { loadPeaks(ctx, uri, 240) { isActive } }
        loading = false
    }
    DisposableEffect(uri) { onDispose { player?.stopPlayback() } }

    val parts = (vm.values[opt.id] ?: "").split(",")
    val s = (parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, dur)
    val e = (parts.getOrNull(1)?.toDoubleOrNull() ?: dur).coerceIn(s, dur)
    val keep = !opt.modes || (vm.values["${opt.id}.mode"] ?: "keep") == "keep"
    fun set(ns: Double, ne: Double) {
        val a = ns.coerceIn(0.0, dur)
        vm.values[opt.id] = String.format(Locale.US, "%.1f,%.1f", a, ne.coerceIn(a, dur))
    }
    fun seek(t: Double) {
        val c = t.coerceIn(0.0, dur)
        pos = c
        player?.seekTo((c * 1000).toInt())
    }
    fun toggle() {
        val p = player ?: return
        if (playing) { p.pause(); playing = false; return }
        if (keep && (pos < s || pos >= e - 0.05)) seek(s)
        p.start()
        playing = true
    }
    val curS by rememberUpdatedState(s)
    val curE by rememberUpdatedState(e)
    val curKeep by rememberUpdatedState(keep)
    LaunchedEffect(playing, player) {
        while (playing) {
            val p = player ?: break
            val t = p.currentPosition / 1000.0
            pos = t
            if (curKeep && t >= curE) { p.pause(); playing = false }
            else if (!curKeep && t >= curS && t < curE) p.seekTo((curE * 1000).toInt())
            delay(50)
        }
    }

    Surface(shape = RangeShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(opt.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (first == null || (!loading && dur <= 0.0)) {
                Text(
                    if (first == null) "Choose a file first, then pick the part you want." else "Couldn't read the length of this file. The whole file will be used.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (dur > 0.0) {
                key(uri) {
                    AndroidView(
                        factory = { c ->
                            VideoView(c).also { v ->
                                v.setVideoURI(uri)
                                v.setOnPreparedListener { v.seekTo(1) }
                                v.setOnCompletionListener { playing = false }
                                player = v
                            }
                        },
                        modifier = if (video) Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)) else Modifier.size(1.dp),
                    )
                }
                if (opt.modes) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = keep, onClick = { vm.values["${opt.id}.mode"] = "keep" }, label = { Text("Keep selection") })
                        FilterChip(selected = !keep, onClick = { vm.values["${opt.id}.mode"] = "remove" }, label = { Text("Remove selection") })
                    }
                }
                Waveform(peaks, dur, s, e, pos, keep, onRange = ::set, onSeek = ::seek)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    FilledIconButton(onClick = ::toggle) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play") }
                    Text("${clock(pos)} / ${clock(dur)}", Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { set(pos, maxOf(e, pos)) }) { Text("Start here") }
                    TextButton(onClick = { set(s, maxOf(pos, s)) }) { Text("End here") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Stepper(clock(s), { set(s - 0.1, e) }, { set(s + 0.1, e) })
                    Text(clock(e - s), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    Stepper(clock(e), { set(s, e - 0.1) }, { set(s, e + 0.1) })
                }
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Waveform(
    peaks: FloatArray?, dur: Double, s: Double, e: Double, pos: Double, keep: Boolean,
    onRange: (Double, Double) -> Unit, onSeek: (Double) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val head = MaterialTheme.colorScheme.tertiary
    val curS by rememberUpdatedState(s)
    val curE by rememberUpdatedState(e)
    val range by rememberUpdatedState(onRange)
    val seekTo by rememberUpdatedState(onSeek)
    Canvas(
        Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(dur) { detectTapGestures { o -> seekTo(o.x / size.width * dur) } }
            .pointerInput(dur) {
                var active = 0
                detectDragGestures(
                    onDragStart = { o ->
                        val sx = (curS / dur * size.width).toFloat()
                        val ex = (curE / dur * size.width).toFloat()
                        val slop = 28.dp.toPx()
                        active = when {
                            abs(o.x - sx) <= slop && abs(o.x - sx) <= abs(o.x - ex) -> 1
                            abs(o.x - ex) <= slop -> 2
                            else -> 3
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val t = change.position.x.coerceIn(0f, size.width.toFloat()) / size.width * dur
                        when (active) {
                            1 -> range(min(t, curE), curE)
                            2 -> range(curS, maxOf(t, curS))
                            else -> seekTo(t)
                        }
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        val n = peaks?.size ?: 120
        val barW = w / n
        val sx = (s / dur * w).toFloat()
        val ex = (e / dur * w).toFloat()
        for (i in 0 until n) {
            val amp = (peaks?.get(i) ?: 0.15f).coerceAtLeast(0.04f)
            val x = i * barW + barW / 2
            val selected = if (keep) x in sx..ex else x !in sx..ex
            drawLine(
                if (selected) primary else muted,
                Offset(x, h / 2 - amp * h * 0.45f), Offset(x, h / 2 + amp * h * 0.45f),
                strokeWidth = barW * 0.6f, cap = StrokeCap.Round,
            )
        }
        for (x in listOf(sx, ex)) {
            drawLine(primary, Offset(x, 0f), Offset(x, h), strokeWidth = 3.dp.toPx())
            drawCircle(primary, 7.dp.toPx(), Offset(x, 7.dp.toPx()))
            drawCircle(primary, 7.dp.toPx(), Offset(x, h - 7.dp.toPx()))
        }
        if (pos in 0.0..dur) {
            val px = (pos / dur * w).toFloat()
            drawLine(head, Offset(px, 0f), Offset(px, h), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
private fun Stepper(text: String, minus: () -> Unit, plus: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = minus) { Icon(Icons.Rounded.Remove, "Earlier") }
            Text(text, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = plus) { Icon(Icons.Rounded.Add, "Later") }
        }
    }
}
