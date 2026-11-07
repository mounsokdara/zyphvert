@file:OptIn(ExperimentalMaterial3Api::class)

package com.mounsokdara.zyphvert.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import com.mounsokdara.zyphvert.ui.openUrl
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Every GitHub release with its notes. Works offline from the last saved copy. */
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf<ReleaseFeed?>(null) }
    var loading by remember { mutableStateOf(true) }

    fun load(refresh: Boolean) {
        scope.launch {
            loading = true
            feed = ReleaseNotes.load(ctx, refresh)
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(false) }

    SubScreenScaffold(
        "Changelog", onBack,
        actions = { IconButton(onClick = { load(true) }, enabled = !loading) { Icon(Icons.Rounded.Refresh, "Refresh") } },
    ) { pad ->
        val f = feed
        val releases = f?.releases.orEmpty()
        PullToRefreshBox(isRefreshing = loading && f != null, onRefresh = { load(true) }, modifier = Modifier.padding(pad).fillMaxSize()) {
            when {
                f == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                releases.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (f.error == null) "No releases published yet." else "Release notes are not available right now.", textAlign = TextAlign.Center)
                        if (f.error != null) Text(f.error + ".", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { ctx.openUrl("https://github.com/${AboutInfo.REPO_SLUG}/releases") }) { Text("Open on GitHub") }
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (f.error != null || f.fromCache) item {
                        Text(
                            if (f.error != null) "${f.error} - showing the saved copy." else "Showing the saved copy.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items(releases, key = { it.tag + it.url }) { note -> ReleaseCard(note) { ctx.openUrl(note.url) } }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(note: ReleaseNote, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (note.prerelease) Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("Pre-release", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            note.publishedAt?.let {
                Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            if (note.body.isEmpty()) Text("No release notes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else plainNotes(note.body).split('\n').forEach { NoteLine(it) }
        }
    }
}

/** One line of release notes: headings, bullets and plain text. */
@Composable
private fun NoteLine(raw: String) {
    val line = raw.trimEnd()
    if (line.isBlank()) { Spacer(Modifier.height(6.dp)); return }
    Regex("^\\s*#{1,6}\\s+(.*)$").find(line)?.let {
        Text(it.groupValues[1], Modifier.padding(top = 6.dp, bottom = 2.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        return
    }
    Regex("^(\\s*)[-*+]\\s+(.*)$").find(line)?.let {
        val indent = if (it.groupValues[1].length >= 2) 16.dp else 0.dp
        Row(Modifier.padding(start = indent, top = 1.dp, bottom = 1.dp)) {
            Text("\u2022  ")
            Text(it.groupValues[2], Modifier.weight(1f))
        }
        return
    }
    Text(line.trim())
}
