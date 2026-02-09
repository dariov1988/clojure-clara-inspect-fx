# Clara Rules Inspector

A Clojure desktop application that uses the [gateless/clara-rules](https://github.com/gateless/clara-rules) fork (with PR 17 introspection) and [cljfx](https://github.com/cljfx/cljfx) / JavaFX to:

- Enter **rules** (Clojure: `ns`, `defrecord`, `defrule`, `defquery`) and **facts** (EDN vector of maps with `:type`) in text editors
- **Load** rules or facts from file (Clojure/EDN)
- **Run** the session (parse → mk-session → insert → fire-rules → inspect-facts)
- View **Execution trace**, **Inspection** (introspection JSON), **Inserts** (derived facts), **Memory** (root vs derived), and **Inspect facts** (fact-types, bindings, rule-id, value) in tabs

## Requirements

- Clojure CLI (`clj`)
- Java 11+ with JavaFX (e.g. OpenJDK 11+ with `javafx.controls` on module path, or a JDK that bundles JavaFX)

## Run

```bash
clj -M:main
```

## Makefile

All common tasks are wired to `make` (on Windows use Git Bash or WSL, or run the scripts in `scripts/` directly):

| Target | Description |
|--------|-------------|
| `make` or `make help` | Show all targets |
| `make run` | Run the app |
| `make repl` | Start REPL |
| `make deps` | Download dependencies |
| `make uberjar` | Build standalone JAR |
| `make clean` | Remove `target/` and `dist/` |
| `make dist-linux` | Linux AppImage (system Java) |
| `make dist-linux-bundle` | Linux AppImage with bundled JRE |
| `make dist-windows` | Windows app-image (run on Windows) |
| `make dist-mac` | macOS .app (run on macOS) |
| `make dist-mac-dmg` | macOS .app + .dmg |
| `make dist-all` | Build for current OS only |
| `make lint` | Run clj-kondo if installed |

## Self-contained images (Linux, Windows, macOS)

Build scripts live in `scripts/`. Each produces output under `dist/`. Use a **JDK 17+ that includes JavaFX** (e.g. [Zulu JDK FX](https://www.azul.com/downloads/?package=jdk#zulu)) when you want a bundled JRE.

- **Linux**: `make dist-linux` or `./scripts/build-appimage.sh` — AppDir + optional `.AppImage`. Use `--bundle-jre` (or `make dist-linux-bundle`) for a fully self-contained image. Optional: [appimagetool](https://github.com/AppImage/AppImageKit/releases) to get a single `.AppImage` file.
- **Windows**: Run on Windows (Git Bash or WSL): `make dist-windows` or `./scripts/build-windows.sh` — produces `dist/Clara-Inspect-Fx/` with `Clara-Inspect-Fx.exe` and a bundled JRE; if `zip` is available, also creates `Clara-Inspect-Fx-windows-x86_64.zip`.
- **macOS**: Run on macOS: `make dist-mac` or `./scripts/build-mac.sh` — produces `dist/Clara-Inspect-Fx.app`. Use `./scripts/build-mac.sh --dmg` (or `make dist-mac-dmg`) to also build a `.dmg` disk image.

Optional: add `resources/clara_inspect_fx/icon.png` (256×256) for the app icon (Linux AppDir; Windows/macOS can be extended to pass `--icon` to jpackage).

## Project layout

- `deps.edn` — dependencies (clara-rules, cljfx, timbre, data.json) and `:main` alias
- `src/clara_inspect_fx/main.clj` — entry point, renderer, mount-renderer, event handler wiring
- `src/clara_inspect_fx/state.clj` — app state atom, default rules and facts (shipped example)
- `src/clara_inspect_fx/core.clj` — re-exports `*state`, event handler, load-from-file (FileChooser)
- `src/clara_inspect_fx/pipeline.clj` — run pipeline (load rules, parse EDN facts, map->record, mk-session, insert, fire-rules, inspect-facts)
- `src/clara_inspect_fx/view.clj` — root UI (editors, Load buttons, Run/Clear, TabPane)
- `src/clara_inspect_fx/log.clj` — Timbre config (file + console)
- `resources/clara_inspect_fx/style.css` — styles

## Rules and facts

- **Rules**: Full Clojure namespace with `(ns ...)`, `defrecord`, `defrule`, `defquery`. The first `(ns ...)` is stripped at runtime; definitions are loaded into `clara_inspect_fx.dynamic_rules` (with required `clara.rules` / `clara.rules.accumulators` already wired) for `mk-session`.
- **Facts**: EDN vector of maps. Each map must have a `:type` key (e.g. `:Customer`, `:OrderRequest`); types are resolved to record constructors in the dynamic rules namespace via `map->RecordName`.

## Logging

- Console: Timbre `:println` appender
- File: `log/clara-inspect.log` (created on first run)
- Key steps (parse, session, insert, fire, inspect-facts, errors) are logged and also shown in the **Execution trace** tab.

## License

MIT License.
