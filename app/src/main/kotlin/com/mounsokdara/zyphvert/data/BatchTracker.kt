package com.mounsokdara.zyphvert.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The files of the conversion the person just started, so one dialog can show its progress and result. */
object BatchTracker {
    private val _ids = MutableStateFlow<List<String>>(emptyList())
    private val _visible = MutableStateFlow(false)
    val ids: StateFlow<List<String>> = _ids.asStateFlow()
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun start(jobIds: List<String>) { _ids.value = jobIds; _visible.value = true }
    fun hide() { _visible.value = false }
    fun show() { _visible.value = true }
    fun clear() { _ids.value = emptyList(); _visible.value = false }
}
