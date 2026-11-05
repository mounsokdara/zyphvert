# Zyphvert

All-in-one **offline media converter and editor** for Android, powered by FFmpeg.
Native Kotlin and Jetpack Compose. No ads, no tracking, no account. The app does not
request the `INTERNET` permission, so your files never leave your phone.

## Features

### Convert: any file to any format
| Tool | What it does |
|---|---|
| **Video converter** | Any video to MP4, M4V, MKV, MOV, WebM, AVI, WMV, 3GP, TS, FLV, MPG, GIF, animated WebP/PNG, image frames, a Live Photo pair (JPG + MOV), an Android Motion Photo (one JPG with the video inside), or a lossless remux. Presets for WhatsApp, Discord (8 MB), TikTok, YouTube, Instagram and Telegram. Resolution, frame rate, quality, audio, trim and target file size. |
| **Audio converter** | Any audio or video to MP3, AAC, M4A, M4B, M4R, Opus, OGG, OGA, **Telegram voice (OGG Opus, mono 48 kHz)**, MKA, WAV, WavPack, FLAC, ALAC, AIFF, WMA, AC3, EAC3, MP2, AU, CAF, W64, TTA or Speex. Conversion templates, CBR/ABR/VBR bitrate, sample rate, channels, trim or cut out a section, normalize, fades, full tag editor and cover art. |
| **Image converter** | Any image (HEIC, HEIF, AVIF, JPG, PNG, WebP, BMP, TIFF, GIF) to JPG, PNG, WebP, BMP, TIFF, GIF, JPEG 2000, TGA, PPM, ICO or QOI, with quality and resize. Live and Motion Photos can become an MP4, GIF or animated WebP. |

### Edit
| Tool | What it does |
|---|---|
| **Trim & Cut** | Keep or remove any part of a video or audio file, lossless or precise |
| **Crop & Rotate** | Crop to a ratio, rotate, flip |
| **Speed & Reverse** | Slow down, speed up or reverse, with natural audio pitch |
| **Filters** | Colour and effect filters for video and photos |
| **Watermark** | Text or logo overlay |

### Metadata and privacy
| Tool | What it does |
|---|---|
| **Tag editor** | Edit title, artist, album, genre and more, set cover art, or view every detail of a file |
| **Privacy Scrub** | Strip GPS, device info and all hidden tags without re-encoding |

### Create
| Tool | What it does |
|---|---|
| **Slideshow maker** | Photos to video with transitions and music |
| **Live/Motion Photos** | Extract the video, make a GIF or grab a still |
| **Sticker Maker** | 512x512 animated WebP stickers |
| **Ringtone Maker** | Cut a song into a ringtone saved to your Ringtones folder |

### Power tools
| Tool | What it does |
|---|---|
| **Batch converter** | Convert many files at once; each becomes a queued background job |
| **Custom FFmpeg** | Run your own FFmpeg or ffprobe command |

Conversions run one after another in the background with a notification, a **Queue** screen
with progress, cancel, open and share, and plain-words error messages.

### Principles
- **Fully offline:** all processing happens on the device.
- **No ads, no tracking, no analytics.**
- **All-files access:** optional `MANAGE_EXTERNAL_STORAGE` so tools can use any path; SAF still works without it.
- **Any file format:** pickers accept `*/*` — no dummy MIME filters.
- **Per-ABI builds:** `arm64-v8a`, `armeabi-v7a`, `x86_64`, plus a universal APK. Android 7.0+ (minSdk 24).
- **Adaptive layout:** bottom bar on phones, navigation rail and width-capped content on tablets.

## Roadmap

- [x] App shell: Material 3 Compose UI with the tool-grid home screen
- [x] CI: lint, unit tests and a signed release build (v1 + v2 + v3) on every push; no debug builds
- [x] Release pipeline: signed per-ABI and universal APKs, AAB, signature verification, SHA256 checksums, GitHub Release on `v*.*.*` tags
- [x] Real converter / editor UIs with advanced options, wired to `MediaEngine`
- [x] All-files access + any-format file pickers
- [ ] FFmpeg native build pipeline (AAR). Status in [docs/FFMPEG.md](docs/FFMPEG.md)
- [ ] First real tool smoke test once AAR is present (ffprobe media-info)
- [ ] Batch queue + richer watermark / multi-input tools

## Tech stack

Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Coroutines/Flow, Room,
WorkManager, Media3, FFmpegKitNext (when AAR is present).
