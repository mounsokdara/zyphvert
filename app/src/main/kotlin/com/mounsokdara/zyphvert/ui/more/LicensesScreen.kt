package com.mounsokdara.zyphvert.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.ui.components.InfoTile
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import com.mounsokdara.zyphvert.ui.openUrl

private data class Lib(val name: String, val license: String, val url: String)

private val libs = listOf(
    Lib("FFmpeg", "LGPL v2.1 or later", "https://ffmpeg.org/legal.html"),
    Lib("FFmpegKitNext", "LGPL v3.0", "https://github.com/arthenica/ffmpeg-kit-next"),
    Lib("smart-exception-java", "See project page", "https://github.com/arthenica/smart-exception"),
    Lib("Jetpack Compose, AndroidX, Lifecycle, DataStore", "Apache License 2.0", "https://developer.android.com/jetpack"),
    Lib("Material Components and icons", "Apache License 2.0", "https://github.com/material-components/material-components-android"),
    Lib("Hilt and Dagger", "Apache License 2.0", "https://github.com/google/dagger"),
    Lib("Kotlin and Coroutines", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    SubScreenScaffold("Open source licenses", onBack) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "FFmpeg is built from source as a shared library under the LGPL, with no GPL components. Tap an entry for its license and source.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            items(libs, key = { it.name }) { lib ->
                InfoTile(Icons.Rounded.Description, lib.name, lib.license, onClick = { ctx.openUrl(lib.url) })
            }
        }
    }
}
