# Para architecture conformance review

Review date: November 5, 2026. Sources: the original architecture, approved conversation decisions, acceptance checklist, Android source, bundled assets, Gradle checks, and recorded Infinix smoke tests.

**Assessment:** Para implements the core approved Android v1 architecture, with intentional changes and remaining verification gates. It does not implement every mechanism in the original cross-platform blueprint. Nationwide data availability does not mean nationwide ride or alarm verification.

## Requirement-by-requirement comparison

| Blueprint area | Current implementation and evidence | Status |
| --- | --- | --- |
| Offline GNSS without network geolocation | `TripService` requests `LocationManager.GPS_PROVIDER`; `TripSession` rejects stale/inaccurate fixes and computes Haversine distance. Waiting/lost/provider-off states are explicit. | Implemented; moving screen-off and cold-start ride checks remain open. |
| Inertial dead reckoning / tunnels | No inertial position estimate. Optional elapsed-time backup says arrival is not confirmed; Keep tracking leaves GPS arrival enabled. | Approved deferral. Do not add dead reckoning to satisfy the old text. |
| Native presentation and local maps | Compose and MapLibre Native with PMTiles local-file URLs; 18 detailed commuter street packs, regional boundaries, named places, local SDF glyphs, and map ring controls. Android has no INTERNET permission. | Implemented. All pack hashes/headers and 36 sample road/landmark layers passed independent validation. Manila/Cebu streets and labels passed an offline Infinix smoke test. |
| Origin/route corridor extraction during setup | Destination-first setup; no route or origin. Prebuilt packs are generated on the development machine and bundled. Detours and vehicle changes need no new route. | Approved replacement. App-side extraction/download would conflict with the current offline flow. |
| Regional pack continuity | All regional geometry renders in one coordinate system. The street pipeline assigns each OSM way to one pack and preserves its full geometry across a boundary. The old pilot road layers are no longer drawn over the new streets. | 18 packs generated: 1,353,438 road/rail/water ways, 103,269 named landmark records, 75,435 tiles, 123.9 MiB. Zambales Highway was independently decoded from its owner pack on both the Ilocos and Central Luzon sides. Physical boundary-crossing ride checks remain open. |
| SQLite FTS5, proximity ranking, typo tolerance | `StopCatalog` now opens a prebuilt read-only SQLite FTS4 database with 104,919 public place records, normalized term prefixes, exact-name/pilot-stop prioritization and indexed nearby lookup. Search runs off the UI thread. | FTS4 is used for Android bundled-SQLite compatibility; no additional database SDK. Multiword queries use implicit AND, verified on the Infinix. Fuzzy matching and current-GPS distance ranking remain absent. This is not literal FTS5 conformance. |
| Android foreground location lifecycle | Non-exported location foreground service; starts from a user action, ongoing notification, StateFlow session, precise-location gate, no background-location permission. Service is not automatically restarted into a stale trip. | Implemented and smoke-tested on Infinix. |
| Three-tier adaptive GPS sampling | A shared two-second request stream drives both recording and alarms. Accepted fixes are at most 15 seconds old and 50 m reported accuracy. | Approved replacement pending pilot battery/path measurements. Do not restore 60–120 second intervals. |
| Wake-lock scope | Backup timer holds a timed partial wake lock until alert/deadline/end; looping playback also has a wake lock. No explicit distance-tier wake lock. | Deviation from the blueprint. Battery and Doze delivery need measurement; no claim of guaranteed wake-up. |
| OEM battery-management guidance | Settings now explains Android battery controls, Infinix power restrictions, current optimization exemption, and links to battery/app settings. | Added in this phase; device navigation verification pending. |
| Two-stage alert priority | One approach chime; first-fix arrival suppresses approach; arrival replaces backup; intentional slide and accessible End trip. | Covered by trip regression tests and prior device flows. |
| Alarm sound and vibration | Service uses `USAGE_ALARM`, looping arrival/backup audio, full-screen permission checks and notification fallback. This phase adds a brief approach vibration and service-owned repeating alert vibration with cancellation. | Audio implemented; added haptics need device verification. Silent/DND/headphone matrix remains open. |
| Automatic speaker escalation | No forced simultaneous speaker/headphone escalation. | Approved deferral pending physical evidence. |
| iOS / ActivityKit | Android-only source tree. | Approved deferral. |
| SQLCipher/Room encrypted database | `EncryptedFile` uses AES-256-GCM, random per-write nonces, Android Keystore keys and AtomicFile in `noBackupFilesDir`. Saved destinations, temporary checkpoint, and saved history are separate. Backup is disabled. | Equivalent local encryption intent, different storage mechanism. No SQLCipher/Room. Keystore hardware backing has not been attested; do not claim StrongBox/TEE on every device. Whole-file history rewrites need measurement with long histories. |
| Memory-only coordinates and purge-on-end | Live samples also checkpoint to an encrypted temporary record. End opens an overview; save is explicit; discard/delete is supported; interruption recovers as ended. | Approved expansion for recovery and recorded-trip history. |
| No telemetry / local isolation | No analytics, ad or cloud crash SDK configured; INTERNET removed from the merged manifest. No app geocoder or map network endpoint. | Implemented. Device/OS diagnostics and assisted GNSS are outside the app's permission boundary; this is not a hardware-level guarantee against all extraction. |
| Trip overview, history, notifications, privacy | Recorded distance excludes gaps; overview shows outcome; fixed Save/Discard with confirmation; notification returns to same session and confirms End; destination hidden on lock screen by default. | Approved additions implemented; physical path/outcome checks remain open. |

## Corrections to interpret the original blueprint safely

The original audio section describes mute/DND bypass as unconditional. Android notification policy can suppress alarms, and full-screen access is a separate permission. Para must describe observed device behavior, not promise an alarm will bypass every silent/DND/headphone configuration. See [Android interruption filters](https://developer.android.com/reference/android/app/NotificationManager#INTERRUPTION_FILTER_NONE), [notification permission behavior](https://developer.android.com/develop/ui/views/notifications/notification-permission), and [Doze restrictions](https://developer.android.com/training/monitoring-device-state/doze-standby).

The original SQLCipher paragraph mixes library-specific encryption details with a general AES-GCM requirement. The app's actual mechanism is Keystore-backed AES-GCM file encryption. FTS prefix search also does not inherently establish typo tolerance. Keep these mechanisms distinct in implementation claims.

## Remaining acceptance gates

1. Broaden regional map checks beyond the sampled tiles and Manila/Cebu device smoke tests. Verify boundary crossings and source coverage on actual rides. Street packs occupy 123.9 MiB and the public search index 39.7 MiB before APK compression; first-use internal copies also require storage. Measure the signed, per-device Play download size rather than assuming the large universal debug APK represents release delivery.
2. Verify the added vibration lifecycle and battery-settings navigation on the connected phone.
3. Complete fresh-install offline setup, moving screen-off pilot rides on two physical devices, backup/arrival delivery, lock-screen privacy, dismissal, DND/silent/headphones, battery saver and power measurements.
4. Complete TalkBack/Switch Access and large-text checks, then sign and verify the Play-distributed release. The current debug build is not a production release.

The [acceptance checklist](<Para v1 Acceptance Checklist.md>) is the live implementation/verification record. The [device guide](<Para Device Verification Guide.md>) contains rider-operated tests that ADB alone cannot establish.
