package com.mounsokdara.zyphvert.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.data.ThemeMode
import com.mounsokdara.zyphvert.ui.components.InfoTile
import com.mounsokdara.zyphvert.ui.components.SectionLabel
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import com.mounsokdara.zyphvert.ui.components.SwitchTile
import com.mounsokdara.zyphvert.util.StorageAccess

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    var themeDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    val hasAllFiles = StorageAccess.hasAllFilesAccess()

    fun launch(intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    SubScreenScaffold("Settings", onBack) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Appearance")
            InfoTile(Icons.Rounded.DarkMode, "Theme", s.themeMode.label, onClick = { themeDialog = true })
            SwitchTile(
                Icons.Rounded.Palette, "Dynamic color",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Use colors from your wallpaper" else "Requires Android 12 or newer",
                checked = s.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onChange = vm::setDynamicColor,
            )
            SwitchTile(Icons.Rounded.Vibration, "Haptic feedback", "Subtle vibration on taps", s.haptics, onChange = vm::setHaptics)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                InfoTile(Icons.Rounded.Language, "Language", "Choose the app language", onClick = {
                    launch(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                })
            }

            SectionLabel("Permissions")
            InfoTile(
                Icons.Rounded.FolderOpen,
                "All files access",
                if (hasAllFiles) "Granted — tools can use any path"
                else "Required for full converter access. Tap to grant",
                onClick = {
                    if (!hasAllFiles) StorageAccess.openAllFilesSettings(ctx)
                    else launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                },
            )
            InfoTile(Icons.Rounded.Notifications, "Notifications", "Progress alerts for long conversions", onClick = {
                launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            })
            InfoTile(Icons.Rounded.Info, "App info", "System permissions and storage", onClick = {
                launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
            })

            SectionLabel("Data")
            InfoTile(Icons.Rounded.Delete, "Clear saved commands", "Remove saved FFmpeg terminal commands", onClick = { clearDialog = true })
            InfoTile(Icons.Rounded.Apps, "Privacy", "Zyphvert is fully offline. It requests no internet permission.")
        }
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setTheme(mode); themeDialog = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.themeMode == mode, onClick = { vm.setTheme(mode); themeDialog = false })
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("Close") } },
        )
    }
    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Clear saved commands?") },
            text = { Text("This removes every command you saved in the FFmpeg terminal.") },
            confirmButton = { TextButton(onClick = { vm.clearSavedCommands(); clearDialog = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } },
        )
    }
}
