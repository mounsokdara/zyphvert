package com.mounsokdara.zyphvert.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.ui.components.CenteredContent
import com.mounsokdara.zyphvert.ui.components.MoreCard

enum class MoreRoute { Settings, Terminal, About, Licenses }

@Composable
fun MoreScreen(bottomPadding: Dp, onOpen: (MoreRoute) -> Unit, modifier: Modifier = Modifier) {
    CenteredContent(maxWidth = 720.dp) {
        Column(
            modifier = modifier
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "More",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 8.dp, top = 40.dp, bottom = 24.dp),
            )
            MoreCard(Icons.Rounded.Settings, "Settings", "Theme, color, permissions, and data") { onOpen(MoreRoute.Settings) }
            MoreCard(Icons.Rounded.Terminal, "FFmpeg terminal", "Run ffmpeg and ffprobe commands") { onOpen(MoreRoute.Terminal) }
            MoreCard(Icons.Rounded.Info, "About", "Version, license, and source") { onOpen(MoreRoute.About) }
        }
    }
}
