// node tools/build-pilot-detail.mjs <tooling> <extracted> <assets>
import { appendFileSync, closeSync, createReadStream, mkdirSync, mkdtempSync, openSync, readFileSync,
  readSync, rmdirSync, statSync, unlinkSync, writeFileSync } from 'node:fs';
import { createInterface } from 'node:readline';
import { createRequire } from 'node:module';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
import { gzipSync } from 'node:zlib';
const [tooling, input, assets, mode] = process.argv.slice(2);
const require = createRequire(resolve(tooling, 'package.json'));
const { default: geojsonvt } = await import(pathToFileURL(require.resolve('geojson-vt')).href);
const { fromGeojsonVt } = require('vt-pbf');
const { zxyToTileId } = require('pmtiles');
const source = JSON.parse(readFileSync(join(input, 'source.json')));
if (!Object.hasOwn(source.counts, 'duplicate_member_areas_removed')) throw Error('Run finalize-pilot-detail.py before tiling');
const minZoom = { landuse: 11, water: 11, buildings: 15, paths: 14, 'road-detail': 11 };
const regional = Boolean(source.coverage);
const firstZoom = 11;
const tileBuffer = 64;
const groupZoom = 9;
const inputPath = join(input, 'pilot.geojson');
const start = '{"type":"FeatureCollection","features":[';
const prefix = Buffer.alloc(start.length + 1);
const fd = openSync(inputPath, 'r');
try {
  readSync(fd, prefix);
} finally {
  closeSync(fd);
}
const streamed = prefix.subarray(0,-1).toString() === start && [10,13].includes(prefix.at(-1));
const legacy = streamed ? null : JSON.parse(readFileSync(inputPath)).features;
async function* featureRows(path, token = '') {
  const stream = createReadStream(path, {encoding:'utf8'});
  try {
    for await (const line of createInterface({input:stream,crlfDelay:Infinity})) {
      if (line === ']}') return;
      if (line && line !== start && line.includes(token)) yield line.endsWith(',') ? line.slice(0,-1) : line;
    }
  } finally {
    stream.destroy();
  }
  throw Error('Truncated feature collection');
}
async function loadLayer(path, layer) {
  if (legacy) return legacy.filter(feature => feature.properties.layer === layer);
  const features = [];
  for await (const row of featureRows(path, `"layer":"${layer}"`)) {
    const feature = JSON.parse(row);
    if (feature.properties.layer === layer) features.push(feature);
  }
  return features;
}
async function tileGroup(path, counts, group) {
  const indexes = {};
  for (const layer of Object.keys(minZoom)) {
    const features = await loadLayer(path, layer);
    if (features.length !== (counts[layer] ?? 0)) throw Error(`Feature count mismatch for ${layer}`);
    indexes[layer] = geojsonvt({type:'FeatureCollection',features},
      {maxZoom:16,indexMaxZoom:regional ? firstZoom : 16,indexMaxPoints:0,tolerance:1,buffer:tileBuffer});
  }
  const coordinates = new Map();
  for (const index of Object.values(indexes)) for (const tile of index.tileCoords) {
    if (tile.z < firstZoom) continue;
    const scale = 2**(tile.z-groupZoom);
    if (!group || (Math.floor(tile.x/scale)===group.x && Math.floor(tile.y/scale)===group.y)) {
      coordinates.set(zxyToTileId(tile.z,tile.x,tile.y),tile);
    }
  }
  for (const {z,x,y} of coordinates.values()) encode(indexes,z,x,y);
}
const tiles = [];
function encode(indexes, z, x, y) {
  const layers = {};
  let hasPoints = false;
  for (const [layer,index] of Object.entries(indexes)) {
    if (!regional && z < minZoom[layer]) continue;
    const tile = index.getTile(z,x,y);
    if (tile?.numPoints > 0) hasPoints = true;
    if (z >= minZoom[layer] && tile?.features.length) layers[layer] = tile;
  }
  if (Object.keys(layers).length) {
    const bytes = gzipSync(fromGeojsonVt(layers,{version:2}));
    tiles.push({id:zxyToTileId(z,x,y),bytes});
  }
  if (regional) {
    if (hasPoints && z < 16) {
      for (let dy=0;dy<2;dy++) for (let dx=0;dx<2;dx++) encode(indexes,z+1,x*2+dx,y*2+dy);
    }
    // geojson-vt 4.0.2 retains visited tiles. Release a completed subtree once
    // its descendants have been encoded; its siblings keep their own sources.
    const tileKey = ((2**z * y + x) * 32) + z;
    for (const index of Object.values(indexes)) delete index.tiles[tileKey];
  }
}
function groupBounds(coordinates) {
  let west=Infinity, east=-Infinity, south=Infinity, north=-Infinity;
  function visit(points) {
    if (typeof points[0] === 'number') {
      west=Math.min(west,points[0]); east=Math.max(east,points[0]);
      south=Math.min(south,points[1]); north=Math.max(north,points[1]);
    } else for (const child of points) visit(child);
  }
  visit(coordinates);
  if (!Number.isFinite(west)) throw Error('Cannot partition a feature without coordinates');
  const projectX = longitude => longitude/360 + 0.5;
  const projectY = latitude => (1-Math.asinh(Math.tan(latitude*Math.PI/180))/Math.PI)/2;
  const margin = tileBuffer/4096/2**firstZoom + Number.EPSILON;
  const group = position => Math.floor(position*2**groupZoom);
  return [group(projectX(west)-margin),group(projectY(north)-margin),
    group(projectX(east)+margin),group(projectY(south)+margin)];
}
async function tileLargeRegion() {
  mkdirSync(assets,{recursive:true});
  const temporary = mkdtempSync(join(resolve(assets),'.tile-groups-'));
  const groups = new Map();
  const totals = {};
  try {
    // Adjacent groups share buffered input, but each owns disjoint output tiles.
    for await (const row of featureRows(inputPath)) {
      const feature = JSON.parse(row);
      const layer = feature.properties.layer;
      totals[layer] = (totals[layer] ?? 0) + 1;
      const [left,top,right,bottom] = groupBounds(feature.geometry.coordinates);
      for (let x=left;x<=right;x++) for (let y=top;y<=bottom;y++) {
        const key = `${x}-${y}`;
        if (!groups.has(key)) groups.set(key,{x,y,path:join(temporary,key),rows:'',counts:{}});
        const group = groups.get(key);
        group.counts[layer] = (group.counts[layer] ?? 0) + 1;
        group.rows += row + '\n';
        if (group.rows.length >= 128*1024) {
          appendFileSync(group.path,group.rows);
          group.rows = '';
        }
      }
    }
    for (const layer of Object.keys(minZoom)) {
      if ((totals[layer] ?? 0) !== (source.counts[layer] ?? 0)) throw Error(`Feature count mismatch for ${layer}`);
    }
    for (const group of groups.values()) {
      appendFileSync(group.path,group.rows + ']}\n');
      group.rows = '';
      await tileGroup(group.path,group.counts,group);
    }
  } finally {
    for (const {path} of groups.values()) {
      try { unlinkSync(path); } catch (error) { if (error.code !== 'ENOENT') throw error; }
    }
    rmdirSync(temporary);
  }
}
if (streamed && regional && (mode==='--partition' || statSync(inputPath).size > 192*1024*1024)) {
  await tileLargeRegion();
} else {
  await tileGroup(inputPath,source.counts);
}
tiles.sort((a,b) => a.id-b.id);
let offset = 0;
for (const tile of tiles) { tile.offset = offset; offset += tile.bytes.length; }
if (!tiles.length) throw Error('Empty pilot archive');
function directory(entries) {
  const out=[];
  function varint(n) { while(n>=128) {out.push(n%128+128);n=Math.floor(n/128);} out.push(n); }
  varint(entries.length); let previous=0;
  for(const e of entries) {varint(e.id-previous);previous=e.id;}
  for(const e of entries) varint(e.run??1);
  for(const e of entries) varint(e.bytes.length);
  for(const e of entries) varint(e.offset+1);
  return gzipSync(Buffer.from(out));
}
const leaves=[];let leafLength=0;
for(let i=0;i<tiles.length;i+=1024) {const bytes=directory(tiles.slice(i,i+1024));leaves.push({id:tiles[i].id,run:0,bytes,offset:leafLength});leafLength+=bytes.length;}
const root=directory(leaves);
if(root.length+127>16384) throw Error('Oversized PMTiles root');
const metadata=gzipSync(JSON.stringify({name:source.name ?? 'Tagum–Davao pilot detail',attribution:'© OpenStreetMap contributors',
  vector_layers:Object.keys(minZoom).map(id=>({id,minzoom:minZoom[id],maxzoom:16,fields:{name:'String',kind:'String',source_id:'String',access:'String',foot:'String'}}))}));
const header=Buffer.alloc(127);header.write('PMTiles');header[7]=3;
const leafStart=127+root.length+metadata.length, dataStart=leafStart+leafLength;
[127,root.length,127+root.length,metadata.length,leafStart,leafLength,dataStart,offset,tiles.length,tiles.length,tiles.length]
  .forEach((v,i)=>header.writeBigUInt64LE(BigInt(v),8+i*8));
header.set([1,2,2,1,11,16],96);
source.bounds.forEach((v,i)=>header.writeInt32LE(Math.round(v*1e7),102+i*4));
const [west,south,east,north]=source.bounds;
header[118]=13;
header.writeInt32LE(Math.round((west+east)*5e6),119);
header.writeInt32LE(Math.round((south+north)*5e6),123);
const bytes=Buffer.concat([header,root,metadata,...leaves.map(e=>e.bytes),...tiles.map(t=>t.bytes)]);
const output=join(assets,'pilot');mkdirSync(output,{recursive:true});
writeFileSync(join(output,'detail.pmtiles'),bytes);
writeFileSync(join(output,'entrances.json'),readFileSync(join(input,'entrances.json')));
writeFileSync(join(output,'index.json'),JSON.stringify({...source,bytes:bytes.length,tiles:tiles.length,
  sha256:createHash('sha256').update(bytes).digest('hex'),schema:1}));
console.log(JSON.stringify({bytes:bytes.length,tiles:tiles.length,...source.counts}));
