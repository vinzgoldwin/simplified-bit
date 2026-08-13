#!/bin/zsh
set -euo pipefail

SCRIPT_DIR=${0:a:h}
cd "$SCRIPT_DIR"
swift build -c release

APP_DIR="$SCRIPT_DIR/build/Simplified Fit Companion.app"
mkdir -p "$APP_DIR/Contents/MacOS"
cp "$SCRIPT_DIR/.build/release/SimplifiedFitCompanion" "$APP_DIR/Contents/MacOS/SimplifiedFitCompanion"
cp "$SCRIPT_DIR/Info.plist" "$APP_DIR/Contents/Info.plist"
codesign --force --deep --sign - "$APP_DIR"
echo "$APP_DIR"
