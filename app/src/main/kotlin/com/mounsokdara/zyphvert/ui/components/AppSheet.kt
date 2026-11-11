package com.mounsokdara.zyphvert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's bottom sheet: Material's own [ModalBottomSheet], so it opens and closes with the standard
 * slide animation, can be dragged by the handle (or by pulling the list down from the top), and
 * dismisses on a scrim tap or Back.
 *
 * It is always fully expanded when open ([skipPartiallyExpanded]), which removes the half-open state
 * that used to make a scrolling list and the sheet fight over the same drag.
 *
 * [content] receives `close`: call it after picking something so the sheet animates away before
 * [onDismiss] removes it. Put long content in a LazyColumn or a verticalScroll Column with
 * `Modifier.weight(1f, fill = false)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Always remove the sheet once the hide animation ends, even if it was interrupted, so a button
    // like "Done" can never leave the sheet stuck on screen.
    val close: () -> Unit = {
        scope.launch {
            try { state.hide() } finally { onDismiss() }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // Dragging only works from the handle. With gestures on, scrolling a long list moved the whole sheet.
        sheetGesturesEnabled = false,
        dragHandle = { SheetHandle(close) },
    ) { content(close) }
}

/** The only part of the sheet that drags it: pull down (or flick down, or tap) to close. */
@Composable
private fun SheetHandle(close: () -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    val closeAt = with(LocalDensity.current) { 56.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { drag += it },
                onDragStopped = { velocity ->
                    if (drag > closeAt || velocity > 1200f) close()
                    drag = 0f
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}
