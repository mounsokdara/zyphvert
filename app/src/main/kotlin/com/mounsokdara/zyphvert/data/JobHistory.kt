package com.mounsokdara.zyphvert.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class JobStatus { Running, Success, Failed, Cancelled }

data class ConversionJob(
    val id: String = UUID.randomUUID().toString(),
    val toolTitle: String,
    val inputLabel: String,
    val outputLabel: String,
    val command: String,
    val status: JobStatus,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val message: String? = null,
)

/** Simple in-memory history shared across tools (survives while process is alive). */
object JobHistory {
    private val _jobs = MutableStateFlow<List<ConversionJob>>(emptyList())
    val jobs: StateFlow<List<ConversionJob>> = _jobs.asStateFlow()

    fun add(job: ConversionJob) {
        _jobs.value = listOf(job) + _jobs.value.take(49)
    }

    fun update(id: String, status: JobStatus, message: String? = null) {
        _jobs.value = _jobs.value.map {
            if (it.id == id) it.copy(
                status = status,
                message = message ?: it.message,
                finishedAt = if (status != JobStatus.Running) System.currentTimeMillis() else it.finishedAt,
            ) else it
        }
    }

    fun clear() {
        _jobs.value = emptyList()
    }
}
