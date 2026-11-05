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
print('PASS pilot relation holes, duplicate handling, boundary entrances, border clipping, access tags and geographic layers')
