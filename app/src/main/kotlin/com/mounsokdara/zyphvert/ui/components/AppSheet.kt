package com.mounsokdara.zyphvert.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * The app's bottom sheet, built on a plain dialog instead of Material's ModalBottomSheet.
 *
 * It has no drag handling of its own, so a list or scrolling column inside it always owns the touch and can
 * never fight the sheet over a gesture. It slides in and out and closes from the scrim, Back, a tap on the
 * handle, or `close`.
 *
 * [content] receives `close`: call it after picking something so the sheet animates away before
 * [onDismiss] removes it. Put long content in a LazyColumn or a verticalScroll Column with
 * `Modifier.weight(1f, fill = false)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val close: () -> Unit = { visible.targetState = false }
    // Remove the sheet once the slide-out ends, so "Done" can never leave it stuck on screen.
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (!visible.currentState && !visible.targetState) onDismiss()
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The scrim below is our own and fades with the sheet, so switch the window's own dim off.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { w ->
            SideEffect { w.setDimAmount(0f) }
        }
        val transition = rememberTransition(visible)
        Box(Modifier.fillMaxSize()) {
            transition.AnimatedVisibility(visible = { it }, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
                )
            }
            transition.AnimatedVisibility(
                visible = { it },
                modifier = Modifier.align(Alignment.BottomCenter).statusBarsPadding(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        Box(Modifier.align(Alignment.CenterHorizontally).clickable(onClick = close)) { BottomSheetDefaults.DragHandle() }
                        content(close)
                    }
                }
            }
        }
    }
}
