package com.mounsokdara.zyphvert.ui.files

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.util.ShizukuFs

@Composable
fun LockedHint(
    folderPermissionPossible: Boolean,
    initialTreeUri: Uri,
    onTreeGranted: (Uri?) -> Unit,
    onRetry: () -> Unit,
) {
    val ctx = LocalContext.current
    var check by remember { mutableIntStateOf(0) }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), onTreeGranted)
    DisposableEffect(Unit) {
        val stop = ShizukuFs.listenForPermission { granted -> if (granted) onRetry() }
        onDispose { stop() }
    }
    val running = remember(check) { ShizukuFs.running() }
    val granted = remember(check) { ShizukuFs.granted() }

    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Android keeps this folder closed", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "Android 11 and newer do not let apps open Android/data and Android/obb, even with All files access.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        if (folderPermissionPossible) {
            Button(onClick = { tree.launch(initialTreeUri) }) { Text("Allow folder access") }
            Text(
                "In the next screen, tap \"Use this folder\".",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
        when {
            granted -> {
                Text(
                    "Shizuku is allowed, but it could not read this folder.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onRetry) { Text("Try again") }
            }
            running -> OutlinedButton(onClick = { ShizukuFs.requestPermission(1001) }) { Text("Allow with Shizuku") }
            else -> {
                if (!folderPermissionPossible) {
                    Text(
                        "On your Android version the only way in is the Shizuku app. Install it, start it, then come back.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        "Or use the Shizuku app: install it and start it, then check again.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
                OutlinedButton(onClick = {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("Get Shizuku") }
                OutlinedButton(onClick = { check++ }) { Text("Check again") }
            }
        }
    }
}
