"""Suppress independently tagged outer member areas already represented by a valid relation.

Run after extraction and before tiling; preserves relation holes and standalone areas
when their parent relation was not assembled successfully.
"""
import json
import sys
from collections import Counter
from pathlib import Path
import osmium

def features(directory, relations_only=False):
    with (directory / 'pilot.geojson').open(encoding='utf8') as stream:
        first = stream.readline()
        if first.strip() != '{"type":"FeatureCollection","features":[':
            for item in json.loads(first + stream.read())['features']:
                yield item, None
            return
        for line in stream:
            line = line.strip()
            if line == ']}':
                return
            if line and (not relations_only or '"source_id":"osm:r/' in line):
                row = line.rstrip(',')
                yield json.loads(row), row
        raise ValueError(f'Truncated feature collection: {directory}')


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

def normalize(directory, suppressed):
    manifest = json.loads((directory / 'source.json').read_text(encoding='utf8'))
    counts = Counter({layer: 0 for layer in ('buildings', 'water', 'landuse', 'paths', 'road-detail')})
    for key in ('duplicate_member_areas_removed', 'non_renderable_geometry_removed', 'unbuilt_roads_removed'):
        counts[key] = manifest['counts'].get(key, 0)
    counts['polygon_holes'] = 0
    target = directory / 'pilot.geojson'
    staging = target.with_suffix('.tmp')
    with staging.open('w', encoding='utf8') as stream:
        stream.write('{"type":"FeatureCollection","features":[\n')
        separator = ''
        for item, row in features(directory):
            props = item['properties']
            layer = props['layer']
            if (props['source_id'], layer) in suppressed:
                counts['duplicate_member_areas_removed'] += 1
                continue
            needs_rewrite = item['geometry']['type'] == 'GeometryCollection'
            if not renderable(item):
                counts['non_renderable_geometry_removed'] += 1
                continue
            if layer == 'road-detail' and props['kind'] in ('construction', 'proposed', 'abandoned', 'razed', 'demolished'):
                counts['unbuilt_roads_removed'] += 1
                continue
            geometry = item['geometry']
            if geometry['type'] in ('Polygon', 'MultiPolygon'):
                polygons = [geometry['coordinates']] if geometry['type'] == 'Polygon' else geometry['coordinates']
                counts['polygon_holes'] += sum(len(polygon) - 1 for polygon in polygons)
            counts[layer] += 1
            stream.write(separator)
            if row is None or needs_rewrite:
                row = json.dumps(item, separators=(',', ':'), ensure_ascii=False)
            stream.write(row)
            separator = ',\n'
        stream.write('\n]}')
    staging.replace(target)
    manifest['counts'].update(counts)
    (directory / 'source.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
    print(f"PASS {manifest['name']}: removed {counts['duplicate_member_areas_removed']} duplicate outer-member areas", flush=True)


def main():
    source = Path(sys.argv[1])
    directories = [Path(path) for path in sys.argv[2:]]
    if not directories:
        raise ValueError('Specify at least one extracted detail directory')
    relations = {}
    suppressed = {directory: set() for directory in directories}
    for directory in directories:
        for item, _ in features(directory, relations_only=True):
            props = item['properties']
            source_id = props['source_id']
            if source_id.startswith('osm:r/'):
                relations.setdefault(int(source_id[6:]), []).append((directory, props['layer']))

    class Relations(osmium.SimpleHandler):
        def relation(self, relation):
            for directory, layer in relations.get(relation.id, ()):
                for member in relation.members:
                    if member.type == 'w' and member.role in ('outer', ''):
                        suppressed[directory].add((f'osm:w/{member.ref}', layer))

    Relations().apply_file(str(source))
    for directory in directories:
        normalize(directory, suppressed[directory])


if __name__ == '__main__':
    main()
