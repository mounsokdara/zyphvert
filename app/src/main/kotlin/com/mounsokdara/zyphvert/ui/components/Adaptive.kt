package com.mounsokdara.zyphvert.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material 3 window width breakpoints: compact < 600dp, medium 600-839dp, expanded >= 840dp. */
object Breakpoints {
    val Medium = 600.dp
    val Expanded = 840.dp
}

/** True on tablets and other wide windows (also updates in split-screen / foldable resize). */
@Composable
fun isWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp.dp >= Breakpoints.Medium

/** Centers [content] and caps its width so lines and cards do not stretch across a tablet screen. */
@Composable
fun CenteredContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 840.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize(), content = content)
    }
}
