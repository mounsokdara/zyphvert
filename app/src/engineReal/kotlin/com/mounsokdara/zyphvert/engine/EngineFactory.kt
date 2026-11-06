package com.mounsokdara.zyphvert.engine

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Real engine, compiled only when libs/ffmpeg-kit-next.aar exists. */
object EngineFactory {
    fun create(): MediaEngine = FfmpegKitEngine()
}

private class FfmpegKitEngine : MediaEngine {
    @Volatile private var current: Session? = null

    override val isAvailable = true

    override fun version(): String =
        "FFmpegKitNext ${FFmpegKitConfig.getVersion()} · FFmpeg ${FFmpegKitConfig.getFFmpegVersion()}"

    override fun run(command: String, probe: Boolean, onProgress: ((Long, Long) -> Unit)?): Flow<EngineEvent> = callbackFlow {
        var finished = false
        fun complete(session: Session) {
            finished = true
            val rc = session.getReturnCode()
            trySend(EngineEvent.Done(ReturnCode.isSuccess(rc), ReturnCode.isCancel(rc), rc?.value))
            close()
        }
        current = if (probe) {
            FFprobeKit.executeAsync(command, { s -> complete(s) }, { l -> trySend(EngineEvent.Log(l.message)) })
        } else {
            FFmpegKit.executeAsync(
                command, { s -> complete(s) }, { l -> trySend(EngineEvent.Log(l.message)) },
                { st -> onProgress?.invoke(st.time.toLong(), st.size.toLong()) },
            )
        }
        awaitClose {
            if (!finished) current?.cancel()
            current = null
        }
    }

    override fun cancel() {
        current?.cancel()
    }

    override fun safRead(context: Context, uri: Uri): String? =
        FFmpegKitConfig.getSafParameterForRead(context, uri)

    override fun safWrite(context: Context, uri: Uri): String? =
        FFmpegKitConfig.getSafParameterForWrite(context, uri)
}
