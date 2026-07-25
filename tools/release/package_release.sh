#!/usr/bin/env bash
#
# Packages the release APKs under names an ordinary user can understand.
#
# Gradle names its outputs after the CPU architecture (app-arm64-v8a-release.apk),
# which is the right convention inside the build but is meaningless to someone who
# just wants legal help on their phone. This script copies the build outputs to
# self-explaining names, in a numbered order so the recommended download appears
# first in the GitHub release listing, and writes SHA-256 checksums.
#
# Usage:
#   ./gradlew assembleRelease
#   tools/release/package_release.sh [output-dir]
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APK_DIR="$REPO_ROOT/app/build/outputs/apk/release"
OUT_DIR="${1:-$REPO_ROOT/build/release-downloads}"

# Version name, read from the build file so the two can never drift apart.
VERSION="$(grep -oE 'versionName = "[^"]+"' "$REPO_ROOT/app/build.gradle.kts" \
  | head -1 | sed 's/.*"\(.*\)"/\1/')"
if [[ -z "$VERSION" ]]; then
  echo "could not read versionName from app/build.gradle.kts" >&2
  exit 1
fi

BASE="Nyaya-AI-v${VERSION}"

# gradle-output-name : published-name
#
# The numeric prefix controls the order users see. arm64-v8a is first because it
# is the correct choice for essentially every phone sold in the last several
# years; the universal build is the fallback when a device refuses that one.
# x86/x86_64 exist for emulators and a few ChromeOS devices, so they are labelled
# as such rather than left looking like an equally valid option.
MAPPINGS=(
  "app-arm64-v8a-release.apk:${BASE}-1-RECOMMENDED-most-phones.apk"
  "app-universal-release.apk:${BASE}-2-BACKUP-works-on-all-phones.apk"
  "app-armeabi-v7a-release.apk:${BASE}-3-old-32-bit-phones.apk"
  "app-x86_64-release.apk:${BASE}-4-emulator-and-ChromeOS-64-bit.apk"
  "app-x86-release.apk:${BASE}-5-emulator-32-bit.apk"
)

if [[ ! -d "$APK_DIR" ]]; then
  echo "no release APKs found at $APK_DIR — run ./gradlew assembleRelease first" >&2
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

missing=0
for entry in "${MAPPINGS[@]}"; do
  src="${entry%%:*}"
  dst="${entry##*:}"
  if [[ -f "$APK_DIR/$src" ]]; then
    cp "$APK_DIR/$src" "$OUT_DIR/$dst"
    printf '  %-52s %6.1f MB\n' "$dst" "$(du -b "$OUT_DIR/$dst" | cut -f1 | awk '{print $1/1048576}')"
  else
    echo "  MISSING: $src" >&2
    missing=1
  fi
done

( cd "$OUT_DIR" && sha256sum ./*.apk | sed 's|\./||' > SHA256SUMS.txt )
echo
echo "packaged into $OUT_DIR"
echo "checksums written to $OUT_DIR/SHA256SUMS.txt"
echo
echo "REMINDER: the download buttons in README.md point at a pinned release tag,"
echo "because the file names contain the version. After publishing this release,"
echo "update the v… tag and the ${BASE} file names in README.md so the buttons"
echo "keep working:"
echo "    grep -n 'releases/download' README.md"
exit $missing
