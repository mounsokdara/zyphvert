package com.mounsokdara.zyphvert.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's bottom sheet: Material's own [ModalBottomSheet], so it opens and closes with the standard
 * slide animation, can be dragged by the handle area (a scrolling list never drags it), and
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
    ) {
        // Scrolling a long list used to drag the whole sheet once the list hit its end. This swallows the leftover
        // scroll before it reaches the sheet, so the sheet is only dragged from the handle and the area around it.
        Column(Modifier.nestedScroll(SwallowLeftoverScroll)) { content(close) }
    }
}

private object SwallowLeftoverScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
