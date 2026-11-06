"""Run with the same osmium/shapely environment as the extractor."""
import json
import subprocess
import sys
import tempfile
from pathlib import Path
from shapely.geometry import shape, Point

root = Path(__file__).resolve().parent
with tempfile.TemporaryDirectory() as directory:
    subprocess.run([sys.executable, str(root / 'extract-pilot-detail.py'), str(root / 'test-fixtures/pilot.osm'), directory], check=True)
    subprocess.run([sys.executable, str(root / 'finalize-pilot-detail.py'), str(root / 'test-fixtures/pilot.osm'), directory], check=True)
    features = json.loads((Path(directory) / 'pilot.geojson').read_text())['features']
    building = [f for f in features if f['properties']['layer'] == 'buildings']
    assert len(building) == 1, 'Relation/member building duplicated or missing'
    polygon = shape(building[0]['geometry'])
    assert not polygon.covers(Point(125.605, 7.105)), 'Multipolygon courtyard was filled'
    assert polygon.covers(Point(125.601, 7.101))
    entrances = json.loads((Path(directory) / 'entrances.json').read_text())
    assert [e['id'] for e in entrances] == ['osm:n/1'], 'Interior/unrelated entrance incorrectly associated'
    assert entrances[0]['landmark_id'] == 'osm:r/100'
    road = next(f for f in features if f['properties']['layer'] == 'road-detail')
    assert shape(road['geometry']).bounds[0] == 125.51, 'Cross-border road not clipped at pilot edge'
    path = next(f for f in features if f['properties']['layer'] == 'paths')
    assert path['properties']['access'] == 'private', 'Access restrictions lost'
    assert any(f['properties']['layer'] == 'water' for f in features)
    assert any(f['properties']['layer'] == 'landuse' for f in features)
    adjacent = Path(directory) / 'adjacent'
    subprocess.run([sys.executable, str(root / 'extract-pilot-detail.py'), str(root / 'test-fixtures/pilot.osm'),
        str(adjacent), '--bounds', '125.45', '6.99', '125.51', '7.48', '--name', 'Adjacent fixture'], check=True)
    adjacent_features = json.loads((adjacent / 'pilot.geojson').read_text())['features']
    adjacent_road = next(f for f in adjacent_features if f['properties']['source_id'] == road['properties']['source_id'])
    seam = shape(road['geometry']).intersection(shape(adjacent_road['geometry']))
    assert seam.geom_type == 'Point' and seam.x == 125.51, 'Adjacent road pieces must share their boundary endpoint'
    region_file = Path(directory) / 'region.json'
    region_file.write_text(json.dumps({'type': 'Feature', 'properties': {},
        'geometry': {'type': 'Polygon', 'coordinates': [[[125.45, 6.95], [125.95, 6.95],
            [125.95, 7.5], [125.45, 7.5], [125.45, 6.95]]]}}), encoding='utf8')
    region_out = Path(directory) / 'region'
    subprocess.run([sys.executable, str(root / 'extract-pilot-detail.py'), str(root / 'test-fixtures/pilot.osm'),
        str(region_out), '--region-geometry', str(region_file), '--exclude-bounds',
        '125.51', '6.99', '125.89', '7.48'], check=True)
    region_manifest = json.loads((region_out / 'source.json').read_text())
    coverage = shape(region_manifest['coverage'])
    assert not coverage.covers(Point(125.605, 7.105)), 'Existing detail must be excluded from region coverage'
    region_features = json.loads((region_out / 'pilot.geojson').read_text())['features']
    region_road = next(f for f in region_features if f['properties']['source_id'] == road['properties']['source_id'])
    assert shape(region_road['geometry']).intersection(shape(road['geometry'])).equals(seam)
    batch = Path(directory) / 'batch'
    regions = Path(directory) / 'regions'
    regions.mkdir()
    entries = []
    for key, west, east in [('left', 125.45, 125.51), ('right', 125.51, 125.89)]:
        filename = f'{key}.geojson'
        entries.append(dict(id=key, name=key, file=filename))
        polygon = dict(type='Polygon', coordinates=[[[west, 6.99], [east, 6.99], [east, 7.48], [west, 7.48], [west, 6.99]]])
        (regions / filename).write_text(json.dumps(dict(type='Feature', geometry=polygon)), encoding='utf8')
    (regions / 'index.json').write_text(json.dumps(entries), encoding='utf8')
    subprocess.run([sys.executable, str(root / 'extract-pilot-detail.py'), str(root / 'test-fixtures/pilot.osm'),
        str(batch), '--regions', str(regions)], check=True)
    subprocess.run([sys.executable, str(root / 'finalize-pilot-detail.py'), str(root / 'test-fixtures/pilot.osm'),
        str(batch / 'left'), str(batch / 'right')], check=True)
    left = json.loads((batch / 'left/pilot.geojson').read_text())['features']
    right = json.loads((batch / 'right/pilot.geojson').read_text())['features']
    assert left == adjacent_features and right == features, 'Batch and single-area extraction disagree'
print('PASS pilot relation holes, duplicate handling, boundary entrances, border clipping, access tags and geographic layers')
