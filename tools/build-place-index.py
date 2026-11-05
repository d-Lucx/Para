"""Build a read-only FTS4 catalog compatible with Android's bundled SQLite."""
import hashlib
import json
import math
import re
import sqlite3
import sys
import unicodedata
from pathlib import Path

assets, intermediates = map(Path, sys.argv[1:])
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
for query in ('manila*', 'cebu* south* bus* terminal*', 'san* pedro* cathedral*'):
    assert db.execute('SELECT count(*) FROM search WHERE search MATCH ?', (query,)).fetchone()[0] > 0, query
count = db.execute('SELECT count(*) FROM places').fetchone()[0]
db.close()
data = path.read_bytes()
(assets / 'place-search.json').write_text(json.dumps(dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), places=count)))
print(f'FTS4 catalog: {count:,} places, {len(data) / 1048576:.1f} MiB; integrity and search checks passed')
