#!/usr/bin/env bash
# Fails unless every given APK has exactly the versionName and versionCode from version.txt.
# Usage: check-version.sh <apk>...   (aapt2 from Android build-tools must be on PATH)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAME="$(grep -m1 '^VERSION_NAME=' "$ROOT/version.txt" | cut -d= -f2 | tr -d '[:space:]')"
CODE="$(grep -m1 '^VERSION_CODE=' "$ROOT/version.txt" | cut -d= -f2 | tr -d '[:space:]')"
[ -n "$NAME" ] && [ -n "$CODE" ] || { echo "::error::version.txt is missing VERSION_NAME or VERSION_CODE"; exit 1; }
for apk in "$@"; do
  LINE="$(aapt2 dump badging "$apk" | grep -m1 '^package:')"
  GOT_NAME="$(sed -E "s/.*versionName='([^']*)'.*/\1/" <<<"$LINE")"
  GOT_CODE="$(sed -E "s/.*versionCode='([^']*)'.*/\1/" <<<"$LINE")"
  if [ "$GOT_NAME" != "$NAME" ] || [ "$GOT_CODE" != "$CODE" ]; then
    echo "::error::$(basename "$apk") is v$GOT_NAME ($GOT_CODE), version.txt says v$NAME ($CODE)"; exit 1
  fi
  echo "version OK: $(basename "$apk") = v$NAME ($CODE)"
done
