package com.mounsokdara.zyphvert.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

sealed interface EngineEvent {
    data class Log(val text: String) : EngineEvent
    data class Done(val success: Boolean, val cancelled: Boolean, val returnCode: Int?) : EngineEvent
}

/** Thin seam over FFmpegKit so the UI compiles with or without the native AAR present. */
interface MediaEngine {
    val isAvailable: Boolean
    fun version(): String
    fun run(command: String, probe: Boolean): Flow<EngineEvent>
    fun cancel()
    /** `saf:` path FFmpeg can read for a picked document, or null if unsupported. */
    fun safRead(context: Context, uri: Uri): String?
    fun safWrite(context: Context, uri: Uri): String?
}
