package com.mounsokdara.zyphvert.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolCategory(val label: String) {
    Convert("Convert"),
    Edit("Edit"),
    Privacy("Metadata & privacy"),
    Create("Create"),
    Power("Power tools"),
}

/** Container / content color pair so each category reads as its own tonal family. */
data class Accent(val container: Color, val content: Color)

@Composable
fun ToolCategory.accent(): Accent {
    val c = MaterialTheme.colorScheme
    return when (this) {
        ToolCategory.Convert -> Accent(c.primaryContainer, c.onPrimaryContainer)
        ToolCategory.Edit -> Accent(c.secondaryContainer, c.onSecondaryContainer)
        ToolCategory.Privacy -> Accent(c.tertiaryContainer, c.onTertiaryContainer)
        ToolCategory.Create -> Accent(c.primaryContainer, c.onPrimaryContainer)
        ToolCategory.Power -> Accent(c.inverseSurface, c.inverseOnSurface)
    }
}

data class Tool(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: ToolCategory,
    /** True = tool has a real options UI and runs FFmpeg. */
    val available: Boolean = true,
    /** Short format labels shown on converter cards. */
    val formats: List<String> = emptyList(),
)

val allTools: List<Tool> = listOf(
    Tool(
        "video",
        "Video converter",
        "Convert any video to whatever you need: another video, audio, a GIF, or images.",
        Icons.Rounded.Movie,
        ToolCategory.Convert,
        formats = listOf("MP4", "MKV", "MOV", "WebM", "AVI", "WMV", "3GP", "TS", "FLV", "MPG", "GIF", "WebP", "APNG", "Live Photo", "Motion Photo", "Images", "Remux"),
    ),
    Tool(
        "audio",
        "Audio converter",
        "Convert any audio to whatever you need: MP3, AAC, WAV, FLAC, OGG, OPUS, or a video.",
        Icons.Rounded.GraphicEq,
        ToolCategory.Convert,
        formats = listOf("MP3", "AAC", "M4A", "M4B", "OPUS", "OGG", "TG VOICE", "OGA", "MKA", "FLAC", "WAV", "ALAC", "AIFF", "WMA", "WV", "AC3"),
    ),
    Tool(
        "image",
        "Image converter",
        "Convert any image, including HEIC and Live Photos, to JPG, PNG, WebP, GIF, or a video.",
        Icons.Rounded.Image,
        ToolCategory.Convert,
        formats = listOf("JPG", "PNG", "WebP", "BMP", "TIFF", "GIF", "JP2", "TGA", "ICO", "QOI", "Live Photo"),
    ),

    Tool("trim", "Trim & Cut", "Cut a clip down to the part you want", Icons.Rounded.ContentCut, ToolCategory.Edit),
    Tool("crop", "Crop & Rotate", "Crop the frame, rotate, or flip", Icons.Rounded.CropRotate, ToolCategory.Edit),
    Tool("speed", "Speed & Reverse", "Slow down, speed up, or reverse", Icons.Rounded.Speed, ToolCategory.Edit),
    Tool("compress", "Compress", "Make a video, audio file, or image smaller before you share it", Icons.Rounded.Compress, ToolCategory.Edit),
    Tool("merge", "Merge", "Join two videos, or two audio files, into one", Icons.Rounded.LibraryAdd, ToolCategory.Edit),
    Tool("volume", "Volume", "Make a clip louder, quieter, or silent", Icons.Rounded.VolumeUp, ToolCategory.Edit),
    Tool("subs", "Subtitles", "Pull the subtitle track out of a video as SRT", Icons.Rounded.ClosedCaption, ToolCategory.Edit),
    Tool("filters", "Filters", "Resize and recolor video or images", Icons.Rounded.Tune, ToolCategory.Edit),
    Tool("wm", "Watermark", "Overlay a logo or text on your media", Icons.Rounded.WaterDrop, ToolCategory.Edit),

    Tool("meta", "Metadata Editor", "View and edit media metadata", Icons.Rounded.Info, ToolCategory.Privacy),
    Tool("scrub", "Privacy Scrub", "Strip location and device info before sharing", Icons.Rounded.VisibilityOff, ToolCategory.Privacy),

    Tool("slideshow", "Slideshow maker", "Turn photos into a video with transitions and music", Icons.Rounded.Slideshow, ToolCategory.Create),
    Tool("live", "Live/Motion Photos", "Get the video out of a Live or Motion Photo", Icons.Rounded.MotionPhotosOn, ToolCategory.Create),
    Tool("sticker", "Sticker Maker", "Make stickers from images and clips", Icons.Rounded.EmojiEmotions, ToolCategory.Create),
    Tool("ringtone", "Ringtone Maker", "Cut audio into a ringtone", Icons.Rounded.RingVolume, ToolCategory.Create),

    Tool("batch", "Batch Queue", "Queue many conversions and run them in the background", Icons.Rounded.Layers, ToolCategory.Power),
    Tool("ffmpeg", "Custom FFmpeg", "Run your own FFmpeg command", Icons.Rounded.Terminal, ToolCategory.Power),
)
