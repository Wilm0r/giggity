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
| `ImportView.java` | Shows incoming selections/deletions from a deep link (`&see=`/`&del=` params); offers overwrite/merge import |

### Data Flow

1. `Fetcher` downloads schedule XML (with HTTP caching)
2. `Schedule` parses via SAX (`XcalParser` for iCal, `PentabarfParser` for Pentabarf) — format detected by inspecting first 1KB
3. `Db` persists user selections SQLite, handles schema migrations via `onUpgrade()`
4. `ScheduleViewActivity` renders using one of the view implementations

### Time Handling

Uses `java.time.*` (ZonedDateTime). Two timezones are tracked: `inTZ` (source/event timezone) and `outTZ` (display timezone). Day boundaries default to 6am (which Pentabarf XML scheduled can customise).

### Conference Menu

Conference entries live in `menu/*.json`. The build merges them into a single resource file. Schema is in `tools/menu-schema.json`; `tools/menu-ci.py` validates entries.
The merged file is built into the binary but at startup Giggity will first try to fetch the most recent version from ggt.gaa.st.

ggt.gaa.st is a tiny little service (source code in `ggt/`) with a webhook called by GitHub on menu file submissions, so that it can always serve a super fresh menu at https://ggt.gaa.st/menu.json.

### Deep Linking

The app handles `https://ggt.gaa.st/` URLs with the format `#url=<schedule_url>&json=<metadata>`. The `tools/ggt.sh` script generates QR codes for these links.

### Instrumented Tests (`Spresso.java`)

The single instrumented test class uses `IntentsTestRule<ChooserActivity>`, which launches a fresh `ChooserActivity` before each test and finishes it after (calling `Intents.init()` / `Intents.release()` around each test). A few things to know:

- **Tests require live network**: schedules are fetched from the internet. Test speed depends on network and HTTP cache freshness (`Fetcher.Source.CACHE_1D` = reuse if <1 day old).
- **Idler pattern for async sync**: `ScheduleViewActivity` and `ChooserActivity` have a static `setIdler(CountingIdlingResource)` method. Tests create a `CountingIdlingResource`, register it with Espresso, and set it on the activity before triggering a load. The `LoadProgressView` / `LoadProgressDialog` inner classes of SVA read the static `idler` field and increment/decrement it. Always call `setIdler(null)` at test end to detach — but only AFTER the load completes (the decrement reads the static field at call time, so nulling early causes the count to never reach 0 and Espresso hangs).
- **`IntentsTestRule` teardown race**: `afterActivityFinished()` (which calls `Intents.release()`) is async relative to the next test's `afterActivityLaunched()` (which calls `Intents.init()`). Explicitly calling `Intents.release()` in `@After tearDown()` avoids the "init called twice in a row" race. `IntentsTestRule.afterActivityFinished()` has a try/catch so the redundant release is safe.
- **`ActivityScenario` within a test**: If a test uses `ActivityScenario.launch()` to start SVA directly (bypassing ChooserActivity), close the scenario explicitly with `scenario.close()` at the end. Not closing it leaves SVA alive, which can prevent `IntentsTestRule` from properly finalizing ChooserActivity between tests.
- **Sticky header touch events**: `TimeTable`'s sticky header overlay (`stickyHeader` FrameLayout) sits on top of `ScheduleListView`. Header views built by `ScheduleListView.makeHeaderView()` become clickable when a room has `latlon` data (they open a geo intent). If left clickable, they intercept taps meant for list items underneath. The sticky header copy is set `clickable=false` to pass touches through.
