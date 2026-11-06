"""Build pilot geography and source-associated entrances, without modifying national packs.

uv run --with osmium --with shapely python tools/extract-pilot-detail.py source.osm.pbf output
"""
import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

import osmium
from shapely.geometry import box, mapping, shape, Point
from shapely.strtree import STRtree

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('source', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--bounds', type=float, nargs=4, metavar=('WEST', 'SOUTH', 'EAST', 'NORTH'),
    default=[125.51, 6.99, 125.89, 7.48])
parser.add_argument('--name', default='Tagum–Davao pilot detail')
args = parser.parse_args()
source, output, bounds = args.source, args.output, args.bounds
west, south, east, north = bounds
if not (-180 <= west < east <= 180 and -85 <= south < north <= 85):
    parser.error('bounds must be west south east north in increasing order')
output.mkdir(parents=True, exist_ok=True)
pilot = box(*bounds)
factory = osmium.geom.GeoJSONFactory()
features, entrances, landmarks = [], [], []
counts = Counter()


def feature(layer, source_id, tags, geometry, **extra):
    return dict(type='Feature', properties=dict(layer=layer, source_id=source_id,
        name=tags.get('name', tags.get('name:en', '')), **extra), geometry=mapping(geometry))


def area_kind(tags):
    if tags.get('building') not in (None, 'no'):
        return 'buildings', tags.get('building')
    if tags.get('natural') == 'water' or tags.get('waterway') == 'riverbank' or tags.get('landuse') == 'reservoir':
        return 'water', tags.get('water', 'water')
    if tags.get('leisure') in ('park', 'garden', 'nature_reserve'):
        return 'landuse', tags.get('leisure')
    if tags.get('landuse') in ('forest', 'grass', 'meadow', 'recreation_ground', 'residential', 'industrial', 'commercial', 'retail'):
        return 'landuse', tags.get('landuse')
    return None


def overlaps_bounds(nodes):
    """Reject distant geometry before converting it to Shapely."""
    min_lon, min_lat, max_lon, max_lat = 180, 90, -180, -90
    for node in nodes:
        if not node.location.valid():
            continue
        lon, lat = node.lon, node.lat
        if west <= lon <= east and south <= lat <= north:
            return True
        min_lon, min_lat = min(min_lon, lon), min(min_lat, lat)
        max_lon, max_lat = max(max_lon, lon), max(max_lat, lat)
    return min_lon <= east and max_lon >= west and min_lat <= north and max_lat >= south


class Extract(osmium.SimpleHandler):
    def node(self, node):
        if node.tags.get('entrance') in (None, 'no') or not node.location.valid():
            return
        point = Point(node.lon, node.lat)
        if pilot.covers(point):
            entrances.append(dict(id=f'osm:n/{node.id}', latitude=node.lat, longitude=node.lon,
                kind=node.tags.get('entrance'), access=node.tags.get('access', ''), name=node.tags.get('name', '')))

    def way(self, way):
        highway = way.tags.get('highway')
        if not highway or len(way.nodes) < 2:
            return
        if not overlaps_bounds(way.nodes):
            return
        try:
            geometry = shape(json.loads(factory.create_linestring(way)))
        except (RuntimeError, ValueError):
            counts['invalid_lines'] += 1
            return
        if not geometry.intersects(pilot):
            return
        layer = 'paths' if highway in ('footway', 'path', 'steps', 'pedestrian') else 'road-detail'
        features.append(feature(layer, f'osm:w/{way.id}', way.tags, geometry.intersection(pilot),
            kind=highway, access=way.tags.get('access', ''), foot=way.tags.get('foot', '')))
        counts[layer] += 1

    def area(self, area):
        category = area_kind(area.tags)
        is_landmark = area.tags.get('name') and (area.tags.get('amenity') or area.tags.get('shop') or area.tags.get('aeroway'))
        if not category and not is_landmark:
            return
        if not any(overlaps_bounds(ring) for ring in area.outer_rings()):
            return
        try:
            geometry = shape(json.loads(factory.create_multipolygon(area)))
            if not geometry.is_valid or geometry.is_empty:
                counts['invalid_areas'] += 1
                return
            if not geometry.intersects(pilot):
                return
            clipped = geometry.intersection(pilot)
            if clipped.geom_type not in ('Polygon', 'MultiPolygon') or clipped.area == 0:
                return
        except (RuntimeError, ValueError):
            counts['invalid_areas'] += 1
            return
        source_id = f"osm:{'w' if area.from_way() else 'r'}/{area.orig_id()}"
        if is_landmark:
            landmarks.append((source_id, area.tags.get('name'), geometry))
        if category:
            layer, kind = category
            features.append(feature(layer, source_id, area.tags, clipped, kind=kind))
            counts[layer] += 1
            counts['polygon_holes'] += sum(len(p.interiors) for p in ([clipped] if clipped.geom_type == 'Polygon' else clipped.geoms))


print('Extracting pilot areas, paths, road hierarchy and entrance nodes...', flush=True)
Extract().apply_file(str(source), locations=True, idx='flex_mem', filters=[
    osmium.filter.KeyFilter('building', 'landuse', 'natural', 'leisure', 'waterway', 'highway', 'entrance', 'amenity', 'shop', 'aeroway')])
# An entrance must intersect a mapped landmark footprint, not just be nearby.
# Roundoff at shared OSM boundaries is handled by a ~1 cm buffer.
tree = STRtree([item[2] for item in landmarks])
associated = []
for entrance in entrances:
    matches = tree.query(Point(entrance['longitude'], entrance['latitude']).buffer(1e-7), predicate='intersects')
    for index in matches:
        landmark_id, landmark_name, footprint = landmarks[int(index)]
        if footprint.boundary.distance(Point(entrance['longitude'], entrance['latitude'])) > 1e-7:
            continue
        associated.append(dict(**entrance, landmark_id=landmark_id, landmark_name=landmark_name))
counts['entrance_nodes'] = len(entrances)
counts['associated_entrances'] = len(associated)
(output / 'pilot.geojson').write_text(json.dumps(dict(type='FeatureCollection', features=features), separators=(',', ':'), ensure_ascii=False), encoding='utf8')
(output / 'entrances.json').write_text(json.dumps(associated, separators=(',', ':'), ensure_ascii=False), encoding='utf8')
with source.open('rb') as source_file:
    source_hash = hashlib.file_digest(source_file, 'sha256').hexdigest()
manifest = dict(name=args.name, bounds=bounds, source_sha256=source_hash, counts=dict(counts))
(output / 'source.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
print(json.dumps(manifest), flush=True)
