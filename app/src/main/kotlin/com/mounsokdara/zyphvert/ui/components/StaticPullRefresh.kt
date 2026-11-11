package com.mounsokdara.zyphvert.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Pull down at the top of a list to refresh, without ever moving the list: the pull is swallowed and only a small
 * spinner shows. (Material's PullToRefreshBox let the list slide along with the finger and jump while refreshing.)
 */
@Composable
fun StaticPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    val maxPull = threshold * 1.4f
    var pull by remember { mutableFloatStateOf(0f) }
    val refreshing by rememberUpdatedState(isRefreshing)
    val refresh by rememberUpdatedState(onRefresh)

    val connection = remember(threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pull > 0f && available.y < 0f) {
                    val d = maxOf(available.y, -pull)
                    pull += d
                    return Offset(0f, d)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!refreshing && source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull = (pull + available.y * 0.5f).coerceAtMost(maxPull)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull >= threshold && !refreshing) refresh()
                val had = pull > 0f
                pull = 0f
                return if (had) available else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pull = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        content()
        val show = isRefreshing || pull > 0f
        if (show) {
            val y = if (isRefreshing) with(density) { 16.dp.toPx() } else (pull - with(density) { 40.dp.toPx() }).coerceAtLeast(0f)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, y.roundToInt()) }.size(40.dp)
                    .alpha(if (isRefreshing) 1f else (pull / threshold).coerceIn(0.2f, 1f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRefreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    else CircularProgressIndicator(progress = { (pull / threshold).coerceIn(0f, 1f) }, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                }
            }
        }
    }
}
