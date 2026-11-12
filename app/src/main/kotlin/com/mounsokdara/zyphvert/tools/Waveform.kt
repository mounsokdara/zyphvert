package com.mounsokdara.zyphvert.tools

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs

internal fun loadPeaks(ctx: Context, uri: Uri, bars: Int, alive: () -> Boolean): FloatArray? = runCatching {
    val ex = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        ex.setDataSource(ctx, uri, null)
        val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: return@runCatching null
        val format = ex.getTrackFormat(track)
        val durUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durUs <= 0L) return@runCatching null
        ex.selectTrack(track)
        val dec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec = dec
        dec.configure(format, null, null, 0)
        dec.start()
        val peaks = FloatArray(bars)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone && alive()) {
            if (!inputDone) {
                val i = dec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val n = ex.readSampleData(dec.getInputBuffer(i)!!, 0)
                    if (n < 0) {
                        dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val o = dec.dequeueOutputBuffer(info, 10_000)
            if (o >= 0) {
                if (info.size > 0) {
                    val out = dec.getOutputBuffer(o)!!
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    val shorts = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                    var peak = 0
                    for (k in 0 until shorts.remaining()) peak = maxOf(peak, abs(shorts.get(k).toInt()))
                    val b = ((info.presentationTimeUs.toDouble() / durUs) * bars).toInt().coerceIn(0, bars - 1)
                    peaks[b] = maxOf(peaks[b], peak / 32768f)
                }
                dec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        val top = peaks.maxOrNull() ?: 0f
        if (top <= 0f) return@runCatching null
        for (i in 1 until bars) if (peaks[i] == 0f) peaks[i] = peaks[i - 1] * 0.7f
        FloatArray(bars) { peaks[it] / top }
    } finally {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        ex.release()
    }
}.getOrNull()
