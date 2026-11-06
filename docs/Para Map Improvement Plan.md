# Para map improvement plan

Updated November 5, 2026. This records approved scope, implementation progress and bounded verification evidence.

The user approved the final light Stitch proposal and requested implementation. Native map/selection changes and pilot asset generation are complete and build-verified. After reconnecting, the Infinix passed focused offline rendering and selection checks. The final layout check also passed at 200% text. The [pilot map design review](<Para Pilot Map Design Review.md>) records data totals and evidence. Comparative performance, accessibility and moving-ride checks remain open. The emulator stayed offline and was stopped.

## Approved decisions

The user selected the following during the map review:

1. Improve map detail and destination selection first. Route previews and full offline turn-by-turn navigation are outside this phase.
2. Use Organic Maps-inspired geographic context and road hierarchy while retaining Para's approved Stitch interface and distinctive approach/arrival rings.
3. Keep all Philippine regions bundled, preserving fresh-install offline use and the app's lack of internet permission.
4. Prioritize tappable landmark details and entrance-aware pins.
5. Add detailed buildings, parks, water areas, and walking paths in Tagum–Davao first; measure before expanding nationwide detail.
6. When no entrance is mapped, show the landmark pin and ask the rider to confirm or adjust it to the intended roadside stop. Do not automatically snap to a road.
7. Retain raw GPS recording and visible gaps. Improve start/end markers and gap explanations without road snapping or smoothing the recorded distance.

Existing decisions remain: destination-first setup, detours and vehicle changes without reconfiguration, shared GPS stream for alarms and recording, first-fix arrival, alarm priority, encrypted optional history, interrupted-trip recovery, and explicit GPS uncertainty. Dead reckoning, speed-scaled rings, and automatic speaker escalation remain deferred.

## Organic Maps source review

Reviewed the public `master` sources linked below on November 5, 2026. Branch links can change; pin a reviewed revision if any source is later incorporated. These observations motivate Para-specific recommendations, rather than proving an implementation or performance result in Para.

| Area and primary reference | Observed approach | Application to Para |
| --- | --- | --- |
| [Basemap styling](https://github.com/organicmaps/organicmaps/blob/master/data/styles/default/include/Basemap.mapcss) and [light style](https://github.com/organicmaps/organicmaps/blob/master/data/styles/default/light/style.mapcss) | Geographic areas, buildings, roads, labels and icons have separate styling rules with zoom-dependent detail. | Introduce contextual layers and deliberate zoom hierarchy. Use independently authored MapLibre styles and keep alarm overlays readable. |
| [Search ranking](https://github.com/organicmaps/organicmaps/blob/master/libs/search/ranking_info.hpp) | Ranking metadata includes distance to a pivot, spelling errors, name matching, and feature categories. | Later evaluate nearby/map-area ranking, categories and bounded typo fallback. Current Para FTS4 prefix search does not provide these. They are suggestions, not yet approved implementation. |
| [Regional storage](https://github.com/organicmaps/organicmaps/blob/master/libs/storage/storage.hpp) | Tracks regional file status, sizes, local presence and updates. | Expose useful coverage/version/size information and measure local copies. Keep bundled delivery for this phase; a downloader would change approved offline architecture. |
| [Routing session](https://github.com/organicmaps/organicmaps/blob/master/libs/routing/routing_session.hpp) | Separate route build/rebuild, following, and location matching operations. | Keep routing separate from destination proximity. Map tiles alone cannot supply turn-by-turn routing. No routing engine integration is approved here. |
| [Project features](https://github.com/organicmaps/organicmaps#features) | Offline maps/search, navigation and track exchange are distinct capabilities. | Treat each as its own feature with explicit scope and validation; do not assume adopting map presentation also supplies navigation. |

### Reuse boundary

This phase uses Organic Maps as a reference, without copying its code, style assets, icons or binary maps. Its root [LICENSE](https://github.com/organicmaps/organicmaps/blob/master/LICENSE) points to Apache 2.0; [NOTICE](https://github.com/organicmaps/organicmaps/blob/master/NOTICE) records additional attribution and third-party information, and [DATA_LICENSE.txt](https://github.com/organicmaps/organicmaps/blob/master/DATA_LICENSE.txt) separately covers compiled map data. Review the relevant files at a pinned revision before any future reuse. Organic Maps `.mwm` files and MapCSS styles are not drop-in replacements for Para's PMTiles/MapLibre assets.

## Baseline before the pilot detail phase

| Area | Current implementation | Gap / opportunity |
| --- | --- | --- |
| Regional data | 18 bundled street packs, one regional owner per whole OSM way, retained cross-border geometry | Preserve continuity when adding polygon/detail layers; regional ownership is a storage partition |
| Content | Major/local roads, rail, rivers, named commuter landmarks, regional boundaries and town anchors | No general building footprints, land-use/water polygons, entrance model or complete walking network |
| Presentation | MapLibre style JSON in `CorridorMap.kt`, road/landmark labels and overlay rings | Roads mostly use broad main/local categories; strengthen road hierarchy and contextual geography |
| Place selection | Rendered named-feature taps select a source anchor; coordinate entry provides an alternative | Add details and a deliberate confirmation/adjustment flow; a campus anchor is not an entrance |
| Search | 104,919 public places in prebuilt read-only SQLite FTS4, normalized prefixes and exact-name/pilot priority | No typo matching or GPS/map-pivot ranking |
| Storage/startup | All street packs copy atomically to versioned internal files; first copies are hash-checked | Packaged maps and internal copies both occupy storage; all sources/layers are currently created |
| Track/alarms | GPS samples drive raw segmented recording and straight-line proximity | Preserve gaps and distance semantics; richer styling must not introduce synthetic fixes or alter alarm triggers |

Previously recorded street archives total 123.9 MiB; the public search database is 39.7 MiB before APK compression. Observed setup readiness was 2.8–3 seconds after prebuilding the search index. These are historical baseline observations, not promised budgets for richer maps. Actual APK download size, installed footprint and first-copy cost require separate measurement.

## Proposed implementation order within approved scope

### Phase 1 — Pilot data and visual hierarchy

Extend the development-machine extractor/schema for pilot building footprints, land use/parks, lakes/coastal water and walking paths. Preserve road classification needed for differentiated widths/casings and labels. Handle polygon relations/holes and cross-tile/regional continuity; do not render building/campus centroids as verified entrances. Keep nationwide street packs available.

Create pilot map examples in Stitch to confirm geographic colors, road hierarchy, POI prominence, detail zoom levels and alarm contrast within Para's existing interface. Translate confirmed designs to native MapLibre styles. The data additions must accompany style work: changing colors cannot reveal omitted geography.

### Phase 2 — Landmark details and precise pin placement

Show a selected place's name, type and source-backed location context in a compact sheet, with accessible actions to use or adjust the pin. Distinguish a mapped entrance from a landmark anchor. Show available mapped entrances for rider selection; do not describe one as a verified vehicle drop-off without supporting data. If none exist, explicitly ask for confirmation/adjustment. Retain custom coordinates and a non-gesture selection path.

Keep the draft, both ring values and backup timer when inspecting or adjusting a place. A confirmed point inside the arrival zone can alarm on the first usable GPS fix, as already approved. Do not move the rider's confirmed destination automatically.

### Phase 3 — Clearer recorded-trip overview

Add distinguishable start/end markers and explain GPS gaps with text as well as map styling. Preserve **Recorded distance**, elapsed duration and outcome. Never join gaps, fabricate positions or replace raw samples with road-matched geometry. Keep Save/Discard visible and encrypted history separate from destinations.

### Phase 4 — Measure and optimize

Profile the current implementation and the richer pilot maps on the same Infinix/build/settings, then a second device. Compare fresh-install first-copy time, warm map-open time, pan/zoom frame times, memory, search latency, APK/per-device release size and installed storage. Check low-storage/copy failure recovery. Measure screen-off GPS battery use on actual rides separately from map-visible rendering.

Evaluate restricting expensive detail by zoom and viewport, caching unchanged overlays, and reducing unnecessary source/layer work only after profiling identifies the cost. Regional viewport loading requires buffers and cross-border ownership bounds; a road can be owned by a neighboring pack. Do not change GPS sampling or drop cross-border geometry as a rendering optimization. Set numeric performance budgets after baseline capture, and record before/after results here.

The isolated debug package built with `-PparaMapBenchmark=true` emits `ParaMapPerf` timing for the first fully rendered frame after map style population, render callback gaps during camera motion, and local catalog searches. Callback gaps measure wall time between callbacks. The logs omit coordinates and search text. The Infinix run installed and launched the benchmark package, but the automation could not find **Set destination** in the UI hierarchy. It restored phone settings and uninstalled the test package. Renderer, gesture and search baselines remain open.

#### Catalog label rendering

Each map opening previously created a bitmap for each of the 412 distinct catalog names. The nationwide and pilot-stop layers now draw names with the bundled Inter glyphs and a theme-colored halo. The bundled catalog names use ASCII, ñ and the en dash. All three are covered by the local glyph ranges. See the [MapLibre symbol layer reference](https://maplibre.org/maplibre-style-spec/layers/#text-field).

The Infinix X6852 ran both debug builds with existing map data, airplane mode enabled and Wi-Fi disabled. Each run started with a force-stopped app. We opened the Polyclinic Davao confirmation map three times and sampled app PSS using `adb shell dumpsys meminfo com.dlucx.para` after a five-second settle and an accessibility dump. The debug APK measured 235,803,409 bytes for each build. The optimized unsigned release bundle measured 192,797,154 bytes.

| Build | PSS samples (KiB) | Median (KiB) |
| --- | --- | ---: |
| Baseline `d240a86` | 235,938 / 255,216 / 255,142 | 255,142 |
| Glyph labels | 191,451 / 246,415 / 248,499 | 246,415 |

Median PSS was 8,727 KiB lower, about 3.4%. The ranges overlap. Keep first-copy time, map-ready time, frame timing, search latency, low-storage recovery, Play download size and the second-device and ride measurements open.

On the optimized build, tapping the rendered Manila label opened destination confirmation and the map displayed the ñ in Las Piñas. Both checks ran offline. The Infinix is running the optimized APK and its original connectivity settings were restored.

#### First-use preparation and copy recovery

An isolated debug install on the Infinix X6852 opened the nationwide map in airplane mode with Wi-Fi off. Debug logs recorded 21 first-use copies totaling 192,361,952 bytes (183.5 MiB), with 797 ms summed across copy, hash and publication operations. The isolated app's `no_backup` directory occupied 188,072 KiB. Reopening setup in the same process produced zero additional copies.

An ADB accessibility probe observed map attribution 8.56 seconds after the fresh setup tap and 3.44 seconds after the warm tap. These are single observations that include accessibility dump overhead. Measure native first-frame and pan/zoom timing separately.

We simulated a write-open failure by placing a directory at the AtomicFile temporary path, once for the pilot archive and once for a regional archive. Each failure surfaced **Retry offline maps**. After removing the obstruction, retry recovered in the same screen and the replacement file matched the bundled SHA-256. The isolated app was removed and the phone settings were restored. A mid-copy failure on a full volume remains open.

## Acceptance gates

- [x] Confirm pilot map/landmark-sheet designs against approved Para styling before production UI changes. User selected “Approve and implement.”
- [x] Generate/normalize pilot geometry and validate hashes, schema, counts and all 5,793 tile geometry streams with an independent decoder. Synthetic fixtures verify holes and duplicate-member suppression. Existing local glyph assets are retained; the pilot notice is included in APK/AAB.
- [ ] Verify roads, water, parks, buildings and walking paths at appropriate zooms in airplane mode with Wi-Fi off, including fresh install.
- [ ] Verify landmark/entrance selection, missing-entrance explanation, pin adjustment, retained draft and coordinate fallback on the phone.
- [ ] Check TalkBack, large text, color distinction and ring/track legibility.
- [ ] Verify recorded gaps remain separate, distance unchanged, and start/end markers do not imply a confirmed arrival for backup/manual/interrupted outcomes.
- [ ] Capture comparative storage, startup, rendering, memory, search and power evidence; ensure the active GPS/alarm session is unaffected.
- [ ] Repeat relevant ride/release gates in the main acceptance checklist. No new physical test is marked passed by build/data validation.

## Suggestions for later decisions

The rider subsequently approved a map-first Home, prompted area downloads and driving route previews. The expanded scope and design gate are tracked in the [map-first expansion plan](<Para Map-first Expansion Plan.md>). The original offline pilot scope above remains the baseline for performance comparisons.

Potential later work: category browsing for terminals/markets/hospitals/schools; bounded typo tolerance and explicit search-area ranking; map source/version/size status; optional user-initiated GPX export with clear location disclosure; route previews with separately labeled road distance; optional walking navigation. Bus/jeepney route guidance would require suitable transit data and validation, not a car route presented as a transit itinerary. None of these is approved by the current answers.

Update this document with clarified decisions, design references, implementation status and measured results as development proceeds. Use the [acceptance checklist](<Para v1 Acceptance Checklist.md>) as the main verification record and the [regional map build guide](<Regional map build.md>) for reproducible commands.
