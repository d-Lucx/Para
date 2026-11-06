# Para

Para is an offline Android commuter alarm. Choose a destination, configure approach and arrival zones, and optionally set an elapsed-time backup alert. During a trip, Para checks usable GPS fixes against the destination and records the path for an optional saved overview.

**Status:** Android pilot development. A controlled screen-off backup alarm passed on an Infinix X6852; moving Tagum–Davao rides, a second device, the broader power/audio matrix, and signed-release verification remain open. Bundled nationwide maps do not establish nationwide ride validation.

## Features implemented

- Destination-first setup with offline search, named landmark selection, custom coordinates, and two adjustable alert rings.
- MapLibre Native, 18 bundled Philippine regional PMTiles street packs, local labels, and a public offline place index. The app has no internet permission.
- A separate Tagum–Davao detail archive with buildings, land use, water, paths and road hierarchy, plus source-associated entrance selection and explicit pin confirmation. New phone verification is pending; see the map review below.
- A foreground GPS session, private lock-screen notification by default, approach chime, arrival alarm, and optional backup timer. Backup alerts explicitly do not confirm arrival.
- Encrypted on-device saved destinations, temporary interrupted-trip recovery, and separately saved trip history with deletion.
- Trip overviews with elapsed time, outcome, and **Recorded distance**, excluding GPS gaps.
- Native Compose screens based on the approved Stitch design, including light/dark/system themes and permission repair links.

Remaining distance is straight-line proximity, not road-route distance. Para currently has no route planner, turn-by-turn navigation, inertial dead reckoning, or road-snapped recording.

## Pilot map phase

The approved light Stitch design now has a native implementation: richer Tagum–Davao geography, landmark details, source-associated entrance choices, manual adjustment without snapping to another landmark, and explicit **Use this pin** confirmation. All regions remain bundled. Trip overviews retain raw GPS paths and gaps, with Start/Last recorded point markers and improved path framing.

The additional archive contains 217,878 building areas and occupies 19.4 MiB. All 5,793 tiles and their geometry streams passed independent decoding; fixtures cover polygon holes, relation/member duplication, entrance association and clipping. The Infinix renders real pilot streets/buildings in airplane mode with Wi-Fi off. Landmark/entrance selection, private-entrance disabling, manual adjustment, coordinate entry and retained ring/timer settings passed focused device checks. The final 200% text check passed on the Infinix. Comparative performance, screen-reader checks and moving rides remain open. The [pilot map design review](<docs/Para Pilot Map Design Review.md>) records the design, data and evidence. Optional routing, downloads and road snapping are outside this phase.

The catalog label optimization uses bundled glyphs for nationwide and pilot-stop names. In a focused Infinix comparison, median app PSS was 8.5 MiB lower across three map openings per build. The samples varied between openings. See the [measurement record](<docs/Para Map Improvement Plan.md#catalog-label-rendering>).

First use prepares local map and search files. If preparation fails, use **Retry offline maps** after resolving the storage problem. An isolated Infinix test recovered both a pilot archive and a regional archive in place. See the [copy and recovery results](<docs/Para Map Improvement Plan.md#first-use-preparation-and-copy-recovery>).

Home opens to the bundled map. Tap a landmark or drop a pin, then choose **Set arrival alarm** to confirm the stop and configure alert zones. Shared nationwide styling, area downloads and offline driving routes are tracked in the [expansion plan](<docs/Para Map-first Expansion Plan.md>).

Tap **Locate** to show your position and center the map. Home can show a recent approximate location with an accuracy label while the trip alarm waits for a fresh, precise GPS fix. The arrow follows the phone's compass heading. When the heading is unavailable, the marker is a dot.

During an active trip, the map shows the confirmed destination, arrival ring, current usable fix and recent recorded GPS trail. Straight-line distance, elapsed time and **End trip** stay below the map. The trip overview retains the full recording.

The 18 regional packs share the pilot's road palette and show category icons, more road labels, and mapped one-way arrows and traffic signals at street zoom. The offline search index is stored as a 23.8 MiB gzip asset and expands to 61.8 MiB on first use. See the [regional build guide](<docs/Regional map build.md>).

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

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`; lint reports are under `app/build/reports/`. Current validation: nine unit tests pass (seven trip/deadline tests and two light/dark style integrity tests); lint has 0 errors, 25 warnings, and 1 hint. The debug APK and unsigned release bundle build; the APK passes 16 KB native-library ZIP alignment checking. Builds and unit tests do not replace the [physical-device verification guide](<docs/Para Device Verification Guide.md>). Record new results in the [acceptance checklist](<docs/Para v1 Acceptance Checklist.md>).

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
