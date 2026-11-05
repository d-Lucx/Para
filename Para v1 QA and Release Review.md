# Para v1 QA and release review

Review date: November 4, 2026. Scope: Android app and Google Play production readiness.

**Decision: Hold public release.** The existing build and one-device smoke results make Para a candidate for a supervised Tagum–Davao pilot. Production sign-off needs evidence that the app can wake a rider at the right time with the screen locked, a tested signed release, and completed Play Console requirements.

Follow-up: The six UI findings have implementation changes. The Infinix smoke test passed coordinate entry, the setup summary, GPS-off guidance, the fixed overview actions and discard confirmation, and full-screen access repair with draft retention. Four Gradle trip regression tests pass. The acceptance checklist records the accessibility, ride, and release checks that remain open.

## Evidence

| Area | Result |
| --- | --- |
| Launcher branding | The supplied `logo.jpg` is the source of the Android adaptive icon. A simplified version of the mark supports themed launchers. The status-bar notification retains its separate glyph. |
| Visual review | Reviewed saved Home, Destination setup, Active trip, Trip overview and Settings captures in the available light and dark states. Clear primary actions, explicit GPS status, and a fixed Start trip button are strengths. These captures are from the prior build. |
| Prior device checks | The acceptance checklist records offline map rendering, stop search, pin boundary behavior, notification actions, an awake backup alarm, save/delete history and force-stop recovery on the Infinix X6852. The screen-off service was observed for 15 seconds; that does not establish that the timer or arrival alarm works with the screen off. |
| Trip logic smoke test | Seven checks passed against the compiled Kotlin classes: timer does not fire early; timer fires without a fix; arrival overrides backup; first-fix arrival skips approach; approach chimes once; stale fixes clear remaining distance; GPS gaps start a separate path without adding distance. This does not exercise Android sound or background delivery. |
| Automated regression coverage | Gradle found no unit test sources; no instrumentation tests were found in the project. |
| Android lint | Final debug lint completed with 0 errors, 15 warnings, and 1 hint. The launcher icon checks passed; remaining findings include the map touch accessibility warning. See the [lint report](app/build/reports/lint-results-debug.html). |
| Build and signing | The debug APK and release AAB build. The release bundle is unsigned, as confirmed by `jarsigner`; it is not ready for Play upload. |
| Live UI retest | Blocked: the Infinix disconnected before the retest and the local emulator stayed offline in ADB. The screenshot review and historical device results are separate evidence. |
| Packaging | The rebuilt debug APK targets API 37, contains the color and themed launcher resources, and passes APK ZIP alignment at 16 KB. The arm64 and x86_64 native library load segments in the previous debug build, including MapLibre, were also 16 KB aligned. Repeat these checks on the signed release. |

## UI and UX findings

| Priority | Finding | Improvement and acceptance check |
| --- | --- | --- |
| High | Destination pin selection relies on map gestures. Lint flags the map touch listener, and a named map alone does not give TalkBack users a way to select a point. | Keep offline stop search as a usable alternative. Add an accessible way to select a custom destination, such as accessible map controls with a “Use map center” action. Verify search and pin workflows with TalkBack and Switch Access. |
| High | The alert warning offers Continue or Cancel; resolving access takes a separate trip to Settings. Tracking notification and full-screen alarm are separate capabilities. | Show which permission or channel is blocked, offer a direct fix, preserve the draft, and explain what will still work. Test approximate location, denied notifications, a disabled alarm channel, and full-screen access off on Android 14+. |
| High | Trip overview puts Save and Discard after a tall, interactive map. In the saved portrait capture, Save is visible and Discard is below the fold. Back also discards an unsaved trip. | Keep the actions reachable from a fixed footer, and confirm before discarding via Back. Verify both actions at large text sizes and with a screen reader. |
| Medium | The Setup header and map can consume most of the first screen; rings and backup timer controls are farther down while Start trip remains visible. | Put a compact arrival-radius and timer summary beside the start action so riders can see what is configured. Verify at narrow widths, 200% font size, and with the keyboard open. |
| Medium | “Waiting for GPS” explains what Para is doing but gives little guidance if the wait persists. | Distinguish device location being off from a weak fix. After a short delay, offer a next step and show whether a backup timer is active. Check in airplane mode and after turning location off and back on. |
| Medium | “OFFLINE” can be read as current network status; inactive bottom tabs look muted in the dark capture. | Use copy such as “Map available offline” where it adds context, and measure dark navigation contrast and touch targets on a device. |

The backup timer can hold a partial wake lock for the configured duration, up to 24 hours. Measure battery use with and without a timer on representative rides and on longer sessions before deciding whether to keep that limit and scheduling approach.

## Release gates

1. **Run the physical ride and alarm matrix.** Start with a fresh install and airplane mode on the Infinix and at least one additional Android device from a different vendor. Verify ongoing GPS fixes while moving and locked; backup sound after the screen turns off; approach once, arrival overriding backup, first-fix arrival, both dismissal methods, and notification fallback with full-screen access denied. Include silent mode, DND, headphones, battery saver, lock-screen privacy, battery use, and path gaps. Use the [device verification guide](<Para Device Verification Guide.md>) and record OS, device, build, settings, and outcome.
2. **Build the release users will receive.** Set the release version, configure secure upload signing and Play App Signing, generate the signed AAB, and install it through an internal track. Verify native [16 KB compatibility](https://developer.android.com/guide/practices/page-sizes), check the merged manifest, and rerun the offline and alarm smoke tests on that build. The app's API 37 target is above the [API 36 minimum](https://support.google.com/googleplay/android-developer/answer/11926878) for new Android phone apps.
3. **Complete Play review and listing.** Prepare the listing, screenshots, store icon, privacy policy, Data safety form, content rating, and support contact. Declare the location foreground service and full-screen intent; explain the user-started trip and test the fallback if automatic full-screen access is unavailable. Prepare the precise-location justification and check whether Play treats screen-off location as equivalent to background location. See Google's [foreground service and full-screen rules](https://support.google.com/googleplay/android-developer/answer/13392821), [precise location rules](https://support.google.com/googleplay/android-developer/answer/17033915) and [background location guidance](https://support.google.com/googleplay/android-developer/answer/9799150).
4. **Run a pilot before rollout.** Add automated regression checks for alert priority, stale fixes, the backup timer, and process interruption. Run the signed build in internal/closed testing and resolve any missed alarms, crashes or inaccessible trip actions. If using a personal Play account created after November 13, 2023, [Google requires 12 closed testers continuously opted in for 14 days](https://support.google.com/googleplay/android-developer/answer/14151465) before applying for production access.
