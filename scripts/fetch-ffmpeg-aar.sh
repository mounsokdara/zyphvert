#!/usr/bin/env bash
# Download the prebuilt FFmpegKitNext AAR (built by ffmpeg-aar.yml) into app/libs.
# Soft-fails if it has not been built yet, so CI stays green until the first AAR exists.
set -uo pipefail
TAG="${FFMPEG_AAR_TAG:-ffmpeg-kit-aar-v9.0.2}"
mkdir -p app/libs
if gh release download "$TAG" -p ffmpeg-kit-next.aar -D app/libs --clobber -R "${GITHUB_REPOSITORY:-mounsokdara/zyphvert}"; then
  ls -la app/libs
else
  echo "::warning::FFmpeg AAR '$TAG' not published yet. Run the 'Build FFmpegKitNext AAR' workflow."
fi
exit 0
