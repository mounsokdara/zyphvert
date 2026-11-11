package com.mounsokdara.zyphvert.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    var h by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var scrollable by remember { mutableStateOf(false) }
    var anim by remember { mutableStateOf<Job?>(null) }

    fun animateTo(target: Float, then: () -> Unit = {}) {
        anim?.cancel()
        anim = scope.launch {
            animate(y, target, animationSpec = tween(240, easing = FastOutSlowInEasing)) { v, _ -> y = v }
            then()
        }
    }
    fun close() {
        if (closing) return
        closing = true
        if (h <= 0f) onDismiss() else animateTo(h) { onDismiss() }
    }
    fun settle(velocity: Float) {
        if (closing) return
        if (velocity > 1500f || y > h * 0.35f) close() else animateTo(0f)
    }

    LaunchedEffect(h > 0f) {
        if (h > 0f && !shown) {
            y = h
            shown = true
            animateTo(0f)
        }
    }

    val dragState = rememberDraggableState { dy ->
        if (!closing) {
            anim?.cancel()
            y = (y + dy).coerceAtLeast(0f)
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (scrollable || closing || y <= 0f || available.y >= 0f) return Offset.Zero
                val c = maxOf(available.y, -y)
                y += c
                return Offset(0f, c)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (scrollable || closing || available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                anim?.cancel()
                y += available.y
                return Offset(0f, available.y)
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (scrollable || closing || y <= 0f) return Velocity.Zero
                settle(available.y)
                return Velocity(0f, available.y)
            }
        }
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { w ->
            SideEffect { w.setDimAmount(0f) }
        }
        Box(Modifier.fillMaxSize()) {
            val shownFraction = if (!shown || h <= 0f) 0f else (1f - y / h).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f * shownFraction))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .statusBarsPadding()
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .onSizeChanged { h = it.height.toFloat() }
                    .graphicsLayer { translationY = y; alpha = if (shown) 1f else 0f }
                    .nestedScroll(connection)
                    .then(
                        if (scrollable) Modifier
                        else Modifier.draggable(dragState, Orientation.Vertical, onDragStopped = { settle(it) }),
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier.layout { measurable, c ->
                        val p = measurable.measure(c)
                        val full = c.hasBoundedHeight && p.height >= c.maxHeight - 1
                        if (full != scrollable) scrollable = full
                        layout(p.width, p.height) { p.place(0, 0) }
                    },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .draggable(dragState, Orientation.Vertical, onDragStopped = { settle(it) }),
                        contentAlignment = Alignment.Center,
                    ) { BottomSheetDefaults.DragHandle() }
                    content { close() }
                }
            }
        }
    }
}
