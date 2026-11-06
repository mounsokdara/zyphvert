package com.mounsokdara.zyphvert.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Used when libs/ffmpeg-kit-next.aar is absent (see scripts/fetch-ffmpeg-aar.sh). */
object EngineFactory {
    fun create(): MediaEngine = object : MediaEngine {
        override val isAvailable = false
        override fun version() = "FFmpeg engine not bundled in this build"
        override fun run(command: String, probe: Boolean, onProgress: ((Long, Long) -> Unit)?): Flow<EngineEvent> = flow {
            emit(EngineEvent.Log("Error: the FFmpeg library is not part of this build."))
            emit(EngineEvent.Log("Run the 'Build FFmpegKitNext AAR' workflow, then rebuild the app."))
            emit(EngineEvent.Done(success = false, cancelled = false, returnCode = null))
        }
        override fun cancel() = Unit
        override fun safRead(context: Context, uri: Uri): String? = null
        override fun safWrite(context: Context, uri: Uri): String? = null
    }
}
