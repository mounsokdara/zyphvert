package com.mounsokdara.zyphvert.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mounsokdara.zyphvert.engine.EngineEvent
import com.mounsokdara.zyphvert.engine.EngineFactory
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import java.io.File

@Composable
fun MediaInfoScreen(path: String, onBack: () -> Unit) {
    val engine = remember { EngineFactory.create() }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf("Probing…") }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(path) {
        if (!engine.isAvailable) {
            status = "FFmpeg engine not available in this build"
            lines = listOf(status)
            return@LaunchedEffect
        }
        val file = File(path)
        if (!file.exists()) {
            status = "File not found"
            lines = listOf(status)
            return@LaunchedEffect
        }
        // Quote path for shell safety in the command string
        val quoted = "\"${path.replace("\"", "\\\"")}\""
        val cmd = "-v error -show_format -show_streams -of json $quoted"
        val out = mutableListOf<String>()
        try {
            engine.run(cmd, probe = true).collect { ev ->
                when (ev) {
                    is EngineEvent.Log -> ev.text.split('\n').filter { it.isNotBlank() }.forEach { out += it }
                    is EngineEvent.Done -> {
                        status = when {
                            ev.cancelled -> "Cancelled"
                            ev.success -> "OK"
                            else -> "Failed" + (ev.returnCode?.let { " ($it)" } ?: "")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            out += t.message ?: "Error"
            status = "Error"
        }
        lines = out.ifEmpty { listOf("(no output)") }
    }

    SubScreenScaffold(
        title = "Media info",
        onBack = onBack,
        actions = {
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(lines.joinToString("\n"))) },
                enabled = lines.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                File(path).name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                status,
                style = MaterialTheme.typography.labelLarge,
                color = if (status == "OK") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                SelectionContainer {
                    LazyColumn(Modifier.padding(12.dp)) {
                        items(lines) { line ->
                            Text(
                                line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
