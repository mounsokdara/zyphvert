package com.mounsokdara.zyphvert.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.mounsokdara.zyphvert.data.ConversionJob
import com.mounsokdara.zyphvert.data.JobHistory
import com.mounsokdara.zyphvert.data.JobStatus
import com.mounsokdara.zyphvert.jobs.ConversionManager
import com.mounsokdara.zyphvert.tools.OutputPaths
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(val manager: ConversionManager) : ViewModel()

@Composable
fun QueueScreen(bottomPadding: Dp, modifier: Modifier = Modifier, vm: QueueViewModel = hiltViewModel()) {
    val jobs by JobHistory.jobs.collectAsState()
    val active = jobs.count { it.status == JobStatus.Queued || it.status == JobStatus.Running }
    val finished = jobs.size - active

    Column(modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomPadding)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Queue", style = MaterialTheme.typography.headlineMedium)
                if (jobs.isNotEmpty()) Text(
                    if (active > 0) "$active running or waiting" else "All done",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (active > 0) TextButton(onClick = vm.manager::cancelAll) { Text("Cancel all") }
            if (finished > 0) IconButton(onClick = JobHistory::clearFinished) { Icon(Icons.Rounded.DeleteSweep, "Clear finished") }
        }

        if (jobs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 480.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(96.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Queue, null, Modifier.size(44.dp)) }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Nothing in the queue", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Start a conversion from any tool. It runs in the background and shows up here with its result.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.widthIn(max = 720.dp),
            ) {
                items(jobs, key = { it.id }) { job -> JobCard(job, onCancel = { vm.manager.cancel(job.id) }, modifier = Modifier.animateItem()) }
            }
        }
    }
}

@Composable
private fun JobCard(job: ConversionJob, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var showDetails by remember { mutableStateOf(job.status == JobStatus.Success && job.details != null && job.outputPath == null) }
    val scheme = MaterialTheme.colorScheme
    val (icon, tint) = when (job.status) {
        JobStatus.Queued -> Icons.Rounded.Schedule to scheme.onSurfaceVariant
        JobStatus.Running -> Icons.Rounded.HourglassTop to scheme.primary
        JobStatus.Success -> Icons.Rounded.CheckCircle to scheme.primary
        JobStatus.Failed -> Icons.Rounded.Error to scheme.error
        JobStatus.Cancelled -> Icons.Rounded.StopCircle to scheme.onSurfaceVariant
    }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(job.startedAt))
    val file = job.outputPath?.let { File(it) }
    val isFile = file?.isFile == true

    Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerLow, modifier = modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.toolTitle, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${job.inputLabel} → ${job.outputLabel}",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val status = when (job.status) {
                        JobStatus.Queued -> "Waiting"
                        JobStatus.Running -> "Converting…"
                        else -> job.message ?: job.status.name
                    }
                    Text(
                        "$time · $status", style = MaterialTheme.typography.labelMedium,
                        color = if (job.status == JobStatus.Failed) scheme.error else scheme.onSurfaceVariant,
                    )
                }
                if (job.status == JobStatus.Queued || job.status == JobStatus.Running) {
                    IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel") }
                }
            }
            if (job.status == JobStatus.Running) {
                val p = job.progress
                // A real bar when the length is known, otherwise the endless one.
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                val parts = listOfNotNull(
                    p?.let { "${(it * 100).toInt()}%" },
                    job.outputBytes.takeIf { it > 0 }?.let { Formatter.formatFileSize(ctx, it) + " written" },
                )
                if (parts.isNotEmpty()) Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
            if (job.status == JobStatus.Success && job.outputPath != null) {
                Text(OutputPaths.display(job.outputPath), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (isFile) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { open(ctx, file!!) }) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Text("  Open") }
                    OutlinedButton(onClick = { share(ctx, file!!) }) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Text("  Share") }
                }
            }
            if (!job.details.isNullOrBlank() && (job.status == JobStatus.Failed || job.outputPath == null)) {
                TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Hide details" else "Show details") }
                if (showDetails) SelectionContainer {
                    Surface(shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainerLowest) {
                        Text(job.details, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

private fun uriFor(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
private fun mimeOf(f: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

private fun open(ctx: Context, f: File) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(ctx, f), mimeOf(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun share(ctx: Context, f: File) {
    val send = Intent(Intent.ACTION_SEND).setType(mimeOf(f)).putExtra(Intent.EXTRA_STREAM, uriFor(ctx, f))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
