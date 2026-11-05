import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

// Inputs: topology-simplified PSGC ADM1 GeoJSON and GeoNames PH.txt.
const [boundaryPath, placesPath, assets] = process.argv.slice(2);
const regionsPath = join(assets, 'regions');
mkdirSync(regionsPath, { recursive: true });
const regions = JSON.parse(readFileSync(boundaryPath, 'utf8')).features.map((feature, i) => {
  const { adm1_psgc: id, adm1_en: name } = feature.properties;
  const file = `${id}.geojson`;
  const polygons = feature.geometry.type === 'Polygon' ? [feature.geometry.coordinates] : feature.geometry.coordinates;
  const bounds = [Infinity, Infinity, -Infinity, -Infinity];
  for (const [x, y] of polygons.flat(2)) {
    bounds[0] = Math.min(bounds[0], x);
    bounds[1] = Math.min(bounds[1], y);
    bounds[2] = Math.max(bounds[2], x);
    bounds[3] = Math.max(bounds[3], y);
  }
  writeFileSync(join(regionsPath, file), JSON.stringify({
    ...feature, properties: { name, shade: i % 3 },
  }));
  return { id: String(id), name, file, polygons, bounds, places: [] };
});
if (regions.length !== 18 || new Set(regions.map(r => r.id)).size !== 18) {
  throw Error('Expected 18 distinct Philippine regions');
}
writeFileSync(join(regionsPath, 'index.json'), JSON.stringify(regions.map(({ id, name, file }) => ({ id, name, file }))));

function insideRing(x, y, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [ax, ay] = ring[i];
    const [bx, by] = ring[j];
    if ((ay > y) !== (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside;
  }
  return inside;
}

const landmarks = new Map([
  ['AIRP', 'Airport'], ['PRT', 'Port'], ['RSTN', 'Railway station'], ['BUSTN', 'Bus station'],
]);
for (const line of readFileSync(placesPath, 'utf8').split(/\r?\n/)) {
  const [id, name, ascii, , latitude, longitude, kind, code, , , , , , , population] = line.split('\t');
  if (!name || name.includes('\t')) continue;
  const size = Number(population);
  const administrative = /^PPL(C|A[234]?)$/.test(code);
  if (!(kind === 'P' && (administrative || (code === 'PPL' && size >= 8_000))) && !landmarks.has(code)) continue;
  const x = Number(longitude);
  const y = Number(latitude);
  const region = regions.find(({ bounds, polygons }) =>
    x >= bounds[0] && x <= bounds[2] && y >= bounds[1] && y <= bounds[3] &&
    polygons.some(([outer, ...holes]) => insideRing(x, y, outer) && !holes.some(ring => insideRing(x, y, ring))),
  );
  if (region) region.places.push({ id, name, ascii, latitude, longitude, kind, code, size, administrative });
}
const rows = ['# GeoNames CC BY 4.0. See regions/NOTICE.txt.'];
for (const region of regions) {
  const ranked = region.places.sort((a, b) => b.size - a.size || a.name.localeCompare(b.name));
  const labelIds = new Set([
    ...ranked.filter(p => p.kind === 'P').slice(0, 18),
    ...ranked.filter(p => landmarks.has(p.code)).slice(0, 8),
  ].map(p => p.id));
  const usedNames = new Set();
  for (const p of ranked) {
    const key = `${p.name.toLowerCase()}:${Number(p.latitude).toFixed(2)}:${Number(p.longitude).toFixed(2)}`;
    if (usedNames.has(key)) continue;
    usedNames.add(key);
    const type = landmarks.get(p.code) ?? 'Populated place';
    rows.push([
      `geo:${p.id}`, p.name, `${type} · ${region.name}`, p.latitude, p.longitude,
      p.ascii, labelIds.has(p.id) ? 'map' : '',
    ].join('\t').trimEnd());
  }
}
writeFileSync(join(assets, 'region-places.tsv'), rows.join('\n') + '\n');
console.log(`Bundled ${regions.length} regional packs and ${rows.length - 1} searchable places.`);
