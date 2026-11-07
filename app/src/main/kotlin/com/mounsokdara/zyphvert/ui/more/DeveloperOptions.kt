package com.mounsokdara.zyphvert.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mounsokdara.zyphvert.data.AppSettings
import com.mounsokdara.zyphvert.ui.components.InfoTile
import com.mounsokdara.zyphvert.ui.components.SectionLabel
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import com.mounsokdara.zyphvert.ui.settings.SettingsViewModel
import com.mounsokdara.zyphvert.util.DeveloperLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shown in About after the version has been tapped ten times. */
@Composable
fun DeveloperOptionsSection(settings: AppSettings, vm: SettingsViewModel, onConsole: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Developer options")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SwitchTile("Log debug", "Write developer logging to the Console", settings.debugLog, vm::setDebugLog,
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
            SwitchTile("Log conversions", "Commands, results and FFmpeg output for every job", settings.logJobs, vm::setLogJobs,
                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 28.dp, bottomEnd = 28.dp))
        }
        InfoTile(
            Icons.Rounded.Terminal, "Console", "Debug log and crash breadcrumbs", onClick = onConsole,
            trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        )
    }
}

@Composable
private fun SwitchTile(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, shape: androidx.compose.ui.graphics.Shape) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

/** The debug log and the last crash, selectable, with Refresh, Copy and Clear. */
@Composable
fun ConsoleScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            body = withContext(Dispatchers.IO) {
                val debug = DeveloperLog.readLog().ifEmpty { "No debug lines yet. Turn on Log debug and use the app." }
                "=== debug ===\n$debug\n\n=== crash ===\n${DeveloperLog.readCrash()}"
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    SubScreenScaffold(
        "Console", onBack,
        actions = {
            IconButton(onClick = { refresh() }) { Icon(Icons.Rounded.Refresh, "Refresh") }
            IconButton(
                enabled = body != null,
                onClick = {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Zyphvert console", body))
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                },
            ) { Icon(Icons.Rounded.ContentCopy, "Copy") }
            IconButton(enabled = body != null, onClick = { scope.launch { withContext(Dispatchers.IO) { DeveloperLog.clear() }; refresh() } }) {
                Icon(Icons.Rounded.DeleteOutline, "Clear")
            }
        },
    ) { pad ->
        val text = body
        if (text == null) {
            Column(Modifier.padding(pad).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            SelectionContainer {
                Text(
                    text, Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp,
                )
            }
        }
    }
}
