package com.mounsokdara.zyphvert.ui.more

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mounsokdara.zyphvert.BuildConfig
import com.mounsokdara.zyphvert.ui.openUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The steps of "Check for update", from looking at GitHub to handing the APK to the installer. */
sealed interface UpdateUi {
    data object None : UpdateUi
    data object Checking : UpdateUi
    data class Failed(val message: String) : UpdateUi
    data class UpToDate(val staleNote: String?) : UpdateUi
    data class Available(val latest: ReleaseNote) : UpdateUi
    /** [total] is 0 when the server didn't say how big the file is. */
    data class Downloading(val done: Long, val total: Long) : UpdateUi
    data class DownloadFailed(val message: String, val release: ReleaseNote) : UpdateUi
    /** The APK is downloaded but Android hasn't been told this app may install apps. */
    data class NeedsInstallPermission(val file: File) : UpdateUi
}

/**
 * The whole update flow in one place: check GitHub, download the APK inside the app, open the system installer.
 * Android still asks you to confirm the install, and only accepts an APK signed with the same key as this app.
 */
class UpdateFlow(private val ctx: Context, private val scope: CoroutineScope) {
    var state by mutableStateOf<UpdateUi>(UpdateUi.None)
        private set
    private var job: Job? = null

    fun check() {
        job?.cancel()
        state = UpdateUi.Checking
        job = scope.launch { state = resolveUpdate(ctx) }
    }

    fun dismiss() {
        job?.cancel()
        state = UpdateUi.None
    }

    fun download(release: ReleaseNote) {
        val url = release.apkUrl
        if (url == null) { // no APK attached to the release: show its page instead
            dismiss()
            ctx.openUrl(release.url)
            return
        }
        job?.cancel()
        state = UpdateUi.Downloading(0, 0)
        job = scope.launch {
            try {
                val file = downloadApk(ctx, url, release.version) { done, total -> state = UpdateUi.Downloading(done, total) }
                install(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = UpdateUi.DownloadFailed(e.message ?: "The download stopped", release)
            }
        }
    }

    /** Opens the installer, or asks for the "install unknown apps" permission first. */
    fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            state = UpdateUi.NeedsInstallPermission(file)
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        state = UpdateUi.None
    }

    fun openInstallSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun rememberUpdateFlow(): UpdateFlow {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { UpdateFlow(ctx.applicationContext, scope) }
}

/** Looks at GitHub and decides which [UpdateUi] to show. Always asks GitHub, never the saved copy first. */
suspend fun resolveUpdate(ctx: Context): UpdateUi {
    val feed = ReleaseNotes.load(ctx, refresh = true)
    val latest = feed.latest ?: return UpdateUi.Failed(
        if (feed.error != null) "${feed.error}. Check your connection and try again." else "No release was found.",
    )
    return if (compareVersions(latest.version, BuildConfig.VERSION_NAME) > 0) UpdateUi.Available(latest)
    else UpdateUi.UpToDate(if (feed.fromCache) "(${feed.error ?: "Saved copy"} - this may be out of date.)" else null)
}

/** Downloads the APK into the cache, reporting progress. Throws when the download can't finish. */
private suspend fun downloadApk(ctx: Context, url: String, version: String, onProgress: (done: Long, total: Long) -> Unit): File =
    withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, "zyphvert-$version.apk.part")
        val target = File(dir, "zyphvert-$version.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "zyphvert-app")
            if (conn.responseCode != 200) throw java.io.IOException("GitHub answered ${conn.responseCode}")
            val total = conn.contentLengthLong.coerceAtLeast(0)
            var done = 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 150) { lastReport = now; onProgress(done, total) }
                    }
                }
            }
            if (total > 0 && done != total) throw java.io.IOException("The download was cut short")
            onProgress(done, total)
            if (!part.renameTo(target)) throw java.io.IOException("Could not save the update")
            target
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

@Composable
fun UpdateDialogs(flow: UpdateFlow) {
    val ctx = LocalContext.current
    when (val state = flow.state) {
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
            onDismissRequest = flow::dismiss, title = { Text("Could not check for updates") }, text = { Text(state.message) },
            confirmButton = { TextButton(onClick = flow::dismiss) { Text("OK") } },
        )
        is UpdateUi.UpToDate -> AlertDialog(
            onDismissRequest = flow::dismiss, title = { Text("You're up to date") },
            text = { Text("${AboutInfo.NAME} v${BuildConfig.VERSION_NAME} is the latest version." + (state.staleNote?.let { "\n\n$it" } ?: "")) },
            confirmButton = { TextButton(onClick = flow::dismiss) { Text("OK") } },
        )
        is UpdateUi.Available -> {
            val latest = state.latest
            val notes = plainNotes(latest.body).lines().filter { it.isNotBlank() }.take(10).joinToString("\n")
            AlertDialog(
                onDismissRequest = flow::dismiss, title = { Text("Update available") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("v${latest.version}  (installed: v${BuildConfig.VERSION_NAME})", fontWeight = FontWeight.SemiBold)
                        if (notes.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Text(notes) }
                    }
                },
                dismissButton = { TextButton(onClick = flow::dismiss) { Text("Later") } },
                confirmButton = {
                    Button(onClick = { flow.download(latest) }) { Text(if (latest.apkUrl != null) "Update now" else "View release") }
                },
            )
        }
        is UpdateUi.Downloading -> AlertDialog(
            onDismissRequest = {}, title = { Text("Downloading update") },
            text = {
                Column {
                    if (state.total > 0) LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (state.total > 0) "${(state.done * 100 / state.total).toInt()}%  ·  ${Formatter.formatFileSize(ctx, state.done)} of ${Formatter.formatFileSize(ctx, state.total)}"
                        else Formatter.formatFileSize(ctx, state.done),
                    )
                }
            },
            confirmButton = { TextButton(onClick = flow::dismiss) { Text("Cancel") } },
        )
        is UpdateUi.DownloadFailed -> AlertDialog(
            onDismissRequest = flow::dismiss, title = { Text("Download failed") }, text = { Text(state.message) },
            dismissButton = { TextButton(onClick = flow::dismiss) { Text("Close") } },
            confirmButton = { Button(onClick = { flow.download(state.release) }) { Text("Try again") } },
        )
        is UpdateUi.NeedsInstallPermission -> AlertDialog(
            onDismissRequest = flow::dismiss, title = { Text("Allow installing updates") },
            text = { Text("The update is downloaded. Android needs you to allow ${AboutInfo.NAME} to install apps. Turn on \"Allow from this source\", come back, then tap Install.") },
            dismissButton = { TextButton(onClick = flow::openInstallSettings) { Text("Open settings") } },
            confirmButton = { Button(onClick = { flow.install(state.file) }) { Text("Install") } },
        )
    }
}
