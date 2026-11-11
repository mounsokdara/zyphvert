package com.mounsokdara.zyphvert.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mounsokdara.zyphvert.data.BatchTracker
import com.mounsokdara.zyphvert.data.JobHistory
import com.mounsokdara.zyphvert.data.JobStatus
import com.mounsokdara.zyphvert.ui.screens.QueueViewModel

@Composable
fun ConversionDialogs(onSeeQueue: () -> Unit, vm: QueueViewModel = hiltViewModel()) {
    val ids by BatchTracker.ids.collectAsState()
    val visible by BatchTracker.visible.collectAsState()
    val allJobs by JobHistory.jobs.collectAsState()
    var askCancel by remember { mutableStateOf(false) }
    if (ids.isEmpty()) return

    val jobs = allJobs.filter { it.id in ids }
    val total = ids.size
    val active = jobs.any { it.status == JobStatus.Queued || it.status == JobStatus.Running }
    val success = jobs.count { it.status == JobStatus.Success }
    val failed = jobs.count { it.status == JobStatus.Failed }
    val cancelled = jobs.count { it.status == JobStatus.Cancelled } + (total - jobs.size)

    LaunchedEffect(active) { if (!active) BatchTracker.show() }
    LaunchedEffect(active, success, failed) { if (!active && success == 0 && failed == 0) BatchTracker.clear() }
    if (!visible && active) return

    fun seeResult() { BatchTracker.clear(); onSeeQueue() }

    if (active) {
        val done = success + failed + cancelled
        val share = jobs.sumOf { j ->
            when (j.status) {
                JobStatus.Success, JobStatus.Failed, JobStatus.Cancelled -> 1.0
                JobStatus.Running -> (j.progress ?: 0f).toDouble()
                else -> 0.0
            }
        } / total
        val percent = (share * 100).toInt().coerceIn(0, 100)
        val current = jobs.firstOrNull { it.status == JobStatus.Running } ?: jobs.lastOrNull { it.status == JobStatus.Queued }
        AlertDialog(
            onDismissRequest = { BatchTracker.hide() },
            title = { Text("Your files are in process", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Please keep the app open. Do not lock your screen or switch to another app while processing or running any other task, otherwise your progress will be lost.")
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { share.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$percent%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("${done.coerceAtMost(total)} of $total", style = MaterialTheme.typography.labelLarge)
                    }
                    if (current != null) Text(
                        current.inputLabel, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { BatchTracker.hide(); onSeeQueue() }, modifier = Modifier.fillMaxWidth()) { Text("Hide & See Queue") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { askCancel = true }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        OutlinedButton(onClick = { BatchTracker.hide() }, modifier = Modifier.weight(1f)) { Text("Hide") }
                    }
                }
            },
        )
        if (askCancel) AlertDialog(
            onDismissRequest = { askCancel = false },
            title = { Text("Cancel this process?") },
            text = { Text("Files that are not finished yet will be stopped and their progress will be lost. Files that are already done are kept.") },
            confirmButton = {
                TextButton(onClick = { askCancel = false; ids.forEach { vm.manager.cancel(it) } }) { Text("Yes, cancel") }
            },
            dismissButton = { TextButton(onClick = { askCancel = false }) { Text("Keep processing") } },
        )
        return
    }

    if (!visible) return
    val (title, body) = when {
        failed == 0 -> "Process successfully" to "All files are successfully processed."
        success > 0 -> "Process failed successfully" to "Some files failed to process. Please see the error in the Queue tab."
        else -> "Process failed" to "Failed to process your files. Please see the error in the Queue tab."
    }
    AlertDialog(
        onDismissRequest = { BatchTracker.clear() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(body)
                if (failed > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text("Success $success   Failed $failed", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Start)
                }
            }
        },
        dismissButton = { TextButton(onClick = { BatchTracker.clear() }) { Text("Close") } },
        confirmButton = { Button(onClick = ::seeResult) { Text("See result") } },
    )
}
