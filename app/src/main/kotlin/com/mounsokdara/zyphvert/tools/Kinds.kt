package com.mounsokdara.zyphvert.tools

/** Everything FFmpeg (or Android's own decoders) can usually read, grouped for the file picker. */
val VIDEO_EXTS = setOf(
    "mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "3g2", "flv", "f4v", "wmv", "asf", "mpg", "mpeg", "mpe",
    "ts", "m2ts", "mts", "vob", "ogv", "rm", "rmvb", "divx", "mxf", "dv", "y4m", "gifv",
)
val AUDIO_EXTS = setOf(
    "mp3", "m4a", "m4b", "m4r", "aac", "flac", "wav", "wave", "ogg", "oga", "opus", "wma", "aiff", "aif", "aifc",
    "amr", "awb", "mka", "ac3", "eac3", "mp2", "au", "caf", "w64", "tta", "spx", "wv", "ape", "dts", "mid", "midi",
)
val IMAGE_EXTS = setOf(
    "jpg", "jpeg", "jfif", "png", "webp", "bmp", "gif", "heic", "heif", "avif", "tif", "tiff", "ico", "jxl",
    "dng", "cr2", "cr3", "nef", "arw", "raf", "orf", "rw2", "srw", "pef",
)
val SUBTITLE_EXTS = setOf("srt", "vtt", "ass", "ssa", "sub")

/** Extensions an iPhone Live Photo stores its motion half in. */
val LIVE_VIDEO_EXTS = setOf("mov", "mp4")
/** Stills that can carry a motion half (HEIC/JPG pair on iPhone, JPG with embedded MP4 on Android). */
val LIVE_STILL_EXTS = setOf("heic", "heif", "jpg", "jpeg")
