@file:OptIn(ExperimentalMaterial3Api::class)

package com.mounsokdara.zyphvert.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.tools.AUDIO_EXTS
import com.mounsokdara.zyphvert.tools.IMAGE_EXTS
import com.mounsokdara.zyphvert.tools.VIDEO_EXTS

enum class SearchKind(val label: String, val icon: ImageVector, val color: Color, val exts: Set<String>) {
    Audio("Audio", Icons.Rounded.Headphones, Color(0xFFD500F9), AUDIO_EXTS),
    Image("Image", Icons.Rounded.Image, Color(0xFF43A047), IMAGE_EXTS),
    Video("Video", Icons.Rounded.Movie, Color(0xFF00ACC1), VIDEO_EXTS),
}

fun passesSearchFilters(isDir: Boolean, ext: String, showFolders: Boolean, kinds: Set<SearchKind>): Boolean =
    if (isDir) showFolders else kinds.isEmpty() || kinds.any { ext.lowercase() in it.exts }

@Composable
fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    grid: Boolean,
    onToggleGrid: () -> Unit,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Row(modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(requester),
            placeholder = { Text("Search") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        IconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onClose() }) { Icon(Icons.Rounded.Close, "Clear search") }
        IconButton(onClick = onToggleGrid) {
            Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, if (grid) "List view" else "Grid view")
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { menuContent { menu = false } }
        }
    }
}

@Composable
fun SearchFilters(
    showFolders: Boolean,
    onShowFolders: (Boolean) -> Unit,
    kinds: Set<SearchKind>,
    onKinds: (Set<SearchKind>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedChip(Color(0xFFFF5722), showFolders, { onShowFolders(!showFolders) }) {
            Icon(Icons.Rounded.Folder, if (showFolders) "Hide folders" else "Show folders", Modifier.size(22.dp), tint = Color(0xFFFF5722))
        }
        SearchKind.entries.forEach { k ->
            val on = k in kinds
            OutlinedChip(k.color, on, { onKinds(if (on) kinds - k else kinds + k) }) {
                Icon(k.icon, null, Modifier.size(22.dp), tint = k.color)
                Text(k.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun OutlinedChip(color: Color, on: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (on) color.copy(alpha = 0.18f) else Color.Transparent,
        border = BorderStroke(1.5.dp, color.copy(alpha = if (on) 1f else 0.55f)),
    ) {
        Row(Modifier.height(44.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}
