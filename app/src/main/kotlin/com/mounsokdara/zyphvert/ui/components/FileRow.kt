package com.mounsokdara.zyphvert.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.tools.AUDIO_EXTS
import com.mounsokdara.zyphvert.tools.IMAGE_EXTS
import com.mounsokdara.zyphvert.tools.SUBTITLE_EXTS
import com.mounsokdara.zyphvert.tools.VIDEO_EXTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** A small picture (images, videos) and a length (videos, audio) for a file. Both can be missing. */
class MediaPreview(val thumb: Bitmap?, val durationMs: Long?)

/** Loads previews off the main thread, three at a time, and remembers them. */
object Thumbs {
    private val cache = object : LruCache<String, MediaPreview>((Runtime.getRuntime().maxMemory() / 1024 / 10).toInt()) {
        override fun sizeOf(key: String, value: MediaPreview): Int = ((value.thumb?.byteCount ?: 0) / 1024).coerceAtLeast(1)
    }
    private val gate = Semaphore(3)

    fun cached(key: String): MediaPreview? = cache.get(key)

    suspend fun load(file: File, key: String, px: Int): MediaPreview = withContext(Dispatchers.IO) {
        gate.withPermit {
            val ext = file.extension.lowercase()
            val p = runCatching {
                when (ext) {
                    in VIDEO_EXTS -> video(file, px)
                    in AUDIO_EXTS -> MediaPreview(null, duration(file))
                    in IMAGE_EXTS -> MediaPreview(image(file, px), null)
                    else -> MediaPreview(null, null)
                }
            }.getOrDefault(MediaPreview(null, null))
            cache.put(key, p)
            p
        }
    }

    private fun duration(file: File): Long? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally { runCatching { r.release() } }
    }

    private fun video(file: File, px: Int): MediaPreview {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.path)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            return MediaPreview(frame?.let { shrink(it, px) }, dur)
        } finally { runCatching { r.release() } }
    }

    private fun image(file: File, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun shrink(b: Bitmap, px: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= px * 2) return b
        val f = px * 2f / m
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }
}

@Composable
private fun rememberPreview(file: File?, wanted: Boolean): MediaPreview? {
    val key = if (file != null) file.path + ":" + file.lastModified() else ""
    var p by remember(key) { mutableStateOf(if (key.isEmpty()) null else Thumbs.cached(key)) }
    LaunchedEffect(key, wanted) {
        if (wanted && file != null && p == null) p = Thumbs.load(file, key, 160)
    }
    return p
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

private val FolderOrange = Color(0xFFFFA726)

/**
 * One file or folder, laid out like ZArchiver: a picture on the left, the name, then the size and length
 * with the date on the right. Videos and photos show a real thumbnail.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    name: String,
    file: File?,
    isDir: Boolean,
    size: String?,
    date: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    selectedOrder: Int = -1,
    showSelect: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val ext = file?.extension?.lowercase().orEmpty()
    val isVideo = !isDir && ext in VIDEO_EXTS
    val isImage = !isDir && ext in IMAGE_EXTS
    val isAudio = !isDir && ext in AUDIO_EXTS
    val preview = rememberPreview(file, isVideo || isImage || isAudio)
    val selected = selectedOrder >= 0
    val meta = listOfNotNull(size, preview?.durationMs?.takeIf { it > 0 }?.let(::formatDuration)).joinToString("  •  ")

    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { cb -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); cb() } },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            val thumb = preview?.thumb
            when {
                isDir -> Icon(Icons.Rounded.Folder, null, Modifier.size(52.dp), tint = FolderOrange)
                thumb != null -> {
                    Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (isVideo) Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp), tint = Color.White) }
                }
                else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    Icon(
                        when {
                            live -> Icons.Rounded.MotionPhotosOn
                            isVideo -> Icons.Rounded.Movie
                            isImage -> Icons.Rounded.Image
                            isAudio -> Icons.Rounded.AudioFile
                            ext in SUBTITLE_EXTS -> Icons.Rounded.ClosedCaption
                            else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                        },
                        null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("LIVE", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                if (live && meta.isNotEmpty()) Spacer(Modifier.width(6.dp))
                Text(
                    meta, Modifier.weight(1f, fill = true), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (showSelect) {
            Spacer(Modifier.width(10.dp))
            if (selected) Box(
                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Text("${selectedOrder + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary) }
            else Icon(Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** The ".." row: a folder with an up arrow, shown first in every folder, including empty ones. */
@Composable
fun UpFolderRow(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DriveFolderUpload, null, Modifier.size(52.dp), tint = FolderOrange)
        }
        Text("..", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
