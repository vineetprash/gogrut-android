#!/usr/bin/env bash
# Bump NewPipeExtractor to its newest release. Run this when YouTube changes and downloads start failing.
set -euo pipefail
cd "$(dirname "$0")/.."
LATEST=$(curl -fsSL https://api.github.com/repos/TeamNewPipe/NewPipeExtractor/releases/latest | grep -m1 '"tag_name"' | cut -d'"' -f4)
[ -n "$LATEST" ] || { echo "couldn't read latest release"; exit 1; }
CURRENT=$(grep -E '^newpipe-extractor' gradle/libs.versions.toml | cut -d'"' -f2)
echo "extractor: $CURRENT -> $LATEST"
[ "$CURRENT" = "$LATEST" ] && { echo "already up to date"; exit 0; }
sed -i "s/^newpipe-extractor = \".*\"/newpipe-extractor = \"$LATEST\"/" gradle/libs.versions.toml
echo "updated. Now run:  ./gradlew assembleRelease   (APK: app/build/outputs/apk/release/)"
