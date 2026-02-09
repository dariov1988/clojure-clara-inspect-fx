#!/usr/bin/env bash
# Build Clara Rules Inspector as a Linux AppImage.
# Prerequisites: clj, Java 11+ with JavaFX, appimagetool (optional, for .AppImage file)
#   appimagetool: https://github.com/AppImage/AppImageKit/releases
#
# Usage:
#   ./build-appimage.sh           # AppDir + optional .AppImage (uses system Java)
#   ./build-appimage.sh --bundle-jre  # Bundle JRE via jpackage (fully self-contained, needs JDK 17+ with JavaFX)

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_NAME="clara-inspect-fx"
DIST_DIR="$PROJECT_ROOT/dist"
APPDIR="$DIST_DIR/AppDir"
JAR_NAME="clara-inspect-fx-standalone.jar"
BUNDLE_JRE=false
for a in "$@"; do [ "$a" = "--bundle-jre" ] && BUNDLE_JRE=true; done

cd "$PROJECT_ROOT"

echo "== Building uberjar =="
clj -T:build uber

JAR_PATH="$PROJECT_ROOT/target/$JAR_NAME"
test -f "$JAR_PATH" || { echo "Uberjar not found: $JAR_PATH"; exit 1; }

echo "== Preparing AppDir =="
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/lib/$APP_NAME"

if [ "$BUNDLE_JRE" = true ] && command -v jpackage &>/dev/null; then
  echo "== Bundling JRE with jpackage =="
  JPACKAGE_APP="$DIST_DIR/Clara-Inspect-Fx"
  rm -rf "$JPACKAGE_APP"
  jpackage --type app-image \
    --name Clara-Inspect-Fx \
    --input "$PROJECT_ROOT/target" \
    --main-jar "$JAR_NAME" \
    --main-class clara_inspect_fx.main \
    --dest "$DIST_DIR" \
    --app-version 0.1.0
  # Wrap jpackage output in AppDir: move app into usr/
  mv "$JPACKAGE_APP" "$APPDIR/usr/"
  # AppRun delegates to jpackage launcher
  cat > "$APPDIR/AppRun" << 'APPRUN'
#!/usr/bin/env bash
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/Clara-Inspect-Fx/bin/Clara-Inspect-Fx" "$@"
APPRUN
  chmod +x "$APPDIR/AppRun"
else
  cp "$JAR_PATH" "$APPDIR/usr/lib/$APP_NAME/"
  # AppRun: use JAVA_HOME or system java
  cat > "$APPDIR/AppRun" << 'APPRUN'
#!/usr/bin/env bash
HERE="$(dirname "$(readlink -f "$0")")"
export JAVA_HOME="${JAVA_HOME:-}"
if [ -n "$JAVA_HOME" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="$(command -v java 2>/dev/null)" || JAVA="java"
fi
exec "$JAVA" -jar "$HERE/usr/lib/clara-inspect-fx/clara-inspect-fx-standalone.jar" "$@"
APPRUN
  chmod +x "$APPDIR/AppRun"
fi

# Desktop entry (Exec=AppRun so the desktop finds the binary in AppDir root)
mkdir -p "$APPDIR/usr/share/applications"
cat > "$APPDIR/clara-inspect-fx.desktop" << 'DESKTOP'
[Desktop Entry]
Name=Clara Rules Inspector
Comment=Inspect Clara rules and facts with JavaFX
Exec=AppRun
Icon=clara-inspect-fx
Terminal=false
Type=Application
Categories=Development;
DESKTOP

# Symlink desktop to standard location
ln -sf ../../clara-inspect-fx.desktop "$APPDIR/usr/share/applications/"

# Icon: use a placeholder if no icon exists
ICON_SRC="$PROJECT_ROOT/resources/clara_inspect_fx/icon.png"
if [ -f "$ICON_SRC" ]; then
  mkdir -p "$APPDIR/usr/share/icons/hicolor/256x256/apps"
  cp "$ICON_SRC" "$APPDIR/usr/share/icons/hicolor/256x256/apps/clara-inspect-fx.png"
  cp "$ICON_SRC" "$APPDIR/clara-inspect-fx.png"
  cp "$ICON_SRC" "$APPDIR/.DirIcon"
else
  echo "Note: Add resources/clara_inspect_fx/icon.png (256x256) for a proper icon."
  # Minimal 1x1 PNG as .DirIcon so appimagetool doesn't complain
  printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82' > "$APPDIR/.DirIcon"
  cp "$APPDIR/.DirIcon" "$APPDIR/clara-inspect-fx.png"
fi

echo "== AppDir ready at $APPDIR =="
echo "Run the app: $APPDIR/AppRun"
echo ""

# Build final .AppImage if appimagetool is available
APPIMAGETOOL=""
for p in appimagetool ./appimagetool-x86_64.AppImage; do
  if command -v $p &>/dev/null; then APPIMAGETOOL="$p"; break; fi
done
if [ -n "$APPIMAGETOOL" ]; then
  echo "== Creating AppImage =="
  OUTPUT="$DIST_DIR/Clara-Inspect-Fx-x86_64.AppImage"
  $APPIMAGETOOL "$APPDIR" "$OUTPUT"
  echo "Created: $OUTPUT"
else
  echo "To create a single .AppImage file, install appimagetool and re-run:"
  echo "  https://github.com/AppImage/AppImageKit/releases"
  echo "  Then: appimagetool $APPDIR $DIST_DIR/Clara-Inspect-Fx-x86_64.AppImage"
fi
