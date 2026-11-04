# FFmpeg integration — status

## Decision: FFmpegKitNext (official), not a Maven dependency

The original `arthenica/ffmpeg-kit` was archived in April 2025. Its own README
points to **FFmpegKitNext** (`arthenica/ffmpeg-kit-next`) as the official
continuation, maintained by the same author, with active releases (latest at
time of writing: 9.0.0, Aug 2026).

Avoid `dev.ffmpegkit-maintained:*` (branded "ffmpegkit-maintained", several
paid tiers sold via Gumroad, maintainer "Jokobee"). Multiple inconsistent
descriptions of that project turned up during research (conflicting tier
counts, conflicting distribution claims) — treat it as unverified and do not
add it as a dependency without a lot more diligence than a web search can
give. FFmpegKitNext is the trustworthy option: same original author, source
available, no opaque paid binary blobs.

## What this means for the build

FFmpegKitNext is **source-distributed only** — there's no simple
`implementation("...")` line. The plan:

1. `scripts/build-ffmpeg-kit.sh` (not yet written — next milestone step)
   clones `arthenica/ffmpeg-kit-next`, runs its Android build script for
   `arm64-v8a` / `armeabi-v7a` / `x86_64`, and produces `.aar` output.
2. The built `.aar` lands in `app/libs/` (gitignored — built in CI, not
   committed) and is referenced via the `flatDir` repo in
   `settings.gradle.kts`.
3. `app/build.gradle.kts` has the dependency line commented out
   (`implementation(files("libs/ffmpeg-kit-next-release.aar"))`) until the
   build script exists and CI can produce that file reliably.
4. License: confirm GPL vs LGPL build config before enabling any GPL-only
   codecs (x264/x265/etc.) — ties into the About-screen license display
   requirement in the spec. Default to LGPL-safe codec set unless a GPL
   build is explicitly chosen.

## Status

- [x] Confirmed a trustworthy source for FFmpeg on Android (this doc)
- [ ] `scripts/build-ffmpeg-kit.sh` written and producing a working `.aar`
- [ ] CI step added to `ci.yml` to build/cache the `.aar` before compiling
- [ ] First real FFmpeg call wired into the app (ffprobe media-info screen is
      the simplest first integration test)

This file should be updated as each box is checked — treat it as the source
of truth for where FFmpeg integration stands, since it's explicitly called
out as a "confirm before building features" blocker in the project spec.
