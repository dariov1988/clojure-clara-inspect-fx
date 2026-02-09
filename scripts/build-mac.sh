#!/usr/bin/env bash
# Build Clara Rules Inspector for macOS (self-contained .app and optional .dmg).
# Run on macOS with JDK 17+ that includes JavaFX (e.g. Zulu JDK FX, or Azul).
# Prerequisites: clj, jpackage (JDK 14+), JavaFX on module path.
#
# Usage:
#   ./build-mac.sh           # .app bundle only
#   ./build-mac.sh --dmg     # .app + .dmg disk image

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_DIR="$PROJECT_ROOT/dist"
JAR_NAME="clara-inspect-fx-standalone.jar"
APP_NAME="Clara-Inspect-Fx"
BUILD_DMG=false
for a in "$@"; do [ "$a" = "--dmg" ] && BUILD_DMG=true; done

cd "$PROJECT_ROOT"

JAR_PATH="$PROJECT_ROOT/target/$JAR_NAME"
if [ ! -f "$JAR_PATH" ]; then
  echo "== Building uberjar =="
  clojure -T:build uber
fi
test -f "$JAR_PATH" || { echo "Uberjar not found: $JAR_PATH"; exit 1; }

echo "== Packaging macOS app-image (jpackage) =="
mkdir -p "$DIST_DIR"
rm -rf "$DIST_DIR/$APP_NAME.app"

jpackage --type app-image \
  --name "$APP_NAME" \
  --input "$PROJECT_ROOT/target" \
  --main-jar "$JAR_NAME" \
  --main-class clara_inspect_fx.main \
  --dest "$DIST_DIR" \
  --app-version 1.0.0

echo "== macOS app ready at $DIST_DIR/$APP_NAME.app =="
echo "Run: open $DIST_DIR/$APP_NAME.app"

if [ "$BUILD_DMG" = true ]; then
  echo "== Creating DMG =="
  jpackage --type dmg \
    --name "$APP_NAME" \
    --input "$PROJECT_ROOT/target" \
    --main-jar "$JAR_NAME" \
    --main-class clara_inspect_fx.main \
    --dest "$DIST_DIR" \
    --app-version 1.0.0
  # jpackage creates e.g. Clara-Inspect-Fx-1.0.0.dmg; rename to include arch for releases
  ARCH=$(uname -m)
  SRC=$(ls "$DIST_DIR"/Clara-Inspect-Fx-*.dmg 2>/dev/null | head -1)
  if [ -n "$SRC" ]; then
    DEST="$DIST_DIR/Clara-Inspect-Fx-macos-${ARCH}.dmg"
    mv "$SRC" "$DEST"
    echo "DMG: $DEST"
  fi
fi
