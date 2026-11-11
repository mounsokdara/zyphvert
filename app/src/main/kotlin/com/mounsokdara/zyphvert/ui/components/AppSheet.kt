package com.mounsokdara.zyphvert.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * The app's bottom sheet: Material's [ModalBottomSheet] with its own drag turned off.
 *
 * The sheet never reacts to touch drags, so a list or scrolling column inside it always scrolls and can
 * never fight the sheet over the gesture. It opens and closes with the standard slide animation and
 * closes from the scrim, Back, a tap on the handle, or `close`.
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
        sheetGesturesEnabled = false,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { Box(Modifier.clickable(onClick = close)) { BottomSheetDefaults.DragHandle() } },
    ) { content(close) }
}
