// node tools/build-pilot-detail.mjs <tooling> <extracted> <assets>
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
import { gzipSync } from 'node:zlib';
const [tooling, input, assets] = process.argv.slice(2);
const require = createRequire(resolve(tooling, 'package.json'));
const { default: geojsonvt } = await import(pathToFileURL(require.resolve('geojson-vt')).href);
const { fromGeojsonVt } = require('vt-pbf');
const { zxyToTileId } = require('pmtiles');
const source = JSON.parse(readFileSync(join(input, 'source.json')));
if (!Object.hasOwn(source.counts, 'duplicate_member_areas_removed')) throw Error('Run finalize-pilot-detail.py before tiling');
const data = JSON.parse(readFileSync(join(input, 'pilot.geojson')));
const minZoom = { landuse: 11, water: 11, buildings: 15, paths: 14, 'road-detail': 11 };
const regional = Boolean(source.coverage);
const firstZoom = 11;
const indexes = Object.fromEntries(Object.keys(minZoom).map(layer => [layer, geojsonvt({type:'FeatureCollection',
  features:data.features.filter(f => f.properties.layer === layer)},
  {maxZoom:16,indexMaxZoom:regional ? firstZoom : 16,indexMaxPoints:0,tolerance:1,buffer:64})]));
const coordinates = new Map();
for (const index of Object.values(indexes)) for (const tile of index.tileCoords) {
  if (tile.z >= firstZoom) coordinates.set(zxyToTileId(tile.z,tile.x,tile.y),tile);
}
const tiles = [];
function encode(z, x, y) {
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
      for (let dy=0;dy<2;dy++) for (let dx=0;dx<2;dx++) encode(z+1,x*2+dx,y*2+dy);
    }
    // geojson-vt 4.0.2 retains visited tiles. Release a completed subtree once
    // its descendants have been encoded; its siblings keep their own sources.
    const tileKey = ((2**z * y + x) * 32) + z;
    for (const index of Object.values(indexes)) delete index.tiles[tileKey];
  }
}
for (const {z,x,y} of coordinates.values()) encode(z,x,y);
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
