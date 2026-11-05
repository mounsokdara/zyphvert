# Zyphvert

All-in-one **offline media converter and editor** for Android, powered by FFmpeg.
Native Kotlin and Jetpack Compose. No ads, no tracking, no account. The app does not
request the `INTERNET` permission, so your files never leave your phone.

## Features

### Convert
| Tool | What it does |
|---|---|
| **Video converter** | Convert any video to MP4, MKV, WebM, MOV, AVI, GIF, WebP, MP3, M4A, WAV, FLAC, or an image sequence |
| **Audio converter** | Convert any audio to MP3, M4A, AAC, WAV, FLAC, OGG, OPUS, or a video |
| **Image converter** | Convert any image, including HEIC and Live Photos, to JPG, PNG, WebP, BMP, GIF, or a short video |

### Edit
| Tool | What it does |
|---|---|
| **Trim & Cut** | Cut a clip to the part you want |
| **Crop & Rotate** | Crop the frame and rotate or flip |
| **Speed & Reverse** | Slow down, speed up or reverse playback |
| **Compress** | Shrink a video, audio file, or image |
| **Merge** | Join two videos or two audio files |
| **Volume** | Make a clip louder, quieter, or silent |
| **Subtitles** | Extract the first subtitle track as SRT |
| **Filters** | Apply visual filters to video and images |
| **Watermark** | Overlay a logo or text on your media |

### Metadata and privacy
| Tool | What it does |
|---|---|
| **Metadata Editor** | View and edit media metadata |
| **Privacy Scrub** | Strip location and device info before sharing |

### Create
| Tool | What it does |
|---|---|
| **Sticker Maker** | Make stickers from images and clips |
| **Ringtone Maker** | Cut audio into a ringtone |

### Power tools
| Tool | What it does |
|---|---|
| **Batch Queue** | Queue many conversions and run them in the background |
| **Custom FFmpeg** | Run your own FFmpeg command for anything not covered above |

### Principles
- **Fully offline:** all processing happens on the device.
- **No ads, no tracking, no analytics.**
- **All-files access:** optional `MANAGE_EXTERNAL_STORAGE` so tools can use any path; SAF still works without it.
- **Any file format:** pickers accept `*/*` — no dummy MIME filters.
- **Per-ABI builds:** `arm64-v8a`, `armeabi-v7a`, `x86_64`, plus a universal APK. Android 7.0+ (minSdk 24).
- **Adaptive layout:** bottom bar on phones, navigation rail and width-capped content on tablets.

## Roadmap

- [x] App shell: Material 3 Compose UI with the tool-grid home screen
- [x] CI: lint, unit tests and debug build on every push and PR
- [x] Release pipeline: signed per-ABI and universal APKs, AAB, signature verification, SHA256 checksums, GitHub Release on `v*.*.*` tags
- [x] Real converter / editor UIs with advanced options, wired to `MediaEngine`
- [x] All-files access + any-format file pickers
- [ ] FFmpeg native build pipeline (AAR). Status in [docs/FFMPEG.md](docs/FFMPEG.md)
- [ ] First real tool smoke test once AAR is present (ffprobe media-info)
- [ ] Batch queue + richer watermark / multi-input tools

## Tech stack

Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Coroutines/Flow, Room,
WorkManager, Media3, FFmpegKitNext (when AAR is present).
