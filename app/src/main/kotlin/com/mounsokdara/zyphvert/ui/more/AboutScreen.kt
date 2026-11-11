package com.mounsokdara.zyphvert.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.BuildConfig
import com.mounsokdara.zyphvert.R
import com.mounsokdara.zyphvert.ui.Links
import com.mounsokdara.zyphvert.ui.components.GithubIcon
import com.mounsokdara.zyphvert.ui.components.InfoTile
import com.mounsokdara.zyphvert.ui.components.SectionLabel
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import com.mounsokdara.zyphvert.ui.openUrl

private val CardShape = RoundedCornerShape(28.dp)

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onLicenses: () -> Unit,
    onChangelog: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // The author's name and photo come from GitHub; until (or unless) they load, the initials stay.
    var profile by remember { mutableStateOf<GithubProfile?>(null) }
    LaunchedEffect(Unit) { profile = GithubProfile.load(AboutInfo.GITHUB_LOGIN) }

    // Check for update: look at GitHub, download the APK here, hand it to the installer
    val updates = rememberUpdateFlow()
    fun checkForUpdate() = updates.check()
    UpdateDialogs(updates)

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
            QuickActions(
                listOf(
                    QuickAction("Changelog", { Icon(Icons.Rounded.History, null) }, onChangelog),
                    QuickAction("GitHub", { GithubIcon() }) { ctx.openUrl(Links.REPO) },
                    QuickAction("Check for update", { Icon(Icons.Rounded.SystemUpdateAlt, null) }) { checkForUpdate() },
                    QuickAction("Issues", { Icon(Icons.Rounded.BugReport, null) }) { ctx.openUrl(Links.ISSUES) },
                ),
            )

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
                        FilledTonalIconButton(onClick = { ctx.openUrl(Links.AUTHOR) }) { GithubIcon() }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                InfoTile(
                    Icons.Rounded.Info, "Version", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                )
                InfoTile(
                    Icons.Rounded.Description, "Open source licenses", "Libraries used by this app",
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
                    onClick = onLicenses,
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                )
            }

        }
    }
}

private class QuickAction(val label: String, val icon: @Composable () -> Unit, val onClick: () -> Unit)

/**
 * A row of equal tiles that join into one pill: big corners on the outside, small ones between.
 * IntrinsicSize makes every tile as tall as the tallest, so a label that wraps never makes one stick out.
 */
@Composable
private fun QuickActions(items: List<QuickAction>) {
    val big = 28.dp
    val small = 4.dp
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, item ->
            val shape = RoundedCornerShape(
                topStart = if (i == 0) big else small, bottomStart = if (i == 0) big else small,
                topEnd = if (i == items.lastIndex) big else small, bottomEnd = if (i == items.lastIndex) big else small,
            )
            Surface(
                shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).fillMaxHeight().clip(shape).clickable(onClick = item.onClick),
            ) {
                Column(
                    Modifier.fillMaxHeight().padding(vertical = 16.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    item.icon()
                    Spacer(Modifier.height(8.dp))
                    Text(item.label, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
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
