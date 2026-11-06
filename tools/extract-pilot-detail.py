"""Extract detailed geography and source-associated entrances for a pilot or a batch of regions.

uv run --with osmium --with shapely python tools/extract-pilot-detail.py source.osm.pbf output
"""
import argparse
import hashlib
import json
from collections import Counter
from contextlib import ExitStack
from pathlib import Path

import osmium
from shapely import prepare
from shapely.geometry import box, mapping, shape, Point
from shapely.strtree import STRtree

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('source', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--bounds', type=float, nargs=4, metavar=('WEST', 'SOUTH', 'EAST', 'NORTH'),
    default=[125.51, 6.99, 125.89, 7.48])
parser.add_argument('--name', default='Tagum–Davao pilot detail')
parser.add_argument('--region-geometry', type=Path, help='Clip to the polygon in this GeoJSON feature')
parser.add_argument('--exclude-bounds', type=float, nargs=4, action='append', default=[],
    metavar=('WEST', 'SOUTH', 'EAST', 'NORTH'))
parser.add_argument('--coverage-only', action='store_true', help='Write coverage for a matching search build and exit')
parser.add_argument('--regions', type=Path, help='Extract all regions in the given regional assets directory in one pass')
parser.add_argument('--skip-region', action='append', default=[])
args = parser.parse_args()
source, output, bounds = args.source, args.output, args.bounds
coverage = None
if args.regions and (args.region_geometry or args.exclude_bounds or args.coverage_only):
    parser.error('--regions cannot be combined with single-region clipping options')
if args.region_geometry:
    coverage = shape(json.loads(args.region_geometry.read_text(encoding='utf8'))['geometry'])
    if not coverage.is_valid:
        parser.error('Region geometry must be valid')
    for excluded in args.exclude_bounds:
        coverage = coverage.difference(box(*excluded))
    if coverage.is_empty or coverage.geom_type not in ('Polygon', 'MultiPolygon'):
        parser.error('Region coverage must be a nonempty polygon')
    bounds = list(coverage.bounds)
elif args.exclude_bounds:
    parser.error('--exclude-bounds requires --region-geometry')
west, south, east, north = bounds
if not (-180 <= west < east <= 180 and -85 <= south < north <= 85):
    parser.error('bounds must be west south east north in increasing order')
output.mkdir(parents=True, exist_ok=True)
if args.coverage_only:
    if coverage is None:
        parser.error('--coverage-only requires --region-geometry')
    (output / 'source.json').write_text(json.dumps(dict(bounds=bounds, coverage=mapping(coverage))), encoding='utf8')
    raise SystemExit(0)
factory = osmium.geom.GeoJSONFactory()


class ExtractArea:
    def __init__(self, directory, name, polygon, exact, stack):
        self.directory = directory
        self.name = name
        self.polygon = polygon
        self.exact = exact
        self.counts = Counter()
        self.entrances = []
        self.landmarks = []
        directory.mkdir(parents=True, exist_ok=True)
        self.stream = stack.enter_context((directory / 'pilot.geojson').open('w', encoding='utf8'))
        self.stream.write('{"type":"FeatureCollection","features":[\n')
        self.separator = ''

    def add(self, item):
        self.stream.write(self.separator)
        self.stream.write(json.dumps(item, separators=(',', ':'), ensure_ascii=False))
        self.separator = ',\n'

    def finish(self, source_hash):
        self.stream.write('\n]}')
        self.stream.close()
        # Allow roundoff at mapped footprint boundaries, but reject interior points.
        tree = STRtree([item[2] for item in self.landmarks])
        associated = []
        for entrance in self.entrances:
            point = Point(entrance['longitude'], entrance['latitude'])
            for index in tree.query(point.buffer(1e-7), predicate='intersects'):
                landmark_id, landmark_name, footprint = self.landmarks[int(index)]
                if footprint.boundary.distance(point) <= 1e-7:
                    associated.append(dict(**entrance, landmark_id=landmark_id, landmark_name=landmark_name))
        self.counts['entrance_nodes'] = len(self.entrances)
        self.counts['associated_entrances'] = len(associated)
        (self.directory / 'entrances.json').write_text(json.dumps(associated, separators=(',', ':'), ensure_ascii=False), encoding='utf8')
        manifest = dict(name=self.name, bounds=self.polygon.bounds, source_sha256=source_hash, counts=dict(self.counts))
        if self.exact:
            manifest['coverage'] = mapping(self.polygon)
        (self.directory / 'source.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
        print(f'{self.name}: {sum(self.counts.values()):,} feature and metadata records', flush=True)


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
    def __init__(self, areas):
        super().__init__()
        self.areas = areas
        self.tree = STRtree([area.polygon for area in areas])

    def matches(self, geometry):
        for index in self.tree.query(geometry):
            area = self.areas[int(index)]
            if area.polygon.intersects(geometry):
                yield area

    def node(self, node):
        if node.tags.get('entrance') in (None, 'no') or not node.location.valid():
            return
        for area in self.matches(Point(node.lon, node.lat)):
            area.entrances.append(dict(id=f'osm:n/{node.id}', latitude=node.lat, longitude=node.lon,
                kind=node.tags.get('entrance'), access=node.tags.get('access', ''), name=node.tags.get('name', '')))

    def way(self, way):
        highway = way.tags.get('highway')
        if not highway or len(way.nodes) < 2 or not overlaps_bounds(way.nodes):
            return
        try:
            geometry = shape(json.loads(factory.create_linestring(way)))
        except (RuntimeError, ValueError):
            return
        layer = 'paths' if highway in ('footway', 'path', 'steps', 'pedestrian') else 'road-detail'
        for area in self.matches(geometry):
            clipped = geometry if area.polygon.covers(geometry) else geometry.intersection(area.polygon)
            area.add(feature(layer, f'osm:w/{way.id}', way.tags, clipped,
                kind=highway, access=way.tags.get('access', ''), foot=way.tags.get('foot', '')))
            area.counts[layer] += 1

    def area(self, area):
        category = area_kind(area.tags)
        name = area.tags.get('name')
        is_landmark = name and (area.tags.get('amenity') or area.tags.get('shop') or area.tags.get('aeroway'))
        if not category and not is_landmark:
            return
        if not any(overlaps_bounds(ring) for ring in area.outer_rings()):
            return
        try:
            geometry = shape(json.loads(factory.create_multipolygon(area)))
        except (RuntimeError, ValueError):
            return
        if not geometry.is_valid or geometry.is_empty:
            return
        source_id = f"osm:{'w' if area.from_way() else 'r'}/{area.orig_id()}"
        for target in self.matches(geometry):
            clipped = geometry if target.polygon.covers(geometry) else geometry.intersection(target.polygon)
            if clipped.geom_type not in ('Polygon', 'MultiPolygon') or clipped.area == 0:
                continue
            if is_landmark:
                target.landmarks.append((source_id, name, geometry))
            if category:
                layer, kind = category
                target.add(feature(layer, source_id, area.tags, clipped, kind=kind))
                target.counts[layer] += 1


with ExitStack() as stack:
    if args.regions:
        areas = []
        for entry in json.loads((args.regions / 'index.json').read_text(encoding='utf8')):
            if entry['id'] in args.skip_region:
                continue
            polygon = shape(json.loads((args.regions / entry['file']).read_text(encoding='utf8'))['geometry'])
            if not polygon.is_valid or polygon.is_empty or polygon.geom_type not in ('Polygon', 'MultiPolygon'):
                parser.error(f"Invalid region polygon: {entry['name']}")
            areas.append(ExtractArea(output / entry['id'], entry['name'], polygon, True, stack))
        if not areas:
            parser.error('No regions selected')
        west = min(area.polygon.bounds[0] for area in areas)
        south = min(area.polygon.bounds[1] for area in areas)
        east = max(area.polygon.bounds[2] for area in areas)
        north = max(area.polygon.bounds[3] for area in areas)
    else:
        polygon = coverage if coverage is not None else box(*bounds)
        areas = [ExtractArea(output, args.name, polygon, coverage is not None, stack)]
    for area in areas:
        prepare(area.polygon)
    print(f'Extracting detailed geography and entrances for {len(areas)} area(s)...', flush=True)
    Extract(areas).apply_file(str(source), locations=True, idx='flex_mem', filters=[
        osmium.filter.KeyFilter('building', 'landuse', 'natural', 'leisure', 'waterway', 'highway', 'entrance', 'amenity', 'shop', 'aeroway')])
    with source.open('rb') as source_file:
        source_hash = hashlib.file_digest(source_file, 'sha256').hexdigest()
    for area in areas:
        area.finish(source_hash)
