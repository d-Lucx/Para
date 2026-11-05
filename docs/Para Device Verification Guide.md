# Para v1 device verification

Use the current debug APK and your Infinix X6852. Record the phone model, Android version, APK build date, and result for each check. Test on a walk or as a passenger. Record **Pass**, **Fail**, or **Blocked**, along with the step where the behavior changed and any Android permission or sound settings that affected it.

## 1. Offline map and stop search

1. Install the APK. For the first-launch check, use a fresh install on a spare device or Android profile so you can keep your current saved destinations.
2. Enable airplane mode and confirm Wi-Fi is off. Open Para, then tap **Set destination**.
3. Wait for the nationwide MapLibre overview. First use prepares local street packs and the search index; measure the wait and available storage. Open **Browse Philippine regions**, pick a region, and pan across a shared border. Search for **Manila** and **Cebu City** and check that each result shows its region and centers the map on the named point. Check real streets and local font labels while still offline; zoom in for smaller landmarks.
4. Search for **Cebu South Bus Terminal** or another named terminal and select the result in the intended locality. Then search **Tagum**, select the pilot terminal, and confirm roads and both rings appear. Tap a named map label to select its point, or tap land for a custom pin. Tap beyond the bundled map areas and confirm Para explains the boundary and keeps the last valid destination. A town-center result is not an exact address or stop. Record missing source roads/landmarks separately.
5. With TalkBack on, open **Enter destination coordinates**. Enter latitude `7.46` and longitude `125.80`, activate **Use coordinates**, and check that the custom destination is selected. Repeat with an outside coordinate and check that TalkBack announces the validation message.

Pass when the map and stop search work on first launch with no connection and the boundary rejects the outside tap. Note any areas with missing road detail separately.

## 2. Background tracking and notification

1. In **Settings → Background tracking**, review the battery optimization status and open the battery settings link. Find Para and review its background restrictions; on Infinix also check the vendor power manager. Return to Para and confirm the status refreshes. Check **Alarms & reminders** access as well: a configured backup timer needs it for precise wake-up scheduling. With it denied, verify the warning/direct repair link and retained draft; Start anyway must show reduced timing capability. Outdoors, pick a destination beyond the arrival ring. Set a one-minute backup timer for a stationary test. Tap **Start trip**, grant precise location while using the app, and allow notifications.
2. Press Home. Open the notification shade and check for the ongoing Para notification. With a usable GPS fix and the phone unlocked, check the destination and remaining distance.
3. Tap **View trip** and confirm the same session and elapsed timer return. Open the notification again, tap **End trip**, and cancel the confirmation. Tracking should continue.
4. For a controlled backup-only check, temporarily turn GPS off so arrival cannot replace the timer alert. Lock the phone immediately after starting and leave it untouched until the minute passes. Record whether sound/vibration begins **before unlocking**; an alert that starts only after unlock is a failure. If working with an observer, let them record the alarm before dismissing it. Then unlock and confirm **Arrival not confirmed** appears. Tap **Keep tracking** and confirm sound/vibration stop while the trip continues. Restore GPS and other settings afterward. Repeat with backup alarm access denied only to verify the explicit reduced-capability warning, not as a successful precise-timing test.

   Para's permitted backup uses Android's user-visible alarm-clock API, so Android may show its upcoming alarm time. Selecting it opens the trip. On the tested Infinix, the confirmed screen-off result used the original **Optimized** app battery policy, without a battery exemption. For power-policy checks, the native route is **Para app settings → Battery → tap the “Allow background usage” row → Optimized / Unrestricted**. The enabled switch alone does not identify the selected policy. Record the policy and restore it after experiments; do not assume Unrestricted fixes every screen-off issue.
5. Start a second test with the backup timer off. With the screen locked, move toward the destination as a passenger or on foot. Unlock after a few minutes and check that elapsed time and the distance update reflect the same trip.

Pass when the trip survives both Home and screen-off, the notification actions behave as described, and the backup can wake you without claiming arrival. Record any interrupted tracking or delayed alarm.

## 3. Lock screen, arrival, and sound

1. In **Settings**, check precise location, notifications, and full-screen alerts. Temporarily turn off one notification setting, prepare a trip, then tap **Start trip**. Confirm Para names the blocked setting and opens the matching Android settings page. Fix it, return, and check that the destination, radii, and backup timer are still present. Repeat with the alarm channel or full-screen access as applicable. Leave **Show trip details on lock screen** off. Use **Test alarm** to set a comfortable alarm volume before starting.
2. Start outside both rings, lock the phone, and approach the destination. Check for the short approach vibration/chime, then repeating arrival vibration/audio. Check whether Android shows the full-screen arrival screen or the notification and whether you can dismiss it. Verify sound and vibration stop after dismissal, including after replacing a backup alert with arrival. Confirm that the destination and distance are hidden on the lock screen.
3. Repeat with lock-screen details enabled and record what appears. Restore your preferred setting after the test.
4. Repeat the arrival check in silent mode and with headphones. Record where the sound plays and whether the lock-screen screen or notification appears. Test both the thumb slide and the accessible **End trip** button with confirmation.
5. For alert priority, let the backup timer expire first. With the backup still active, enter the arrival zone and confirm the arrival alert replaces it. For first-fix arrival, start a separate trip while already inside the arrival ring and wait for GPS.

Record each sound and display result separately. Include the phone's alarm volume, silent or Do Not Disturb state, headphones, and full-screen permission so differences can be reproduced.

## 4. Trip overview, recovery, and history

1. End a normal trip and check the destination, elapsed time, recorded distance, path, and **Manual stop** outcome. Repeat by ending during a backup alert and after an arrival alarm. Check that the outcome matches the alert you ended.
2. During a separate trip, turn Location off and check for **Device location is off**, the link to location settings, and the backup timer status. Move a short distance, turn Location back on, and wait for a new fix. End the trip and check that the map does not draw a straight connector across the missing portion. The recorded distance should exclude the gap. If the provider is on but the fix is weak, check for guidance after 20 seconds.
3. Check that **Save trip** and **Discard** stay visible while scrolling the overview. Press Android Back, choose **Keep reviewing**, and verify the overview remains open. Save it, close and reopen Para, then open the saved item in **History**. Delete it and check that your saved destinations remain. End another trip, choose **Discard**, and confirm. Reopen the app and check that the discarded trip is absent from History.
4. Start a separate trip and let it run at least 35 seconds so it has a chance to record a checkpoint. Use Android **Settings → Apps → Para → Force stop**, then open Para. Check for an **Interrupted** overview with the last recorded checkpoint. Save it or discard it, then confirm you can start a new trip.

## 5. Tagum–Davao pilot ride

Choose a stop along the route and start before boarding. Enable airplane mode, select the bundled stop, and lock the phone. Record the time or approximate location of the first usable fix, approach chime, and arrival alarm. Confirm dismissal and review the path and recorded distance. Note any tracking gaps, delayed alerts, missing map detail, and battery percentage before and after the trip.

Use these results to update the open device verification entries in the acceptance checklist. Restore any phone settings changed for the test.
