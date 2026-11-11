package com.mounsokdara.zyphvert.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

sealed interface EngineEvent {
    data class Log(val text: String) : EngineEvent
    data class Done(val success: Boolean, val cancelled: Boolean, val returnCode: Int?) : EngineEvent
}

interface MediaEngine {
    val isAvailable: Boolean
    fun version(): String
    fun run(command: String, probe: Boolean, onProgress: ((timeMs: Long, bytes: Long) -> Unit)? = null): Flow<EngineEvent>
    fun cancel()
    fun safRead(context: Context, uri: Uri): String?
    fun safWrite(context: Context, uri: Uri): String?
}
