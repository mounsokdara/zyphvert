package com.mounsokdara.zyphvert.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class JobStatus { Queued, Running, Success, Failed, Cancelled }

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
    /** Finished file or folder, for Open and Share. */
    val outputPath: String? = null,
    /** Command output (tail) or the full report for info jobs. */
    val details: String? = null,
    /** 0..1 while running, or null when the length of the job isn't known. */
    val progress: Float? = null,
    /** Bytes written to the output file so far. */
    val outputBytes: Long = 0L,
)

/** Simple in-memory history shared across tools (survives while process is alive). */
object JobHistory {
    private val _jobs = MutableStateFlow<List<ConversionJob>>(emptyList())
    val jobs: StateFlow<List<ConversionJob>> = _jobs.asStateFlow()

    fun add(job: ConversionJob) {
        _jobs.value = listOf(job) + _jobs.value.take(299)
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

    fun patch(id: String, change: (ConversionJob) -> ConversionJob) {
        _jobs.value = _jobs.value.map { if (it.id == id) change(it) else it }
    }

    fun clearFinished() {
        _jobs.value = _jobs.value.filter { it.status == JobStatus.Queued || it.status == JobStatus.Running }
    }
}
