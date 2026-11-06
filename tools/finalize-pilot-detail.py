"""Suppress independently tagged outer member areas already represented by a valid relation.

Run after extraction and before tiling; preserves relation holes and standalone areas
when their parent relation was not assembled successfully.
"""
import json
import sys
from collections import Counter
from pathlib import Path
import osmium

source, output = map(Path, sys.argv[1:])
data = json.loads((output / 'pilot.geojson').read_text(encoding='utf8'))
relations = {f['properties']['source_id']: f['properties']['layer'] for f in data['features']
    if f['properties']['source_id'].startswith('osm:r/')}
suppressed = set()

class Relations(osmium.SimpleHandler):
    def relation(self, relation):
        layer = relations.get(f'osm:r/{relation.id}')
        if layer:
            for member in relation.members:
                if member.type == 'w' and member.role in ('outer', ''):
                    suppressed.add((f'osm:w/{member.ref}', layer))

Relations().apply_file(str(source))
original = len(data['features'])
data['features'] = [f for f in data['features'] if
    (f['properties']['source_id'], f['properties']['layer']) not in suppressed]
removed_duplicates = original - len(data['features'])
before_dimensions = len(data['features'])

def renderable(feature):
    geometry = feature['geometry']
    linear = feature['properties']['layer'] in ('paths', 'road-detail')
    single, multi = ('LineString', 'MultiLineString') if linear else ('Polygon', 'MultiPolygon')
    if geometry['type'] == 'GeometryCollection':
        def parts(item):
            if item['type'] == single:
                yield item['coordinates']
            elif item['type'] == multi:
                yield from item['coordinates']
            elif item['type'] == 'GeometryCollection':
                for child in item['geometries']:
                    yield from parts(child)
        coordinates = list(parts(geometry))
        if not coordinates:
            return False
        feature['geometry'] = dict(type=multi, coordinates=coordinates)
        return True
    return geometry['type'] in (single, multi)

data['features'] = [f for f in data['features'] if renderable(f)]
before_unbuilt = len(data['features'])
data['features'] = [f for f in data['features'] if not (f['properties']['layer'] == 'road-detail' and
    f['properties']['kind'] in ('construction', 'proposed', 'abandoned', 'razed', 'demolished'))]
manifest = json.loads((output / 'source.json').read_text())
counts = Counter(f['properties']['layer'] for f in data['features'])
for layer in ('buildings', 'water', 'landuse', 'paths', 'road-detail'):
    manifest['counts'][layer] = counts[layer]
manifest['counts']['duplicate_member_areas_removed'] = manifest['counts'].get('duplicate_member_areas_removed', 0) + removed_duplicates
manifest['counts']['non_renderable_geometry_removed'] = manifest['counts'].get('non_renderable_geometry_removed', 0) + before_dimensions - before_unbuilt
manifest['counts']['unbuilt_roads_removed'] = manifest['counts'].get('unbuilt_roads_removed', 0) + before_unbuilt - len(data['features'])
manifest['counts']['polygon_holes'] = sum(
    sum(max(0, len(polygon) - 1) for polygon in f['geometry']['coordinates'])
    if f['geometry']['type'] == 'MultiPolygon' else max(0, len(f['geometry']['coordinates']) - 1)
    if f['geometry']['type'] == 'Polygon' else 0 for f in data['features'])
(output / 'pilot.geojson').write_text(json.dumps(data, separators=(',', ':'), ensure_ascii=False), encoding='utf8')
(output / 'source.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
print(f"PASS relation normalization: removed {removed_duplicates} duplicate outer-member areas")
