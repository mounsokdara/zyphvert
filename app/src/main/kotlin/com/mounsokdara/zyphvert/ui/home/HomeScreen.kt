package com.mounsokdara.zyphvert.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tool-grid home screen. Each entry will route to its own conversion/editing
 * flow as those milestones land; this is the milestone-1 scaffold.
 */
private val placeholderTools = listOf(
    "Image → Video", "Video → Images", "Video → Audio", "Video → GIF/WebP",
    "Audio → Video", "Live/Motion Photos", "HEIC/AVIF Convert", "Trim & Cut",
    "Crop & Rotate", "Speed & Reverse", "Filters", "Watermark",
    "Metadata Editor", "Privacy Scrub", "Sticker Maker", "Ringtone Maker",
    "Batch Queue", "Custom FFmpeg"
)

@Composable
fun HomeScreen() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(12.dp)
    ) {
        items(placeholderTools) { tool ->
            Card(modifier = Modifier.padding(4.dp)) {
                Text(text = tool, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
