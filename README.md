# Zyphvert

All-in-one offline media converter and editor for Android, powered by FFmpeg.
Native Kotlin + Jetpack Compose. Zero ads, zero tracking, fully offline.

## Status: Milestone 1 — setup, signing, CI

- [x] Project scaffold (Kotlin/Compose, Hilt, Room, WorkManager, Media3 deps wired)
- [x] Signing: keystore generated, encrypted backup pushed to private vault repo,
      signer script reused from vault (`scripts/sign-apk.sh`)
- [x] `ci.yml` — lint, unit tests, debug build on every push/PR
- [x] `release.yml` — signed per-ABI + universal APKs, AAB, apksigner
      verification, SHA256 checksums, GitHub Release on `v*.*.*` tags
- [ ] FFmpeg native build pipeline — see [docs/FFMPEG.md](docs/FFMPEG.md) for
      current status; this is the blocker before any conversion feature work
      starts

## Tech stack

Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Coroutines/Flow, Room,
WorkManager + foreground service, Media3, scoped storage only (Photo Picker /
SAF / MediaStore). minSdk 24.

## Building locally

```
./gradlew assembleDebug
```

Release builds require `key.properties` at the repo root (gitignored, never
commit it) pointing at a local copy of the signing keystore. In CI this is
generated at runtime from GitHub Actions secrets — see `release.yml`.

## Required GitHub Actions secrets (for release builds)

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | base64-encoded `com.mounsokdara.zyphvert.p12` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `com.mounsokdara.zyphvert` |
| `KEY_PASSWORD` | same as `KEYSTORE_PASSWORD` (PKCS12 requirement) |

Add these under **Settings → Secrets and variables → Actions → New repository
secret** on this repo. The keystore itself is backed up, encrypted, in the
private vault repo — see that repo's `backups/zyphvert/` for the encrypted
bundle and its README for the restore command.
