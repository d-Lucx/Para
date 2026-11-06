"""Build a read-only FTS4 catalog compatible with Android's bundled SQLite."""
import argparse
import gzip
import hashlib
import json
import math
import re
import sqlite3
import unicodedata
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('assets', type=Path)
parser.add_argument('intermediates', type=Path)
parser.add_argument('--bounds', type=float, nargs=4, metavar=('WEST', 'SOUTH', 'EAST', 'NORTH'))
parser.add_argument('--coverage-manifest', type=Path, help='Use the exact coverage in a detail source manifest')
args = parser.parse_args()
assets, intermediates = args.assets, args.intermediates
coverage = None
if args.coverage_manifest:
    if args.bounds:
        parser.error('Choose --bounds or --coverage-manifest')
    from shapely import prepare
    from shapely.geometry import Point, shape
    manifest = json.loads(args.coverage_manifest.read_text(encoding='utf8'))
    coverage = shape(manifest['coverage'])
    prepare(coverage)
    args.bounds = manifest['bounds']
if args.bounds:
    west, south, east, north = args.bounds
    if not (-180 <= west < east <= 180 and -85 <= south < north <= 85):
        parser.error('bounds must be west south east north in increasing order')
path = assets / 'place-search.sqlite'
path.unlink(missing_ok=True)
regions = json.loads((assets / 'regions/index.json').read_text())


def normalized(text):
    text = ''.join(c for c in unicodedata.normalize('NFKD', text.lower()) if not unicodedata.combining(c))
    return ' '.join(re.findall(r'[^\W_]+', text))


db = sqlite3.connect(path)
db.executescript('''
PRAGMA journal_mode=OFF;
PRAGMA user_version=1;
CREATE TABLE places(id TEXT NOT NULL UNIQUE, name TEXT NOT NULL, locality TEXT NOT NULL,
 latitude REAL NOT NULL, longitude REAL NOT NULL, lat_e7 INTEGER NOT NULL, lon_e7 INTEGER NOT NULL,
 region TEXT NOT NULL, name_key TEXT NOT NULL, show_on_map INTEGER NOT NULL);
CREATE VIRTUAL TABLE search USING fts4(tokens, tokenize=unicode61);
''')
for file in [assets / 'corridor-stops.tsv', assets / 'region-places.tsv', intermediates / 'street-landmarks.tsv']:
    for line in file.read_text(encoding='utf8').splitlines():
        if not line or line.startswith('#'):
            continue
        fields = line.split('\t')
        ident, name, locality = fields[:3]
        lat, lon = map(float, fields[3:5])
        if args.bounds and not (west <= lon <= east and south <= lat <= north):
            continue
        if coverage is not None and not coverage.covers(Point(lon, lat)):
            continue
        aliases = fields[5] if len(fields) > 5 else ''
        region = next((r['name'] for r in regions if locality.endswith(r['name'])), '')
        show = int(file.name == 'corridor-stops.tsv' or (len(fields) > 6 and fields[6] == 'map'))
        cursor = db.execute('INSERT INTO places VALUES(?,?,?,?,?,?,?,?,?,?)',
            (ident, name, locality, lat, lon, math.floor(lat * 1e7 + .5), math.floor(lon * 1e7 + .5), region, normalized(name), show))
        db.execute('INSERT INTO search(rowid,tokens) VALUES(?,?)', (cursor.lastrowid, normalized(f'{name} {locality} {aliases}')))
db.executescript('CREATE INDEX coordinates ON places(lat_e7,lon_e7); CREATE INDEX nearby ON places(latitude,longitude);')
db.execute("INSERT INTO search(search) VALUES('optimize')")
db.commit()
assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
if args.bounds:
    sample = db.execute("SELECT name_key FROM places WHERE name_key != '' LIMIT 1").fetchone()
    assert sample, 'No searchable places in these bounds'
    queries = (' '.join(f'{term}*' for term in sample[0].split()),)
else:
    queries = ('manila*', 'cebu* south* bus* terminal*', 'san* pedro* cathedral*')
for query in queries:
    assert db.execute('SELECT count(*) FROM search WHERE search MATCH ?', (query,)).fetchone()[0] > 0, query
count = db.execute('SELECT count(*) FROM places').fetchone()[0]
db.close()
data = path.read_bytes()
# AAPT expands assets ending in .gz, so keep the .gzip suffix.
(assets / 'place-search.sqlite.gzip').write_bytes(gzip.compress(data, compresslevel=9, mtime=0))
path.unlink()
(assets / 'place-search.json').write_text(json.dumps(dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), places=count)))
print(f'FTS4 catalog: {count:,} places, {len(data) / 1048576:.1f} MiB; integrity and search checks passed')
