# Para

Para is an offline Android commuter alarm. Choose a destination, configure approach and arrival zones, and optionally set an elapsed-time backup alert. During a trip, Para checks usable GPS fixes against the destination and records the path for an optional saved overview.

**Status:** Android pilot development. A controlled screen-off backup alarm passed on an Infinix X6852; moving Tagum–Davao rides, a second device, the broader power/audio matrix, and signed-release verification remain open. Bundled nationwide maps do not establish nationwide ride validation.

## Features implemented

- Destination-first setup with offline search, named landmark selection, custom coordinates, and two adjustable alert rings.
- MapLibre Native, 18 bundled Philippine regional PMTiles street packs, local labels, and a public offline place index. The app has no internet permission.
- A foreground GPS session, private lock-screen notification by default, approach chime, arrival alarm, and optional backup timer. Backup alerts explicitly do not confirm arrival.
- Encrypted on-device saved destinations, temporary interrupted-trip recovery, and separately saved trip history with deletion.
- Trip overviews with elapsed time, outcome, and **Recorded distance**, excluding GPS gaps.
- Native Compose screens based on the approved Stitch design, including light/dark/system themes and permission repair links.

Remaining distance is straight-line proximity, not road-route distance. Para currently has no route planner, turn-by-turn navigation, inertial dead reckoning, or road-snapped recording.

## Next approved map phase

Use Organic Maps as a reference for geographic context and road hierarchy while keeping Para's interface and alarm behavior. Add detailed buildings, parks, water areas, and walking paths in the Tagum–Davao pilot first. Improve tappable landmark details and entrance-aware pin selection; if no entrance is mapped, ask the rider to confirm or adjust the landmark pin. Keep all regions bundled and retain raw GPS paths, with clearer overview markers and gap explanations.

These changes are approved for development and **not yet implemented**. Optional route previews, turn-by-turn directions, and downloadable maps are outside this phase. See the [map improvement plan](<docs/Para Map Improvement Plan.md>) for decisions, source references, proposed implementation order, and verification gates.

## Build and verify

Use JDK 17, Android SDK Platform 37, Android platform-tools, and the repository's Gradle wrapper (Gradle 9.6.0). Android Studio can install the SDK components. The app supports Android 8.0 / API 26 and newer.

On Windows, set the SDK path in an untracked `local.properties` file, for example:

```properties
sdk.dir=C:/Users/Renelyn/AppData/Local/Android/Sdk
```

From the repository root:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The first build needs access to dependency repositories. Later builds can use `--offline` once dependencies and SDK components are cached.

Install on a connected, USB-debugging-enabled device:

```powershell
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" devices
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -d install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`; lint reports are under `app/build/reports/`. Current alarm-fix validation: seven unit tests pass, lint has 0 errors, 25 warnings, and 1 hint. Builds and unit tests do not replace the [physical-device verification guide](<docs/Para Device Verification Guide.md>). Record new results in the [acceptance checklist](<docs/Para v1 Acceptance Checklist.md>).

## Repository layout

| Location | Purpose |
| --- | --- |
| `app/src/main/kotlin/com/dlucx/para/` | Compose UI, map rendering, search, GPS service, alarms, and encrypted records |
| `app/src/main/assets/` | Bundled maps, regional metadata, public search data, glyphs, and data notices |
| `app/src/test/` | Trip and backup-deadline regression tests |
| `tools/` | Development-machine map extraction, packaging, indexing, and integrity checks |
| [docs/](docs/README.md) | Architecture, decisions, live acceptance checklist, device guide, map build instructions, and release review |

## Data and external references

Map data comes from OpenStreetMap/Geofabrik, with GeoNames places and separately attributed regional boundaries. Keep the notices shipped with the assets. Local Inter fonts/glyphs retain their font license. The [regional map build guide](<docs/Regional map build.md>) describes sources and reproducibility.

[Organic Maps](https://github.com/organicmaps/organicmaps) is a design and engineering reference; its code, artwork, and compiled map files have not been incorporated by this documentation change. Its source, third-party assets, and binary map data have separate notices. Our proposed implementation continues using Para's MapLibre/PMTiles pipeline.

## Keeping documentation current

Update this README when shipped behavior, setup commands, map coverage, or release status changes. Update the acceptance checklist with the same change, separating implementation from physical verification. Record map decisions and measurements in the map improvement plan; update the device guide when a rider needs new verification steps. Keep project Markdown guides in `docs/`, with this root README as the repository entry point. Preserve asset/license notices with their corresponding data.
