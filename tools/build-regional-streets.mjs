import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
import { gzipSync } from 'node:zlib';

// npm install --prefix <tooling> geojson-vt@4.0.2 vt-pbf@3.1.3 pmtiles@4.3.0
// node --max-old-space-size=8192 tools/build-regional-streets.mjs tooling input assets
const [tooling, input, assets] = process.argv.slice(2);
const require = createRequire(resolve(tooling, 'package.json'));
const { default: geojsonvt } = await import(pathToFileURL(require.resolve('geojson-vt')).href);
const { fromGeojsonVt } = require('vt-pbf');
const { zxyToTileId } = require('pmtiles');
const regions = JSON.parse(readFileSync(join(assets, 'regions/index.json')));
const counts = JSON.parse(readFileSync(join(input, 'counts.json')));
const output = join(assets, 'streets');
mkdirSync(output, { recursive: true });
const manifest = [];

function directory(entries) {
  const bytes = [];
  function varint(value) {
    while (value >= 128) { bytes.push(value % 128 + 128); value = Math.floor(value / 128); }
    bytes.push(value);
  }
  varint(entries.length);
  let previous = 0;
  for (const e of entries) { varint(e.id - previous); previous = e.id; }
  for (const e of entries) varint(e.run ?? 1);
  for (const e of entries) varint(e.bytes.length);
  for (const e of entries) varint(e.offset + 1);
  return gzipSync(Buffer.from(bytes));
}

for (const [position, region] of regions.entries()) {
  const features = readFileSync(join(input, `${region.id}.jsonl`), 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse);
  if (!features.length) continue;
  const coordinates = features.flatMap(f => f.geometry.type === 'Point' ? [f.geometry.coordinates] : f.geometry.coordinates);
  const bounds = coordinates.reduce((b, [x, y]) => [Math.min(b[0], x), Math.min(b[1], y), Math.max(b[2], x), Math.max(b[3], y)], [180, 90, -180, -90]);
  const indexes = Object.fromEntries(['roads', 'landmarks'].map(layer => [layer, geojsonvt({
    type: 'FeatureCollection', features: features.filter(f => f.properties.layer === layer),
  }, { maxZoom: 14, indexMaxZoom: 14, indexMaxPoints: 0, tolerance: 3, buffer: 64 })]));
  const coords = new Map();
  for (const index of Object.values(indexes)) for (const c of index.tileCoords) {
    if (c.z >= 8) coords.set(zxyToTileId(c.z, c.x, c.y), c);
  }
  let offset = 0;
  const tiles = [];
  for (const [id, { z, x, y }] of [...coords].sort(([a], [b]) => a - b)) {
    const layers = {};
    for (const [name, index] of Object.entries(indexes)) {
      const tile = index.getTile(z, x, y);
      if (tile?.features.length) {
        tile.features = tile.features.filter(f => z >= (name === 'landmarks' ? 13 : f.tags.kind === 'local' ? 12 : f.tags.kind === 'rail' ? 10 : 8));
        if (tile.features.length) layers[name] = tile;
      }
    }
    if (!Object.keys(layers).length) continue;
    const bytes = gzipSync(fromGeojsonVt(layers, { version: 2 }));
    tiles.push({ id, bytes, offset });
    offset += bytes.length;
  }
  const leafEntries = [];
  let leafOffset = 0;
  for (let i = 0; i < tiles.length; i += 1024) {
    const bytes = directory(tiles.slice(i, i + 1024));
    leafEntries.push({ id: tiles[i].id, run: 0, bytes, offset: leafOffset });
    leafOffset += bytes.length;
  }
  const root = directory(leafEntries);
  if (root.length + 127 > 16384) throw Error('Root directory too large');
  const metadata = gzipSync(JSON.stringify({ name: region.name, attribution: '© OpenStreetMap contributors',
    vector_layers: ['roads', 'landmarks'].map(id => ({ id, fields: {
      name: 'String', kind: 'String', ...(id === 'roads' ? { oneway: 'Number' } : {}),
    }, minzoom: 8, maxzoom: 14 })) }));
  const header = Buffer.alloc(127);
  header.write('PMTiles'); header[7] = 3;
  const leafStart = 127 + root.length + metadata.length;
  const dataStart = leafStart + leafOffset;
  [127, root.length, 127 + root.length, metadata.length, leafStart, leafOffset, dataStart,
    offset, tiles.length, tiles.length, tiles.length].forEach((v, i) => header.writeBigUInt64LE(BigInt(v), 8 + i * 8));
  header.set([1, 2, 2, 1, 8, 14], 96);
  bounds.forEach((v, i) => header.writeInt32LE(Math.round(v * 1e7), 102 + i * 4));
  header[118] = 10;
  header.writeInt32LE(Math.round((bounds[0] + bounds[2]) / 2 * 1e7), 119);
  header.writeInt32LE(Math.round((bounds[1] + bounds[3]) / 2 * 1e7), 123);
  const bytes = Buffer.concat([header, root, metadata, ...leafEntries.map(e => e.bytes), ...tiles.map(t => t.bytes)]);
  const hash = createHash('sha256').update(bytes).digest('hex');
  const file = `${region.id}.pmtiles`;
  writeFileSync(join(output, file), bytes);
  manifest.push({ id: region.id, name: region.name, file, sha256: hash, bytes: bytes.length, tiles: tiles.length, bounds, ...counts[position] });
  console.log(`${position + 1}/18 ${region.name}: ${tiles.length} tiles, ${(bytes.length / 1048576).toFixed(1)} MiB`);
}
writeFileSync(join(output, 'index.json'), JSON.stringify(manifest));
