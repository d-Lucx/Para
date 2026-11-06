import { readFileSync, openSync, readSync, closeSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';

const [tooling, input, assets, borderPath] = process.argv.slice(2);
const border = borderPath ? JSON.parse(readFileSync(borderPath)) : null;
const require = createRequire(resolve(tooling, 'package.json'));
const { PMTiles } = require('pmtiles');
const { VectorTile } = await import(pathToFileURL(require.resolve('@mapbox/vector-tile')).href);
const { default: Pbf } = await import(pathToFileURL(require.resolve('pbf')).href);
const packs = JSON.parse(readFileSync(join(assets, 'streets/index.json')));
let checked = 0;
const verifiedTags = new Set();
const tagSamples = [
  ['roads', 'oneway', 1], ['roads', 'oneway', -1],
  ...['Restaurant', 'Bank', 'Clinic', 'Fuel', 'Traffic signal'].map(kind => ['landmarks', 'kind', kind]),
];
for (const pack of packs) {
  const path = join(assets, 'streets', pack.file);
  const bytes = readFileSync(path);
  if (bytes.length !== pack.bytes || createHash('sha256').update(bytes).digest('hex') !== pack.sha256) throw Error(`Hash mismatch: ${pack.name}`);
  const fd = openSync(path);
  try {
    const archive = new PMTiles({
      getKey: () => path,
      async getBytes(offset, length) {
        const buffer = Buffer.alloc(length);
        const count = readSync(fd, buffer, 0, length, offset);
        return { data: buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + count) };
      },
    });
    const header = await archive.getHeader();
    const metadata = await archive.getMetadata();
    if (header.specVersion !== 3 || header.maxZoom !== 14 || metadata.name !== pack.name) throw Error(`Bad header: ${pack.name}`);
    const features = readFileSync(join(input, `${pack.id}.jsonl`), 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse);
    async function sampleLayer(feature, layer) {
      const [lon, lat] = feature.geometry.type === 'Point' ? feature.geometry.coordinates : feature.geometry.coordinates[Math.floor(feature.geometry.coordinates.length / 2)];
      const x = Math.floor((lon + 180) / 360 * 16384);
      const y = Math.floor((1 - Math.asinh(Math.tan(lat * Math.PI / 180)) / Math.PI) / 2 * 16384);
      const result = await archive.getZxy(14, x, y);
      return result && new VectorTile(new Pbf(new Uint8Array(result.data))).layers[layer];
    }
    for (const layer of ['roads', 'landmarks']) {
      const feature = features.find(f => f.properties.layer === layer);
      if (!feature) throw Error(`Missing ${layer}: ${pack.name}`);
      if (!(await sampleLayer(feature, layer))?.length) throw Error(`Unreadable ${layer} tile: ${pack.name}`);
      checked++;
    }
    for (const [layer, field, expected] of tagSamples) {
      const key = `${field}=${expected}`;
      if (verifiedTags.has(key)) continue;
      const candidates = features.filter(f => f.properties.layer === layer && f.properties[field] === expected).slice(0, 12);
      for (const feature of candidates) {
        const decoded = await sampleLayer(feature, layer);
        if (!decoded || !Array.from({ length: decoded.length }, (_, i) => decoded.feature(i).properties[field]).includes(expected)) continue;
        verifiedTags.add(key);
        console.log(`PASS encoded ${key}: ${pack.name}`);
        break;
      }
    }
    if (border?.pack === pack.id) {
      for (const point of border.points) {
        const [lon, lat] = point.coordinates;
        const x = Math.floor((lon + 180) / 360 * 16384);
        const y = Math.floor((1 - Math.asinh(Math.tan(lat * Math.PI / 180)) / Math.PI) / 2 * 16384);
        const result = await archive.getZxy(14, x, y);
        const roads = result && new VectorTile(new Pbf(new Uint8Array(result.data))).layers.roads;
        if (!roads || !Array.from({ length: roads.length }, (_, i) => roads.feature(i)).some(f => f.properties.name === border.name)) {
          throw Error(`Cross-border geometry missing: ${border.name}, ${point.region}`);
        }
        console.log(`PASS cross-border ${border.name}: ${point.region}`);
      }
    }
    console.log(`PASS ${pack.name}: hash, header, roads and landmark tiles`);
  } finally { closeSync(fd); }
}
console.log(`Verified ${packs.length} packs and ${checked} sample layers; ${(packs.reduce((n, p) => n + p.bytes, 0) / 1048576).toFixed(1)} MiB total.`);
if (process.env.PARA_VERIFY_STREET_TAGS === '1' && verifiedTags.size !== tagSamples.length) {
  throw Error(`Missing encoded street tag samples: ${tagSamples.map(([, field, value]) => `${field}=${value}`).filter(key => !verifiedTags.has(key)).join(', ')}`);
}
