package com.mounsokdara.zyphvert.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
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
    /** Flip to true as each tool ships; hides the "Soon" badge. */
    val available: Boolean = false,
)

val allTools: List<Tool> = listOf(
    Tool("img2vid", "Image → Video", "Turn photos into a video or slideshow", Icons.Rounded.Slideshow, ToolCategory.Convert),
    Tool("vid2img", "Video → Images", "Export frames from a video as images", Icons.Rounded.PhotoLibrary, ToolCategory.Convert),
    Tool("vid2aud", "Video → Audio", "Extract the soundtrack from a video", Icons.Rounded.GraphicEq, ToolCategory.Convert),
    Tool("vid2gif", "Video → GIF/WebP", "Make an animated GIF or WebP from a clip", Icons.Rounded.Gif, ToolCategory.Convert),
    Tool("aud2vid", "Audio → Video", "Combine audio with a picture or clip", Icons.Rounded.Movie, ToolCategory.Convert),
    Tool("live", "Live/Motion Photos", "Convert to regular video or images", Icons.Rounded.MotionPhotosOn, ToolCategory.Convert),
    Tool("heic", "HEIC/AVIF Convert", "Convert to JPG, PNG and other formats", Icons.Rounded.Image, ToolCategory.Convert),

    Tool("trim", "Trim & Cut", "Cut a clip to the part you want", Icons.Rounded.ContentCut, ToolCategory.Edit),
    Tool("crop", "Crop & Rotate", "Crop the frame, rotate or flip", Icons.Rounded.CropRotate, ToolCategory.Edit),
    Tool("speed", "Speed & Reverse", "Slow down, speed up or reverse", Icons.Rounded.Speed, ToolCategory.Edit),
    Tool("filters", "Filters", "Apply visual filters to video and images", Icons.Rounded.Tune, ToolCategory.Edit),
    Tool("wm", "Watermark", "Overlay a logo or text on your media", Icons.Rounded.WaterDrop, ToolCategory.Edit),

    Tool("meta", "Metadata Editor", "View and edit media metadata", Icons.Rounded.Info, ToolCategory.Privacy),
    Tool("scrub", "Privacy Scrub", "Strip location and device info before sharing", Icons.Rounded.VisibilityOff, ToolCategory.Privacy),

    Tool("sticker", "Sticker Maker", "Make stickers from images and clips", Icons.Rounded.EmojiEmotions, ToolCategory.Create),
    Tool("ringtone", "Ringtone Maker", "Cut audio into a ringtone", Icons.Rounded.RingVolume, ToolCategory.Create),

    Tool("batch", "Batch Queue", "Queue many conversions and run them in the background", Icons.Rounded.Layers, ToolCategory.Power),
    Tool("ffmpeg", "Custom FFmpeg", "Run your own FFmpeg command", Icons.Rounded.Terminal, ToolCategory.Power),
)
