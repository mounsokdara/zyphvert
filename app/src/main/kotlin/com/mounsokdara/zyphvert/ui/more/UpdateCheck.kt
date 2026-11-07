package com.mounsokdara.zyphvert.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.BuildConfig
import com.mounsokdara.zyphvert.ui.openUrl

/** The steps of "Check for update". */
sealed interface UpdateUi {
    data object None : UpdateUi
    data object Checking : UpdateUi
    data class Failed(val message: String) : UpdateUi
    data class UpToDate(val staleNote: String?) : UpdateUi
    data class Available(val latest: ReleaseNote) : UpdateUi
}

/** Looks at GitHub and decides which [UpdateUi] to show. Always asks GitHub, never the saved copy first. */
suspend fun resolveUpdate(ctx: android.content.Context): UpdateUi {
    val feed = ReleaseNotes.load(ctx, refresh = true)
    val latest = feed.latest ?: return UpdateUi.Failed(
        if (feed.error != null) "${feed.error}. Check your connection and try again." else "No release was found.",
    )
    return if (compareVersions(latest.version, BuildConfig.VERSION_NAME) > 0) UpdateUi.Available(latest)
    else UpdateUi.UpToDate(if (feed.fromCache) "(${feed.error ?: "Saved copy"} - this may be out of date.)" else null)
}

@Composable
fun UpdateDialogs(state: UpdateUi, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    when (state) {
        UpdateUi.None -> Unit
        UpdateUi.Checking -> AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(20.dp))
                    Text("Checking for updates...")
                }
            },
        )
        is UpdateUi.Failed -> AlertDialog(
            onDismissRequest = onDismiss, title = { Text("Could not check for updates") }, text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is UpdateUi.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss, title = { Text("You're up to date") },
            text = { Text("${AboutInfo.NAME} v${BuildConfig.VERSION_NAME} is the latest version." + (state.staleNote?.let { "\n\n$it" } ?: "")) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is UpdateUi.Available -> {
            val latest = state.latest
            val notes = plainNotes(latest.body).lines().filter { it.isNotBlank() }.take(10).joinToString("\n")
            AlertDialog(
                onDismissRequest = onDismiss, title = { Text("Update available") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("v${latest.version}  (installed: v${BuildConfig.VERSION_NAME})", fontWeight = FontWeight.SemiBold)
                        if (notes.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(notes) }
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
                confirmButton = {
                    Button(onClick = { onDismiss(); ctx.openUrl(latest.apkUrl ?: latest.url) }) {
                        Text(if (latest.apkUrl != null) "Download" else "View release")
                    }
                },
            )
        }
    }
}
