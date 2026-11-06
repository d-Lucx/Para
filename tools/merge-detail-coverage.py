"""Merge disjoint, finalized detail extracts from the same OSM snapshot."""
import argparse
import json
from collections import Counter
from pathlib import Path

from shapely import union_all
from shapely.geometry import box, mapping, shape


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('--name', required=True)
    parser.add_argument('inputs', type=Path, nargs='+')
    args = parser.parse_args()
    manifests = [json.loads((path / 'source.json').read_text(encoding='utf8')) for path in args.inputs]
    if len({manifest['source_sha256'] for manifest in manifests}) != 1:
        parser.error('All inputs must use the same source snapshot')
    polygons = [shape(item['coverage']) if 'coverage' in item else box(*item['bounds']) for item in manifests]
    coverage = union_all(polygons)
    if not coverage.is_valid or abs(sum(polygon.area for polygon in polygons) - coverage.area) > 1e-9:
        parser.error('Inputs must have valid, nonoverlapping coverage')
    if any('duplicate_member_areas_removed' not in item['counts'] for item in manifests):
        parser.error('Finalize each input before merging')

    args.output.mkdir(parents=True, exist_ok=True)
    counts = Counter()
    entrances = {}
    with (args.output / 'pilot.geojson').open('w', encoding='utf8') as output:
        output.write('{"type":"FeatureCollection","features":[\n')
        separator = ''
        for path, manifest in zip(args.inputs, manifests):
            counts.update(manifest['counts'])
            for item in json.loads((path / 'pilot.geojson').read_text(encoding='utf8'))['features']:
                output.write(separator)
                output.write(json.dumps(item, ensure_ascii=False, separators=(',', ':')))
                separator = ',\n'
            for entry in json.loads((path / 'entrances.json').read_text(encoding='utf8')):
                entrances[entry['id'], entry['landmark_id']] = entry
        output.write('\n]}')
    counts['associated_entrances'] = len(entrances)
    (args.output / 'entrances.json').write_text(json.dumps(list(entrances.values()), ensure_ascii=False), encoding='utf8')
    manifest = dict(name=args.name, bounds=coverage.bounds, coverage=mapping(coverage),
                    counts=dict(counts), source_sha256=manifests[0]['source_sha256'])
    (args.output / 'source.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')


if __name__ == '__main__':
    main()
