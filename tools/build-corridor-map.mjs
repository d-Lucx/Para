import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { gzipSync } from 'node:zlib';

// npm install --prefix <tooling-directory> geojson-vt@4.0.2 vt-pbf@3.1.3 pmtiles@4.3.0
const [toolingDirectory, source, output] = process.argv.slice(2);
const require = createRequire(resolve(toolingDirectory, 'package.json'));
const { default: geojsonvt } = await import(pathToFileURL(require.resolve('geojson-vt')).href);
const { fromGeojsonVt } = require('vt-pbf');
const { zxyToTileId } = require('pmtiles');
const data = JSON.parse(readFileSync(source, 'utf8'));
const minZoom = 8;
const maxZoom = 14;
const features = data.lines.map(([kind, points]) => ({
  type: 'Feature',
  properties: { kind },
  geometry: { type: 'LineString', coordinates: points.map(([lat, lon]) => [lon, lat]) },
}));
const places = data.places.map(([name, lat, lon]) => ({
  type: 'Feature',
  properties: { name },
  geometry: { type: 'Point', coordinates: [lon, lat] },
}));
const options = { maxZoom, indexMaxZoom: maxZoom, indexMaxPoints: 0 };
const lines = geojsonvt({ type: 'FeatureCollection', features }, options);
const labels = geojsonvt({ type: 'FeatureCollection', features: places }, options);

const coordinates = new Map();
for (const { z, x, y } of [...lines.tileCoords, ...labels.tileCoords]) {
  if (z >= minZoom) coordinates.set(zxyToTileId(z, x, y), { z, x, y });
}
const tiles = [];
for (const [id, { z, x, y }] of [...coordinates].sort(([a], [b]) => a - b)) {
  const layers = {};
  const line = lines.getTile(z, x, y);
  const label = labels.getTile(z, x, y);
  if (line?.features.length) layers.lines = line;
  if (label?.features.length) layers.places = label;
  if (Object.keys(layers).length) {
    tiles.push({ id, bytes: gzipSync(fromGeojsonVt(layers, { version: 2 })) });
  }
}
const directoryBytes = [];
function varint(value) {
  while (value >= 128) {
    directoryBytes.push((value % 128) + 128);
    value = Math.floor(value / 128);
  }
  directoryBytes.push(value);
}
varint(tiles.length);
let previousId = 0;
for (const { id } of tiles) {
  varint(id - previousId);
  previousId = id;
}
for (const tile of tiles) varint(1);
for (const { bytes } of tiles) varint(bytes.length);
tiles.forEach((_, i) => varint(i === 0 ? 1 : 0));
const directory = gzipSync(Buffer.from(directoryBytes));
if (directory.length + 127 > 16384) throw Error('PMTiles root directory exceeds 16 KiB');
const metadata = gzipSync(JSON.stringify({
  name: 'Tagum–Davao pilot',
  attribution: '© OpenStreetMap contributors',
  vector_layers: [
    { id: 'lines', fields: { kind: 'String' }, minzoom: minZoom, maxzoom: maxZoom },
    { id: 'places', fields: { name: 'String' }, minzoom: minZoom, maxzoom: maxZoom },
  ],
}));
const header = Buffer.alloc(127);
header.write('PMTiles');
header[7] = 3;
const dataOffset = 127 + directory.length + metadata.length;
const lengths = [
  127, directory.length, 127 + directory.length, metadata.length, dataOffset, 0, dataOffset,
  tiles.reduce((total, tile) => total + tile.bytes.length, 0), tiles.length, tiles.length, tiles.length,
];
lengths.forEach((value, i) => header.writeBigUInt64LE(BigInt(value), 8 + i * 8));
header.set([1, 2, 2, 1, minZoom, maxZoom], 96);
[125.51, 6.99, 125.89, 7.48].forEach((value, i) => header.writeInt32LE(Math.round(value * 1e7), 102 + i * 4));
header[118] = 10;
header.writeInt32LE(1257000000, 119);
header.writeInt32LE(72350000, 123);
writeFileSync(output, Buffer.concat([header, directory, metadata, ...tiles.map(tile => tile.bytes)]));
console.log(`${tiles.length} vector tiles written to ${output}`);
