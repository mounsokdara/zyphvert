package com.mounsokdara.zyphvert.ui.tool

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import com.mounsokdara.zyphvert.ui.home.Tool
import com.mounsokdara.zyphvert.ui.home.ToolCategory
import com.mounsokdara.zyphvert.ui.home.accent

/** MIME types each tool accepts as primary input. */
private fun Tool.acceptedMimeTypes(): Array<String> = when (category) {
    ToolCategory.Convert -> when (id) {
        "img2vid" -> arrayOf("image/*")
        "vid2img", "vid2aud", "vid2gif" -> arrayOf("video/*")
        "aud2vid" -> arrayOf("audio/*")
        "live" -> arrayOf("image/*", "video/*")
        "heic" -> arrayOf("image/*")
        else -> arrayOf("*/*")
    }
    ToolCategory.Edit -> arrayOf("video/*", "audio/*", "image/*")
    ToolCategory.Privacy -> arrayOf("video/*", "audio/*", "image/*")
    ToolCategory.Create -> arrayOf("image/*", "audio/*", "video/*")
    ToolCategory.Power -> arrayOf("*/*")
}

@Composable
fun ToolScreen(tool: Tool, onBack: () -> Unit) {
    val accent = tool.category.accent()
    var pickedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pickedName by rememberSaveable { mutableStateOf<String?>(null) }

    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
            pickedName = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
            // Persist read permission across process restarts
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* not persistable from SAF – fine */ }
        }
    }

    SubScreenScaffold(title = tool.title, onBack = onBack) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Tool header card ──────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = accent.container,
                        contentColor = accent.content,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(tool.icon, contentDescription = null, modifier = Modifier.size(30.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tool.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            tool.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── File picker ───────────────────────────────────────────────────
            OutlinedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Input file", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pickedUri == null) {
                        FilledTonalButton(
                            onClick = { picker.launch(tool.acceptedMimeTypes()) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Choose file")
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    pickedName ?: "Selected",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(
                                    onClick = { pickedUri = null; pickedName = null },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove",
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { picker.launch(tool.acceptedMimeTypes()) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Change file")
                        }
                    }
                }
            }

            // ── Processing section ────────────────────────────────────────────
            if (pickedUri != null) {
                ComingSoonProcessingCard(tool = tool)
            }

            // ── Run button ────────────────────────────────────────────────────
            Button(
                onClick = { /* processing is coming soon — button is here when file is picked */ },
                enabled = pickedUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (pickedUri == null) "Choose a file to continue" else "Start ${tool.title}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ComingSoonProcessingCard(tool: Tool) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.HourglassEmpty,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "${tool.title} engine is in development",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                "File access is ready. Processing will be enabled once the FFmpeg native build lands. Follow the repo for updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}
