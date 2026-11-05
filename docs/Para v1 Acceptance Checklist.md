# Para Android v1 acceptance checklist

Use this checklist alongside the architecture and the approved light and dark Stitch mockups. Where the original architecture differs, the decisions below define v1.

Checked items have been implemented and checked in the build, in a focused test, or on a device as applicable. Physical ride checks are listed separately.

Use the [Para v1 device verification guide](<Para Device Verification Guide.md>) for the open device checks.

## 1. Offline foundation and destination setup

- [x] Native Android with Kotlin and Jetpack Compose. Home, History, and Settings are the top-level destinations. Navigation and persisted theme selection passed on the Infinix X6852.
- [x] Bundle a Tagum–Davao pilot map and eight searchable stops. MapLibre Native renders the bundled OpenStreetMap PMTiles archive and coverage boundary. The APK has no internet permission.
- [x] On the Infinix X6852, cold-launch the upgraded APK in airplane mode with Wi-Fi off. Verify roads, place labels, the coverage boundary, and alert rings render and Tagum stop search works. The smoke test restored the phone’s connectivity settings afterward.
- [x] On the Infinix X6852, place a pin inside coverage, reject a tap outside it, and render the MapLibre basemap in the completed-trip overview.
- [ ] Verify the full setup flow on a fresh install in airplane mode. Some areas in the pilot archive still have partial road detail.
- [x] Choose a destination from offline search or a dropped pin. No origin or route is required. Encrypt saved destinations separately from trip history.
- [x] Bundle 18 Philippine regional overview packs on one continuous MapLibre canvas, with region browsing, regional labels, and 1,642 searchable named places in addition to the eight pilot stops. Boundaries are simplified together before splitting so adjacent packs share their edges. Keep Tagum–Davao PMTiles street detail available.
- [x] On the Infinix X6852, cold-launch the upgraded regional build with airplane mode on and Wi-Fi off; browse Ilocos, search Manila and Cebu City, and verify the selected region follows the destination. Inspect country and city map captures. Tap the Mandaue City map marker and verify its name becomes the destination. The smoke test restored connectivity afterward.
- [x] Prioritize the map below search and region browsing. Put coordinates and destination details below it. Ask riders to check the pin. Named populated-place coordinates represent a town/city point, not an exact stop or address; OSM landmark records provide additional specific anchors.
- [x] Add 18 detailed regional commuter street packs from the Philippine OSM extract: 1,353,438 road/rail/water ways, 103,269 named landmark records, and 75,435 tiles. Preserve complete cross-border ways in one owner pack. Validate all archive hashes/headers and 36 representative road/landmark layers using an independent PMTiles/MVT reader; decode Zambales Highway on both sides of the Ilocos–Central Luzon border.
- [x] On the Infinix X6852 in airplane mode with Wi-Fi off, verify region browsing, Manila/Cebu City search, Cebu South Bus Terminal selection, and locally rendered street/landmark labels. Bundle local Inter SDF glyphs; avoid duplicate pilot-road layers. Map completeness still depends on source OSM data and the selected commuter schema.
- [x] Replace the large startup-built token list with a prebuilt read-only SQLite FTS4 index for 104,919 public place records. Keep private destinations/history in separate encrypted files. Verify multiword search on Android using implicit term matching. Setup readiness in the ADB smoke sequence improved from about 11.5 seconds to 2.8–3 seconds; this is a one-device observation, not a startup benchmark across devices.
- [ ] Verify detailed regional coverage and seamless boundary crossings on physical rides. Check signed per-device download size, first-use storage, low-storage behavior and first-use performance on the second device. The street archives total 123.9 MiB and the public search database 39.7 MiB before APK compression; versioned internal copies also consume space.
- [x] Show adjustable map rings and numeric controls. Approach defaults to 1,500 m and arrival to 300 m. The approach radius must exceed the arrival radius. Include an optional backup timer and Test alarm.
- [x] Require precise location before starting. Explain reduced lock-screen alert capability when permissions are missing and allow the rider to proceed after the warning. Explain that the first usable GPS fix can trigger an immediate arrival alarm.

## 2. Tracking and alarms

- [x] One foreground-service-owned trip at a time. Starting begins the session and any configured backup timer immediately. Reopening shows the same active trip.
- [x] Use a single offline GPS stream for proximity checks and in-memory recording. Show Waiting for GPS, Tracking, GPS signal lost, and Approaching. Show remaining distance when a usable fix is available. Initial pilot settings request fixes every two seconds and accept fixes up to 15 seconds old with reported accuracy of 50 m or better.
- [x] Approach chimes once. If a fix enters both zones, arrival takes priority. If the backup alert is sounding, arrival replaces it. The first usable fix inside the arrival zone triggers arrival. The trip rules passed focused checks for these transitions.
- [x] A backup timer can sound even without GPS. Show Arrival not confirmed, Keep tracking, and End trip. Keeping the trip active still allows the arrival alarm to fire. Setup accepts a timer from one minute to 24 hours.
- [x] Use the Android alarm audio route and provide a thumb slide plus an accessible End trip action.
- [x] Add a short approach vibration and service-controlled repeating backup/arrival vibration. Cancel vibration when an alert is replaced, dismissed, or the service ends; preserve repeating vibration if audio playback itself fails. Declare Android's normal VIBRATE permission.
- [x] On Infinix X6852 with GPS off and the display awake, verify a one-minute backup alert. The rider confirmed both sound and vibration. Keep tracking retained the session; Android's vibration history reported cancellation and no current vibration. End and discard the temporary test trip.
- [ ] Verify approach/arrival vibration, replacement/cancellation and sound with the physical ride, silent/DND and headphones matrix.
- [x] Replace the duration-long timer wake lock with Android's user-visible alarm-clock scheduling and an explicit, non-exported manifest receiver. Keep the countdown based on elapsed time, translate its remaining duration to the clock trigger, and reschedule after clock changes or an early callback. Check Alarms & reminders access; provide a warning/direct repair link and an explicitly limited inexact fallback when denied. Cancel the pending alarm when alerting or ending. A callback cannot reconstruct or restart a terminated trip.
- [x] Add an ongoing Android notification with tracking status. View trip opens the live session. End trip requires confirmation. Show the destination and usable remaining distance when unlocked. Hide those details on the lock screen by default and support opt-in disclosure.
- [x] On the Infinix X6852, start a trip through the precise-location permission flow, background the app, reopen the same session, dismiss the one-minute backup alert with Keep tracking, and confirm End trip. The notification activity actions were exercised directly through ADB.
- [x] On the Infinix X6852, switch off the display for 15 seconds. ADB confirms Para’s foreground service and GPS registration remain active while the phone is asleep.
- [x] On Infinix X6852 / Android 16 (API 36), verify the one-minute backup with GPS off and the phone locked/display off. With alarm access allowed, original Optimized battery mode and no battery exemption, the alarm-clock backend logged audio startup about 0.3 seconds after its deadline with `interactive=false`; the rider confirmed sound and vibration before unlock. The manifest receiver also handled the deadline. This was a stationary USB-connected test; it does not close unplugged/Doze, battery saver, silent/DND/headphone or moving-ride checks. The earlier handler and ordinary wake-up approaches were delayed until unlock, and the kernel showed the process frozen.
- [ ] Verify fresh fixes continue during a moving screen-off trip and tune GPS sampling against path fidelity and battery usage on pilot rides.
- [x] On the Infinix X6852, expand Para’s actual Android notification and verify View trip returns to the active session and End trip opens confirmation before stopping it.
- [ ] Verify lock-screen privacy, arrival sound, full-screen alert, and dismissal on a physical device with the screen off, in silent mode, and with headphones.

## 3. Overview, recovery, and history

- [x] Show the path, destination, elapsed time, recorded distance, and how the trip ended. Distinguish arrival alarm, backup alert, manual stop, and interruption. Leave gaps visible in the path and exclude them from recorded distance.
- [x] Keep a temporary encrypted trip record separate from saved history. Recover the last checkpoint as Interrupted after a process stop. The service writes an initial checkpoint and refreshes it about every 30 seconds while running.
- [x] Show the overview after a trip. Keep Save trip and Discard visible while the map scrolls. Add the trip to history only on Save trip, and confirm before discarding through either Discard or Back. Allow individual deletion of encrypted on-device trip records.
- [x] On the Infinix X6852, end a trip, save its overview, reopen it after relaunch, and delete it individually. Force-stop during a separate trip, recover Interrupted, and confirm Back discards it across relaunch.
- [ ] Verify recorded path gaps, recorded distance, and each trip outcome on a physical ride.

## 4. Design and device verification

- [x] Implement System, Light, and Dark appearance and the Stitch layout hierarchy for setup and live-trip screens, including explicit GPS and alert state labels. Settings includes alarm testing, permission status, and lock-screen privacy.
- [x] Apply the approved Stitch light and dark styles in native Compose across Home, destination setup, active trip, arrival, trip overview, History, and Settings. Bundle Inter and JetBrains Mono for offline rendering. On the Infinix X6852, verify theme switching, offline stop selection, the fixed Start trip action, live trip telemetry, End trip confirmation, overview rendering, and History navigation.
- [ ] Verify an offline Tagum–Davao pilot ride end to end on physical Android devices, including first fix, screen-off tracking, alert priority and dismissal, recovery, and saved history.
- [ ] Verify the coordinate picker with TalkBack and Switch Access. Check the setup footer and overview actions at 200% font size, and test the notification-denied and blocked-channel variants of the settings repair flow.
- [x] On the Infinix X6852, enter a custom destination through the coordinate form, verify the arrival radius and backup timer in the setup footer, and check the location-off guidance during a trip. Verify Save trip and Discard are visible and Back asks before discarding.
- [x] On the Infinix X6852, temporarily block full-screen alert access. Verify Para identifies the block, opens Android's full-screen settings, rechecks access on return, and retains the custom destination and trip settings.
- [x] Add battery-management guidance in Settings, Android optimization-exemption status, and links to Android battery/app settings. Include Infinix power-manager guidance without promising uninterrupted tracking.
- [x] On Infinix X6852, open Android battery settings from Para and return to the Background tracking panel with the optimization status displayed. Preserve existing power settings.
- [ ] Verify vendor-specific background restrictions and battery guidance on a second Android device.
- [x] Run the Gradle trip regression tests for backup timing, arrival priority, one-time approach, stale fixes, and GPS gaps. The tests also cover breaking the path when the GPS provider is turned off.
- [x] Run seven Gradle regression tests, adding duplicate/delayed backup callback suppression and monotonic countdown preservation across wall-clock changes/overdue triggers. Final debug build and lint pass with 0 errors, 25 warnings and 1 hint.
- [ ] Verify denied alarm-access live fallback, access repair during a trip, clock changes and pending-alarm cancellation on the signed release. The permission warning, direct settings navigation and retained one-minute draft were exercised on the debug device.

Architecture comparison: see [Para Architecture Conformance Review.md](<Para Architecture Conformance Review.md>). FTS4 replaces the blueprint's FTS5 for Android bundled-SQLite compatibility; private data uses Keystore AES-GCM files rather than SQLCipher/Room. Typo tolerance and GPS-distance search ranking are not implemented. Approved v1 changes and open device gates are distinguished in the review.

Dead reckoning, speed-scaled zones, automatic speaker escalation, and iOS are deferred.

## 6. Approved map improvement phase (November 5, 2026)

Scope and Organic Maps references are recorded in the [map improvement plan](<Para Map Improvement Plan.md>). Build/data implementation evidence is distinguished from physical verification below.

- [x] Record the map review decisions and collect project guides in `docs/`, with a root README and documentation index.
- [x] Locate the existing Para Stitch project, verify the cached map-source SHA-256 against its notice, and record the unchanged pack/search baseline in the [pilot map design review](<Para Pilot Map Design Review.md>). ADB detects the Infinix; no new app/device acceptance check is implied.
- [x] Generate and refine the light pilot-map/landmark-confirmation design in Stitch, export the final PNG and visually inspect it. User confirmation and physical UI checks remain separate.
- [x] Confirm the final light Stitch pilot-map/landmark design with the user before implementation. The user selected “Approve and implement.”
- [x] Generate the separate 19.4 MiB Tagum–Davao archive: 217,878 building areas, 2,756 land-use areas, 512 water areas, 6,697 paths and 40,809 classified road ways. Retain all 18 national packs unchanged.
- [x] Independently decode all 5,793 pilot tiles and feature geometry streams. Verify source/archive hashes, metadata and 79 holes; run the relation-member duplication, courtyard, boundary-entrance, border clipping and access-tag fixture through PMTiles encoding.
- [x] Implement and build-check landmark details, source-backed entrance selection, missing-entrance confirmation, coordinate fallback and manual adjustment without landmark snapping. Start/Save require a confirmed point and no unfinished search. Phone interaction checks remain open.
- [x] Implement and build-check overview Start/Last recorded point markers, path framing and gap explanations, preserving raw samples, visible gaps and Recorded distance. Physical overview checks remain open.
- [x] Pass nine unit tests (seven trip/deadline plus two light/dark style integrity tests), debug APK and unsigned release bundle builds, and APK 16 KB native-library ZIP alignment check. Lint has 0 errors, 25 warnings and 1 hint.
- [x] On the reconnected Infinix, install without clearing saved data; verify offline pilot buildings/streets/labels, missing-entrance guidance, manual adjustment and explicit confirmation. Verify a mapped entrance, two disabled private entrances, coordinate fallback, and retained 2,000 m/250 m/two-minute settings. No trip was started. Review light/dark captures; fix map height so attribution stays above the footer.
- [x] Verify the final 200% text layout on the Infinix. The map, source credits and all three pin actions are visible. Full TalkBack and Switch Access checks remain open.
- [ ] Preserve unsaved setup drafts across rotation/font-scale configuration changes without persisting raw locations in an unencrypted saved-state bundle. Current selection-flow retention does not cover activity recreation.
- [ ] Measure storage, first-use/warm map readiness, rendering, memory and battery effects; verify offline operation, accessibility and unchanged GPS/alarm behavior on physical devices.

Route previews, turn-by-turn navigation, downloadable maps, typo-tolerant search, category browsing and track export require later decisions.
