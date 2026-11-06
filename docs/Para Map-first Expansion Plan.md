# Para map-first expansion

Updated November 6, 2026.

The rider approved a map-first Home, a driving route preview with estimated travel time, and explicit confirmation before downloading an area. The revised light-mode Stitch layouts are approved.

## Map and destination flow

1. Open Home to the map, search and a recenter control. Keep History and Settings in the bottom navigation.
2. Let riders tap a landmark, drop an arbitrary pin within coverage or enter coordinates. The destination sheet shows source details, entrance options where available and pin confirmation.
3. Offer **From** and **To**. From defaults to a fresh usable location fix. Riders can choose another origin while waiting for GPS or if location permission is unavailable. Choosing a route origin never changes the actual position marker.
4. Show the route distance and an estimated driving duration once a route is available. Show coverage or routing errors in the sheet with a way to change the endpoints. Keep **Set arrival alarm** accessible from a confirmed pin even when no route is available.
5. Use the confirmed destination for Para’s existing arrival alarm. Routing, route estimates and the recorded GPS track have separate state.

The position marker uses a dot and an accuracy circle for usable fixes. Show a heading arrow when orientation is trustworthy and a dot when it is uncertain. Use device orientation for facing and GPS bearing for direction of travel where appropriate, with explicit source handling and camera-bearing compensation. Subscribe to map location and orientation while the screen is visible. See the [Android position sensors reference](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position).

## Nationwide presentation and data

Apply the pilot’s geographic colors and road hierarchy throughout the Philippines. Detailed packs should use the same schema and style as Tagum–Davao. Actual coverage depends on mapped source features.

| Feature | Data and rendering work |
| --- | --- |
| Buildings, water, parks and paths | Generalize the pilot extractor, normalize polygon relations and holes, and preserve continuity across pack boundaries. Use zoom thresholds to control density. |
| Named roads | Preserve local and alternate names, road references and highway class. Use line labels, spacing and collision handling to favor major routes at lower zoom and local street names at higher zoom. |
| Food, banks, clinics and fuel | Extract tagged amenities and use compact, labeled category icons. Include restaurants, cafés, fast food, banks, ATMs, hospitals, clinics, pharmacies, fuel, transit and other existing landmark classes. Keep unnamed features eligible for an icon and identify them by category. |
| Road directions | Show one-way arrows where source rules support them. Preserve the OSM way direction, reverse direction values and relevant implicit rules. Use destination or road-reference labels when supplied. |
| Traffic signals | Extract signal nodes even when unnamed and show them at street-level zoom. The icon identifies a mapped signal location. |

The current national extractor stores road class and name and requires a name for a landmark. The pilot adds geographic detail, but neither map format supplies a complete routing graph. Add these attributes and signal points in the source pipeline rather than inferring them from rendered pixels. See the [OSM one-way reference](https://wiki.openstreetmap.org/wiki/Key:oneway) and [traffic-signal reference](https://wiki.openstreetmap.org/wiki/Tag:highway=traffic_signals).

## Zoom-driven downloads

Bundle a lightweight nationwide overview and the Tagum–Davao starter pack. Publish versioned, bounded subregion packs containing detailed map tiles and a matching local search index. Include matching routing data where route preview coverage is available. Keep downloaded packs usable offline.

On camera idle at detail zoom, intersect the viewport with pack boundaries locally. If relevant packs are missing, offer **Download map area** with their names and total size. A viewport that spans a boundary can suggest more than one pack. The rider confirms before transfer. Show progress, cancel/retry and installed coverage. Store and delete packs individually. Keep the base map browsable during download.

Use a static HTTPS catalog with pack IDs, bounds, versions, sizes, hashes and compatible map/search/graph versions. Send pack requests by ID. Keep raw coordinates, routes, search queries and saved data on device. Stage and validate downloaded files before publishing them together. Resume or restart interrupted downloads and preserve the last usable version during updates. Select and verify a hosting source before shipping the downloader.

This design follows Organic Maps’ regional download model. Its documentation describes selecting relevant regional maps for offline use and marks downloaded regions on the overview. Para’s specific confirmation rules and pack layout are product decisions. See the [Organic Maps map FAQ](https://organicmaps.app/faq/map/cant-download-maps/) and [downloaded-region overview](https://organicmaps.app/news/2025-11-11/detailed-world-map-highlighted-downloaded-regions-conditional-routing-support-carplay-android-auto-chromebook/).

## Driving route preview

Evaluate a local routing engine and package format against the Tagum–Davao source. Build a connected graph from road nodes and ways, with directed edges, restrictions, access, speed and bridges/tunnels. Join neighboring graphs at stable node/edge identities. Compute route distance along the graph and travel time from mapped speed data and profile defaults. Present the result as an offline driving estimate. If required coverage is missing, identify the additional packs and let the rider choose whether to download them.

Acceptance fixtures should cover a reversed one-way, a forbidden turn, a grade-separated crossing, a border crossing, manual origin with location denied, missing packs and no feasible route. Validate physical examples before expanding coverage. Live traffic, bus and jeepney schedules, and voice guidance are separate work.

## Implementation order

- [x] Confirm map-first Home, prompted area downloads and driving previews with the rider.
- [x] Generate and inspect the light-mode [route preview](designs/map-first-route-light.png) and [download prompt](designs/map-first-download-light.png) in Stitch.
- [x] Confirm the revised layouts with the rider.
- [x] Implement map-first Home with the bundled map, a destination pin and handoff to the existing confirmation and alarm setup screens.
- [x] Smoke-test the Home custom-pin handoff, landmark search and saved destinations disclosure on the Infinix.
- [ ] Finish the [device verification checklist](<Para v1 Acceptance Checklist.md>), including large text and TalkBack.
- [x] Add the foreground position marker and recenter control.
- [ ] Verify a live fix, one-shot recenter, heading and permission fallback on a phone.
- [ ] Extend the source schema and deliver the shared style with category icons, one-way arrows, traffic signals and richer road names. Validate one pilot pack and an adjacent pack before the national rebuild.
- [ ] Implement the area catalog and downloader, then verify cancellation, disk errors, integrity, version changes, boundary coverage and offline reopen.
- [ ] Verify an offline driving graph and route preview on the pilot and a cross-pack fixture. Record measured startup, frame cadence, search, disk and package-size changes before enabling broad rollout.

## Revised Stitch drafts

The revisions use the Para name and Map tab in both states, remove the microphone, show both pin markers above the sheet and give each endpoint an edit control. The download sheet uses a consistent pack name and explains when internet is needed.

The previews use illustrative geography, size, distance and duration. Implementation will fit the actual route to both endpoints, derive status from installed coverage and connectivity, label the transfer size as **Download size** and use **Driving route** in place of the preview's **Optimal route** badge.
