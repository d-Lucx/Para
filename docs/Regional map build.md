# Philippine offline street packs

The Android app never downloads map data. Run this pipeline on the development machine, then rebuild the APK. The source PBF and intermediate JSONL/landmark TSV stay outside the repository; the finished regional PMTiles, read-only search database and local font glyphs are packaged assets.

1. Download the [Geofabrik Philippine OSM extract](https://download.geofabrik.de/asia/philippines.html) to a build directory. Record the source date and SHA-256 in the map notice. Keep the source snapshot for reproducibility.
2. Prepare build-only dependencies:

   ```powershell
   npm install --prefix <tooling> geojson-vt@4.0.2 vt-pbf@3.1.3 pmtiles@4.3.0 @mapbox/vector-tile@2.0.4 pbf@4.0.1
   ```

3. Extract selected roads, railways, rivers, commuter landmarks and traffic signals:

   ```powershell
   uv run --python 3.12 --with osmium --with shapely --with pillow --with scipy python tools/extract-philippine-streets.py <source.osm.pbf> app/src/main/assets <intermediates>
   ```

4. Generate all regional PMTiles and local SDF font ranges, then validate archive hashes, metadata and sample road/landmark tiles using an independent PMTiles/MVT reader:

   ```powershell
   node --max-old-space-size=8192 tools/build-regional-streets.mjs <tooling> <intermediates> app/src/main/assets
   uv run --python 3.12 python tools/build-place-index.py app/src/main/assets <intermediates>
   uv run --python 3.12 --with pillow --with scipy python tools/build-offline-glyphs.py app/src/main/res/font/inter.ttf app/src/main/assets/glyphs/Inter-Regular
   $env:PARA_VERIFY_STREET_TAGS = '1'
   node tools/verify-regional-streets.mjs <tooling> <intermediates> app/src/main/assets
   Remove-Item Env:PARA_VERIFY_STREET_TAGS
   ```

Each OSM way/landmark has exactly one regional owner. The whole way is retained, including the part crossing a regional border. All sources render simultaneously on the same map, rather than switching basemaps at a border. Assignment uses a point on the way and nearest land region for coastal geometry. This is a storage partition, not an authoritative political boundary or a routing graph.

The commuter schema includes major and local roads, railways, rivers, terminals, named civic landmarks, and categorized food, bank, health and fuel points. Selected amenities can show a category icon without a name. Traffic-signal icons show mapped signal locations. Roads use the source name, English name or road reference in that order. The direction field distinguishes forward and reverse one-ways, with implied direction for motorways and roundabouts where the source supplies no override. Zoom levels 8–14 are stored and the renderer can overzoom. Street labels start at zoom 12, selected landmark labels at zoom 13, and one-way arrows and signals at zoom 16. Map completeness depends on OSM source data.

Search uses a prebuilt SQLite FTS4 index, supported by Android's bundled SQLite. The repository and APK carry it as `place-search.sqlite.gzip`. On first use, Para expands it into a private file and verifies the original SQLite length and SHA-256 before publication. The immutable database contains public OSM and GeoNames data. Private saved destinations and trip records stay in separate encrypted files. Search supports normalized term prefixes and exact-name prioritization. Coordinate indexes handle nearby pin labels and exact source-backed coastal anchors.

Generated PMTiles use gzip-compressed MVT tiles and a root/leaf PMTiles v3 directory. `streets/index.json` records sizes, hashes and feature/tile counts. First map use verifies each asset while copying it atomically to versioned internal files. This works around the MapLibre asset-archive decompression issue; it also means installed map storage includes both the packaged archives and their internal copies. Measure release size and first-use storage before distribution.

The November 6, 2026 rebuild used the source hash already recorded in `streets/NOTICE.txt`. The 18 street archives total 128.0 MiB and the matching FTS4 index contains 171,655 places. The extractor produced 179,449 landmark and signal features and 239,883 labeled road ways. The independent reader verified all archive hashes and 36 sample layers, plus encoded examples of both one-way directions, restaurants, banks, clinics, fuel stations and traffic signals.

OSM-derived assets are ODbL 1.0; keep source attribution and the data notice. Inter glyphs derive from the existing bundled font under its OFL license. Regional boundaries and GeoNames place data retain their separate notices.

## Tagum–Davao pilot detail

The approved detail layer is a separate archive; do not replace all national packs to add it. Run these commands from the repository root using the same source snapshot and build-only dependencies above:

```powershell
uv run --python 3.12 --with osmium --with shapely python tools/test-pilot-detail.py
uv run --python 3.12 --with osmium --with shapely python tools/extract-pilot-detail.py <source.osm.pbf> <pilot-intermediates>
uv run --python 3.12 --with osmium --with shapely python tools/finalize-pilot-detail.py <source.osm.pbf> <pilot-intermediates>
node --max-old-space-size=8192 tools/build-pilot-detail.mjs <tooling> <pilot-intermediates> app/src/main/assets
node tools/verify-pilot-detail.mjs <tooling> <pilot-intermediates> app/src/main/assets --all
```

Stop on any failed command. Area assembly scans the source twice and a national extract can take several minutes. The normalization pass is mandatory: a separately tagged outer way must not cover a courtyard represented by its parent relation. Invalid areas are excluded; relation holes and clipping are checked with the synthetic fixture. Walking-path access/foot tags are retained; proposed/construction/abandoned road ways are removed before tiling.

Pilot source bounds are west 125.51, south 6.99, east 125.89, north 7.48. The schema separates land use, water, buildings, paths and road detail. Stored zooms are 11–16; buildings start at 15 and paths at 14. General coastline assembly is outside this extractor; water polygons and existing river/coast context retain their separate roles.

`pilot/index.json` records the source hash, normalized feature counts, archive hash, bytes and tile count. `pilot/entrances.json` holds source IDs and boundary-associated entrances for named area landmarks, not nearest-place guesses or verified passenger drop-offs. An unassociated or unmapped entrance uses the user-confirmed landmark/custom-pin fallback. No runtime download or routing graph is introduced.

For an independent courtyard-hole check after encoding, run the extractor, finalizer and writer against `tools/test-fixtures/pilot.osm` into temporary fixture directories, then pass `--fixture` as the final argument to `verify-pilot-detail.mjs`. The fixture also covers a separately tagged outer member, a cross-boundary road, unrelated interior entrance and a restricted footway.

To build the adjacent Tagum North detail candidate, pass `--bounds 125.51 7.48 125.89 7.72 --name "Tagum North detail"` to the same extractor. Keep its output and assets in separate staging directories and run the same finalizer, writer and verifier there. The shared edge is latitude 7.48. The archive schema matches the pilot. Area download packaging and delivery are tracked in the [map-first expansion plan](<Para Map-first Expansion Plan.md>).

Build its companion search asset by passing the same `--bounds` to `tools/build-place-index.py` with staged copies of the regional index and place TSV files. The November 6 candidate has 3,928 independently decoded detail tiles, 70,923 building areas, 959 paths and 459 searchable places. Its local staging ZIP contains the detail archive, entrance records, compressed search index, their manifests and an attribution notice.
