package com.mounsokdara.zyphvert.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.mounsokdara.zyphvert.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.BuildConfig
import com.mounsokdara.zyphvert.R
import com.mounsokdara.zyphvert.ui.Links
import com.mounsokdara.zyphvert.ui.components.InfoTile
import com.mounsokdara.zyphvert.ui.components.SectionLabel
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import com.mounsokdara.zyphvert.ui.openUrl

private val CardShape = RoundedCornerShape(28.dp)

private const val TAPS_NEEDED = 10

@Composable
fun AboutScreen(
    settingsVm: SettingsViewModel,
    onBack: () -> Unit,
    onLicenses: () -> Unit,
    onChangelog: () -> Unit,
    onConsole: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by settingsVm.settings.collectAsState()

    // The author's name and photo come from GitHub; until (or unless) they load, the initials stay.
    var profile by remember { mutableStateOf<GithubProfile?>(null) }
    LaunchedEffect(Unit) { profile = GithubProfile.load(AboutInfo.GITHUB_LOGIN) }

    // Check for update
    var update by remember { mutableStateOf<UpdateUi>(UpdateUi.None) }
    fun checkForUpdate() {
        update = UpdateUi.Checking
        scope.launch { update = resolveUpdate(ctx) }
    }
    UpdateDialogs(update) { update = UpdateUi.None }

    // Ten quick taps on the version turn on developer options.
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    fun say(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
    fun onVersionTap() {
        val now = System.currentTimeMillis()
        if (now - lastTap > 2_000) taps = 0
        lastTap = now
        taps++
        if (settings.developerEnabled) { say("Developer options are on"); return }
        val left = TAPS_NEEDED - taps
        if (left in 1..3) say("$left tap${if (left == 1) "" else "s"} away from developer options")
        if (taps >= TAPS_NEEDED) {
            taps = 0
            settingsVm.setDeveloperEnabled(true)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            say("Developer options enabled")
        }
    }

    SubScreenScaffold("About", onBack) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    AppIcon(Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Zyphvert", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Offline FFmpeg media converter and editor. Your files never leave the device.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Link tiles
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LinkTile(Icons.Rounded.History, "Changelog", Modifier.weight(1f), onClick = onChangelog)
                LinkTile(Icons.Rounded.Code, "GitHub", Modifier.weight(1f)) { ctx.openUrl(Links.REPO) }
                LinkTile(Icons.Rounded.SystemUpdateAlt, "Check for update", Modifier.weight(1f)) { checkForUpdate() }
                LinkTile(Icons.Rounded.BugReport, "Issues", Modifier.weight(1f)) { ctx.openUrl(Links.ISSUES) }
            }

            SectionLabel("Author")
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val photo = profile?.avatar
                    if (photo != null) Image(
                        bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp).clip(CircleShape),
                    ) else Box(
                        Modifier.size(112.dp).clip(CircleShape).background(
                            Brush.linearGradient(listOf(Color(0xFF00A86B), Color(0xFF004D36)))
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("MS", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(profile?.name ?: AboutInfo.AUTHOR, style = MaterialTheme.typography.headlineSmall)
                    Text(AboutInfo.AUTHOR_ROLE, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = { ctx.openUrl(Links.SUPPORT) }) {
                            Icon(Icons.Rounded.VolunteerActivism, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text("Support my work", modifier = Modifier.padding(start = 8.dp))
                        }
                        FilledTonalIconButton(onClick = { ctx.openUrl(Links.AUTHOR) }) {
                            Icon(Icons.Rounded.Code, contentDescription = "Author on GitHub")
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                InfoTile(
                    Icons.Rounded.Info, "Version", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                    onClick = { onVersionTap() },
                )
                InfoTile(
                    Icons.Rounded.Description, "Open source licenses", "Libraries used by this app",
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
                    onClick = onLicenses,
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                )
            }

            if (settings.developerEnabled) DeveloperOptionsSection(settings, settingsVm, onConsole)
        }
    }
}

@Composable
private fun LinkTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.clip(shape).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** The launcher icon, drawn in Compose (painterResource can't load adaptive-icon XML). */
@Composable
fun AppIcon(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF00A86B), Color(0xFF004D36)))
        ),
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize().scale(1.7f),
        )
    }
}
