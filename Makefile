# Clara Rules Inspector — build and run
# Use: make [target]. On Windows use Git Bash or WSL to run make, or run scripts in scripts/ directly.

.DEFAULT_GOAL := help
SHELL := /bin/bash
SCRIPTS := scripts
DIST := dist
TARGET := target

.PHONY: help run repl deps uberjar clean \
	dist-linux dist-linux-bundle dist-windows dist-mac dist-mac-dmg \
	dist-all lint

help:
	@echo "Clara Rules Inspector — targets:"
	@echo "  run              Run app (clj -M:main)"
	@echo "  repl             Start REPL (clj -M:repl)"
	@echo "  deps             Download dependencies (clj -P)"
	@echo "  uberjar          Build standalone JAR (target/clara-inspect-fx-standalone.jar)"
	@echo "  clean            Remove target/ and dist/"
	@echo "  dist-linux       Linux AppImage (uses system Java; run on Linux)"
	@echo "  dist-linux-bundle Linux AppImage with bundled JRE (jpackage + appimagetool)"
	@echo "  dist-windows     Windows app-image + optional ZIP (run on Windows / Git Bash)"
	@echo "  dist-mac         macOS .app bundle (run on macOS)"
	@echo "  dist-mac-dmg     macOS .app + .dmg (run on macOS)"
	@echo "  dist-all         Build for current OS only (linux | windows | mac)"
	@echo "  lint             Run clj-kondo (if available)"

run:
	clj -M:main

repl:
	clj -M:repl

deps:
	clj -P

uberjar:
	clj -T:build uber

clean:
	clj -T:build clean 2>/dev/null || true
	rm -rf $(TARGET) $(DIST)

dist-linux:
	$(SCRIPTS)/build-appimage.sh

dist-linux-bundle:
	$(SCRIPTS)/build-appimage.sh --bundle-jre

dist-windows:
	$(SCRIPTS)/build-windows.sh

dist-mac:
	$(SCRIPTS)/build-mac.sh

dist-mac-dmg:
	$(SCRIPTS)/build-mac.sh --dmg

dist-all:
	@case "$$(uname -s)" in \
		Linux)   $(MAKE) dist-linux ;; \
		Darwin)  $(MAKE) dist-mac ;; \
		MINGW*|MSYS*|CYGWIN*) $(MAKE) dist-windows ;; \
		*) echo "Unknown OS; run dist-linux, dist-windows, or dist-mac manually." ;; \
	esac

lint:
	@command -v clj-kondo >/dev/null 2>&1 && clj-kondo --lint src --config '{:output {:format :edn}}' || echo "clj-kondo not found; install from https://github.com/clj-kondo/clj-kondo"
