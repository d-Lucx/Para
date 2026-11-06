"""Extract a lean, reproducible commuter basemap from a Geofabrik OSM PBF.

uv run --python 3.12 --with osmium --with shapely --with pillow --with scipy \
  python tools/extract-philippine-streets.py source.osm.pbf assets output-directory
Each feature has one regional owner. Cross-border geometry is retained whole,
so rendering all packs preserves roads without duplicate overlapping features.
"""
import json
import sys
from pathlib import Path

import osmium
from shapely import prepare
from shapely.geometry import Point, shape
from shapely.strtree import STRtree
from map_osm_tags import road_properties, street_amenity

source, assets, output = map(Path, sys.argv[1:])
output.mkdir(parents=True, exist_ok=True)
regions = json.loads((assets / 'regions/index.json').read_text())
polygons = [shape(json.loads((assets / 'regions' / r['file']).read_text())['geometry']) for r in regions]
prepare(polygons)
tree = STRtree(polygons)
streams = [(output / f"{r['id']}.jsonl").open('w', encoding='utf8') for r in regions]
counts = [dict(roads=0, landmarks=0) for _ in regions]
pois = []
major = {'motorway', 'motorway_link', 'trunk', 'trunk_link', 'primary', 'primary_link', 'secondary', 'secondary_link'}
minor = {'tertiary', 'tertiary_link', 'residential', 'unclassified', 'living_street', 'service', 'pedestrian'}
amenities = {'bus_station', 'ferry_terminal', 'school', 'university', 'college', 'marketplace', 'place_of_worship', 'townhall', 'police', 'fire_station'}


def owner(lon, lat):
    point = Point(lon, lat)
    for index in tree.query(point):
        if polygons[index].covers(point):
            return int(index)
    return int(tree.nearest(point))


def emit(region, layer, properties, geometry):
    feature = dict(type='Feature', properties=dict(layer=layer, **properties), geometry=geometry)
    streams[region].write(json.dumps(feature, ensure_ascii=False, separators=(',', ':')) + '\n')


def landmark(tags):
    category = street_amenity(tags)
    if category:
        return category
    if tags.get('amenity') in amenities:
        return tags.get('amenity').replace('_', ' ').capitalize()
    if tags.get('railway') in {'station', 'halt'}:
        return 'Railway station'
    if tags.get('highway') == 'bus_stop' or tags.get('public_transport') in {'station', 'platform'}:
        return 'Transit stop'
    if tags.get('aeroway') in {'aerodrome', 'terminal'}:
        return 'Airport'
    if tags.get('shop') in {'mall', 'supermarket', 'department_store'}:
        return 'Shopping'
    if tags.get('leisure') == 'park':
        return 'Park'
    return None


class Extract(osmium.SimpleHandler):
    def poi(self, entity, lon, lat):
        category = landmark(entity.tags)
        name = entity.tags.get('name') or entity.tags.get('name:en')
        if not category or (not name and not street_amenity(entity.tags)):
            return
        region = owner(lon, lat)
        name = ' '.join((name or '').split())[:140]
        emit(region, 'landmarks', dict(name=name, kind=category), dict(type='Point', coordinates=[lon, lat]))
        if name and category != 'Traffic signal':
            pois.append([f'osm:{entity.type_str()}/{entity.id}', name, f"{category} · {regions[region]['name']}", lat, lon, entity.tags.get('alt_name', '')])
        counts[region]['landmarks'] += 1

    def node(self, node):
        if landmark(node.tags) and node.location.valid():
            self.poi(node, node.location.lon, node.location.lat)

    def way(self, way):
        highway = way.tags.get('highway')
        railway = way.tags.get('railway')
        waterway = way.tags.get('waterway')
        kind = ('main' if highway in major else 'local' if highway in minor else
                'rail' if railway in {'rail', 'light_rail', 'subway', 'tram'} else
                'water' if waterway in {'river', 'canal'} else None)
        has_poi = landmark(way.tags) and (street_amenity(way.tags) or way.tags.get('name') or way.tags.get('name:en'))
        if not kind and not has_poi:
            return
        if any(not n.location.valid() for n in way.nodes):
            return
        coords = [[round(n.lon, 7), round(n.lat, 7)] for n in way.nodes]
        if len(coords) < 2:
            return
        lon, lat = coords[len(coords) // 2]
        if kind:
            region = owner(lon, lat)
            properties = road_properties(way.tags) if kind in ('main', 'local') else dict(name=way.tags.get('name', '')[:140])
            emit(region, 'roads', dict(kind=kind, **properties), dict(type='LineString', coordinates=coords))
            counts[region]['roads'] += 1
        if has_poi:
            # A representative point is inside a closed landmark footprint.
            if coords[0] == coords[-1] and len(coords) >= 4:
                from shapely.geometry import Polygon
                center = Polygon(coords).representative_point()
                lon, lat = center.x, center.y
            self.poi(way, lon, lat)


print('Reading OSM nodes and ways...', flush=True)
try:
    Extract().apply_file(str(source), locations=True, idx='flex_mem', filters=[
        osmium.filter.KeyFilter('highway', 'railway', 'waterway', 'amenity', 'public_transport', 'aeroway', 'shop', 'leisure'),
    ])
finally:
    for stream in streams:
        stream.close()
with (output / 'street-landmarks.tsv').open('w', encoding='utf8', newline='\n') as stream:
    stream.write('# OSM named landmarks; © OpenStreetMap contributors, ODbL 1.0\n')
    for row in pois:
        stream.write('\t'.join(str(v).replace('\t', ' ').replace('\n', ' ') for v in row) + '\n')
(output / 'counts.json').write_text(json.dumps(counts))
print(f"Extracted {sum(r['roads'] for r in counts):,} road/rail/water ways and {len(pois):,} named landmarks", flush=True)
