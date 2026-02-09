#!/usr/bin/env bash
# Build Clara Rules Inspector for Windows (self-contained app-image with bundled JRE).
# Run on Windows (e.g. Git Bash or WSL) with JDK 17+ that includes JavaFX (e.g. Zulu JDK FX).
# Prerequisites: clj, jpackage (JDK 14+), JavaFX on module path.

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_DIR="$PROJECT_ROOT/dist"
JAR_NAME="clara-inspect-fx-standalone.jar"
APP_NAME="Clara-Inspect-Fx"

cd "$PROJECT_ROOT"

echo "== Building uberjar =="
clj -T:build uber

JAR_PATH="$PROJECT_ROOT/target/$JAR_NAME"
test -f "$JAR_PATH" || { echo "Uberjar not found: $JAR_PATH"; exit 1; }

echo "== Packaging Windows app-image (jpackage) =="
mkdir -p "$DIST_DIR"
rm -rf "$DIST_DIR/$APP_NAME" "$DIST_DIR/${APP_NAME}.exe"

jpackage --type app-image \
  --name "$APP_NAME" \
  --input "$PROJECT_ROOT/target" \
  --main-jar "$JAR_NAME" \
  --main-class clara_inspect_fx.main \
  --dest "$DIST_DIR" \
  --app-version 0.1.0

echo "== Windows app-image ready at $DIST_DIR/$APP_NAME =="
echo "Run: $DIST_DIR/$APP_NAME/$APP_NAME.exe"
if command -v zip &>/dev/null; then
  ZIP="$DIST_DIR/Clara-Inspect-Fx-windows-x86_64.zip"
  (cd "$DIST_DIR" && zip -r "$ZIP" "$APP_NAME")
  echo "ZIP: $ZIP"
fi
