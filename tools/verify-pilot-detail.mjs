// Independent PMTiles/MVT reader. Add --fixture to check the synthetic courtyard.
import { readFileSync, openSync, readSync, closeSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
const [tooling,input,assets,mode] = process.argv.slice(2);
const require=createRequire(resolve(tooling,'package.json'));
const {PMTiles,tileIdToZxy}=require('pmtiles');
const {VectorTile}=await import(pathToFileURL(require.resolve('@mapbox/vector-tile')).href);
const {default:Pbf}=await import(pathToFileURL(require.resolve('pbf')).href);
const path=join(assets,'pilot/detail.pmtiles'), bytes=readFileSync(path);
const manifest=JSON.parse(readFileSync(join(assets,'pilot/index.json')));
if(bytes.length!==manifest.bytes || createHash('sha256').update(bytes).digest('hex')!==manifest.sha256) throw Error('Pilot integrity mismatch');
const fd=openSync(path);
const archive=new PMTiles({getKey:()=>path,async getBytes(offset,length) {
  const b=Buffer.alloc(length); const count=readSync(fd,b,0,length,offset);return {data:b.buffer.slice(b.byteOffset,b.byteOffset+count)};
}});
function tileAt(lon,lat,z) {return [Math.floor((lon+180)/360*2**z),Math.floor((1-Math.asinh(Math.tan(lat*Math.PI/180))/Math.PI)/2*2**z)];}
async function tile(lon,lat,z=16) {const [x,y]=tileAt(lon,lat,z);const r=await archive.getZxy(z,x,y);if(!r)throw Error('Missing tile');return new VectorTile(new Pbf(new Uint8Array(r.data)));}
try {
  const header=await archive.getHeader(), metadata=await archive.getMetadata();
  if(header.specVersion!==3 || header.maxZoom!==16 || metadata.vector_layers.length!==5) throw Error('Invalid pilot header/schema');
  const expectedLayers=['buildings','water','landuse','paths','road-detail'];
  if(mode!=='--all') {
    const features=JSON.parse(readFileSync(join(input,'pilot.geojson'))).features;
    for(const layer of expectedLayers) {
      const f=features.find(f=>f.properties.layer===layer);if(!f)throw Error(`Missing source ${layer}`);
      let coordinate=f.geometry.coordinates;while(Array.isArray(coordinate[0]))coordinate=coordinate[0];
      const data=await tile(...coordinate);
      if(!data.layers[layer]?.length) throw Error(`Missing decoded ${layer}`);
      console.log(`PASS pilot ${layer}: independent MVT decode`);
    }
  }
  if(mode==='--fixture') {
    const lon=125.605,lat=7.105,z=16,[x,y]=tileAt(lon,lat,z);
    const data=await tile(lon,lat,z), layer=data.layers.buildings;
    const px=((lon+180)/360*2**z-x)*4096, py=((1-Math.asinh(Math.tan(lat*Math.PI/180))/Math.PI)/2*2**z-y)*4096;
    function inside(ring) {let hit=false;for(let i=0,j=ring.length-1;i<ring.length;j=i++) {const a=ring[i],b=ring[j];if((a.y>py)!==(b.y>py)&&px<(b.x-a.x)*(py-a.y)/(b.y-a.y)+a.x)hit=!hit;}return hit;}
    for(let i=0;i<(layer?.length??0);i++) {
      const filled=layer.feature(i).loadGeometry().reduce((v,ring)=>v!==inside(ring),false);
      if(filled)throw Error('MVT courtyard hole filled');
    }
    console.log('PASS courtyard remains empty after PMTiles/MVT tiling');
  }
  if(mode==='--all') {
    let checked=0;
    const seenLayers=new Set();
    async function decodeDirectory(offset,length,depth) {
      if(depth>3)throw Error('PMTiles directory nesting exceeds the format limit');
      const entries=await archive.cache.getDirectory(archive.source,offset,length,header);
      for(const entry of entries) {
        if(!entry.runLength) {
          await decodeDirectory(header.leafDirectoryOffset+entry.offset,entry.length,depth+1);
          continue;
        }
        for(let id=entry.tileId;id<entry.tileId+entry.runLength;id++) {
          const result=await archive.getZxy(...tileIdToZxy(id));
          if(!result)throw Error(`Missing indexed tile ${id}`);
          const decoded=new VectorTile(new Pbf(new Uint8Array(result.data)));
          for(const [name,layer] of Object.entries(decoded.layers)) {
            seenLayers.add(name);
            for(let i=0;i<layer.length;i++) layer.feature(i).loadGeometry();
          }
          checked++;
        }
      }
    }
    await decodeDirectory(header.rootDirectoryOffset,header.rootDirectoryLength,0);
    if(checked!==manifest.tiles)throw Error(`Decoded ${checked}, expected ${manifest.tiles} tiles`);
    for(const layer of expectedLayers) {
      if(manifest.counts[layer]>0 && !seenLayers.has(layer))throw Error(`Missing decoded ${layer}`);
    }
    console.log(`PASS all ${checked} pilot tiles and feature geometry streams decoded`);
  }
  console.log(`PASS pilot hash/header: ${manifest.tiles} tiles, ${manifest.bytes} bytes`);
} finally {closeSync(fd);}
