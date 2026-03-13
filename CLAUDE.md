# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Giggity** is an Android app for viewing conference/festival schedules in Pentabarf, frab, wafer, Pretalx, or OSEM XML formats. Package: `net.gaast.giggity`.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Run unit tests (JVM, no device needed)
./gradlew testDebug

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run lint
./gradlew lint

# Clean
./gradlew clean
```

Unit tests are in `app/src/test/`, instrumented tests in `app/src/androidTest/`.

A `generateMenu` Gradle task runs automatically before builds — it calls `tools/merge.py` to merge `menu/*.json` into the generated `menu.json` resource.

## Architecture

**Single-module Android app** (`:app`), Java with minSdk 26, targetSdk 35.
One side goal of Giggity is to keep the .apk small which prohibits a lot of library dependencies including most *Compat libraries.
It also should never pick up a dependency on Play Services.

### Key Classes

| Class | Role |
|-------|------|
| `Giggity.java` | Application singleton; owns `Db`, cache, reminders, notifications |
| `ScheduleViewActivity.java` | Main activity; navigation, view switching, event dialogs |
| `Schedule.java` | Core data model; parses Pentabarf/iCal XML into `Item`/`Track`/`Line` objects |
| `Db.java` | SQLite layer; saving user's selections, FTS4 full-text search (unicode61 tokenizer) |
| `Fetcher.java` | HTTP client with caching; supports offline mode, 304 Not Modified, multiple source strategies |
| `BlockSchedule.java` / `BlockScheduleVertical.java` | Time-grid view renderers |
| `ScheduleListView.java` | List-based schedule view, subclassed/used by a few viewers like for example `TimeTable` and `ItemSearch` |
| `ChooserActivity.java` | Launcher/schedule selector |

### Data Flow

1. `Fetcher` downloads schedule XML (with HTTP caching)
2. `Schedule` parses via SAX (`XcalParser` for iCal, `PentabarfParser` for Pentabarf) — format detected by inspecting first 1KB
3. `Db` persists user selections SQLite, handles schema migrations via `onUpgrade()`
4. `ScheduleViewActivity` renders using one of the view implementations

### Time Handling

Uses `java.time.*` (ZonedDateTime). Two timezones are tracked: `inTZ` (source/event timezone) and `outTZ` (display timezone). Day boundaries default to 6am (which Pentabarf XML scheduled can customise).

### Deep Linking

The app handles `https://ggt.gaa.st/` URLs with the format `#url=<schedule_url>&json=<metadata>`. The `tools/ggt.sh` script generates QR codes for these links.

### Conference Menu

Conference entries live in `menu/*.json`. The build merges them into a single resource file. Schema is in `tools/menu-schema.json`; `tools/menu-ci.py` validates entries.
The merged file is built into the binary but at startup Giggity will first try to fetch the most recent version from ggt.gaa.st.