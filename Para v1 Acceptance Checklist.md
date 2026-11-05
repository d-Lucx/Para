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
- [x] Show adjustable map rings and numeric controls. Approach defaults to 1,500 m and arrival to 300 m. The approach radius must exceed the arrival radius. Include an optional backup timer and Test alarm.
- [x] Require precise location before starting. Explain reduced lock-screen alert capability when permissions are missing and allow the rider to proceed after the warning. Explain that the first usable GPS fix can trigger an immediate arrival alarm.

## 2. Tracking and alarms

- [x] One foreground-service-owned trip at a time. Starting begins the session and any configured backup timer immediately. Reopening shows the same active trip.
- [x] Use a single offline GPS stream for proximity checks and in-memory recording. Show Waiting for GPS, Tracking, GPS signal lost, and Approaching. Show remaining distance when a usable fix is available. Initial pilot settings request fixes every two seconds and accept fixes up to 15 seconds old with reported accuracy of 50 m or better.
- [x] Approach chimes once. If a fix enters both zones, arrival takes priority. If the backup alert is sounding, arrival replaces it. The first usable fix inside the arrival zone triggers arrival. The trip rules passed focused checks for these transitions.
- [x] A backup timer can sound even without GPS. Show Arrival not confirmed, Keep tracking, and End trip. Keeping the trip active still allows the arrival alarm to fire. Setup accepts a timer from one minute to 24 hours.
- [x] Use the Android alarm audio route and provide a thumb slide plus an accessible End trip action.
- [x] Add an ongoing Android notification with tracking status. View trip opens the live session. End trip requires confirmation. Show the destination and usable remaining distance when unlocked. Hide those details on the lock screen by default and support opt-in disclosure.
- [x] On the Infinix X6852, start a trip through the precise-location permission flow, background the app, reopen the same session, dismiss the one-minute backup alert with Keep tracking, and confirm End trip. The notification activity actions were exercised directly through ADB.
- [x] On the Infinix X6852, switch off the display for 15 seconds. ADB confirms Para’s foreground service and GPS registration remain active while the phone is asleep.
- [ ] Verify the one-minute backup alert while the display stays off. In the ADB attempt the phone woke before the result could be attributed to the timer. The earlier awake-device backup alert and Keep tracking test passed.
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
- [x] Run the Gradle trip regression tests for backup timing, arrival priority, one-time approach, stale fixes, and GPS gaps. The tests also cover breaking the path when the GPS provider is turned off.

Dead reckoning, speed-scaled zones, automatic speaker escalation, and iOS are deferred.
