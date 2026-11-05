// Independent PMTiles/MVT reader. Add --fixture to check the synthetic courtyard.
import { readFileSync, openSync, readSync, closeSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
const [tooling,input,assets,mode] = process.argv.slice(2);
const require=createRequire(resolve(tooling,'package.json'));
const {PMTiles}=require('pmtiles');
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
  const features=JSON.parse(readFileSync(join(input,'pilot.geojson'))).features;
  for(const layer of ['buildings','water','landuse','paths','road-detail']) {
    const f=features.find(f=>f.properties.layer===layer);if(!f)throw Error(`Missing source ${layer}`);
    let coordinate=f.geometry.coordinates;while(Array.isArray(coordinate[0]))coordinate=coordinate[0];
    const data=await tile(...coordinate);
    if(!data.layers[layer]?.length) throw Error(`Missing decoded ${layer}`);
    console.log(`PASS pilot ${layer}: independent MVT decode`);
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
    const [west,south,east,north]=manifest.bounds;let checked=0;
    for(let z=11;z<=16;z++) {
      const [left,top]=tileAt(west,north,z),[right,bottom]=tileAt(east,south,z);
      for(let x=left-1;x<=right+1;x++) for(let y=top-1;y<=bottom+1;y++) {
        const result=await archive.getZxy(z,x,y);if(!result)continue;
        const decoded=new VectorTile(new Pbf(new Uint8Array(result.data)));
        for(const layer of Object.values(decoded.layers)) for(let i=0;i<layer.length;i++) layer.feature(i).loadGeometry();
        checked++;
      }
    }
    if(checked!==manifest.tiles)throw Error(`Decoded ${checked}, expected ${manifest.tiles} tiles`);
    console.log(`PASS all ${checked} pilot tiles and feature geometry streams decoded`);
  }
  console.log(`PASS pilot hash/header: ${manifest.tiles} tiles, ${manifest.bytes} bytes`);
} finally {closeSync(fd);}
