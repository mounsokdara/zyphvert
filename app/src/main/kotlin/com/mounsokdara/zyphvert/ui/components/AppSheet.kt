package com.mounsokdara.zyphvert.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's bottom sheet: Material's own [ModalBottomSheet], so it opens and closes with the standard
 * slide animation, dragged by its handle when the content is short (a sheet whose content scrolls can't be dragged at all), and
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
    // True once the content is as tall as the sheet can get, i.e. it scrolls. Then the sheet can't be dragged at all
    // (handle included); it closes from the scrim, Back or a button. Short content keeps the normal drag.
    var scrollable by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(Modifier.then(if (scrollable) Modifier.blockDrag() else Modifier)) { BottomSheetDefaults.DragHandle() }
        },
    ) {
        BoxWithConstraints {
            val maxH = constraints.maxHeight
            Column(
                Modifier
                    .onSizeChanged { scrollable = maxH != Constraints.Infinity && it.height >= maxH - 2 }
                    .then(if (scrollable) Modifier.nestedScroll(SwallowLeftoverScroll).blockDrag() else Modifier),
            ) { content(close) }
        }
    }
}

/** Eats vertical drags that nothing below used (a list uses its own), so the sheet's own drag never sees them. */
private fun Modifier.blockDrag(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Main)
            e.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
}

private object SwallowLeftoverScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
