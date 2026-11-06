"""Package verified Para map and search outputs for a versioned HTTPS catalog."""

import argparse
import gzip
import hashlib
import json
import re
import sqlite3
from pathlib import Path
from urllib.parse import urlsplit
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_verified(directory, name, metadata):
    data = (directory / name).read_bytes()
    if len(data) != metadata['bytes'] or sha256(data) != metadata['sha256']:
        raise ValueError(f'Unexpected size or hash for {name}')
    return data


def package(pack_id, detail, search, output, version, base):
    detail_manifest = (detail / 'index.json').read_bytes()
    metadata = json.loads(detail_manifest)
    search_manifest = (search / 'place-search.json').read_bytes()
    search_metadata = json.loads(search_manifest)
    west, south, east, north = metadata['bounds']
    if metadata['schema'] != 1 or not (-180 <= west < east <= 180 and -85 <= south < north <= 85):
        raise ValueError(f'Invalid detail schema or bounds for {pack_id}')
    tiles = read_verified(detail, 'detail.pmtiles', metadata)
    if not tiles.startswith(b'PMTiles\x03'):
        raise ValueError(f'Invalid PMTiles header for {pack_id}')
    compressed_search = (search / 'place-search.sqlite.gzip').read_bytes()
    database = gzip.decompress(compressed_search)
    if len(database) != search_metadata['bytes'] or sha256(database) != search_metadata['sha256']:
        raise ValueError(f'Invalid search database checksum for {pack_id}')
    with sqlite3.connect(':memory:') as db:
        db.deserialize(database)
        count, outside = db.execute(
            'SELECT count(*), sum(longitude NOT BETWEEN ? AND ? OR latitude NOT BETWEEN ? AND ?) FROM places',
            (west, east, south, north),
        ).fetchone()
        if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok' or count != search_metadata['places'] or outside:
            raise ValueError(f'Invalid search database or coverage for {pack_id}')
    entrances = (detail / 'entrances.json').read_bytes()
    if len(json.loads(entrances)) != metadata['counts']['associated_entrances']:
        raise ValueError(f'Invalid entrance count for {pack_id}')
    notice = (
        f"{metadata['name']}\n\n"
        "Map data © OpenStreetMap contributors, ODbL 1.0.\n"
        "https://www.openstreetmap.org/copyright\n"
        "https://opendatacommons.org/licenses/odbl/1-0/\n"
        "Geofabrik Philippines extract: https://download.geofabrik.de/asia/philippines.html\n"
        f"Source PBF SHA-256: {metadata['source_sha256']}\n"
        "Place names also include GeoNames, CC BY 4.0.\n"
        "https://www.geonames.org/\nhttps://creativecommons.org/licenses/by/4.0/\n"
    ).encode()
    filename = f'{pack_id}-{version}.zip'
    path = output / filename
    with ZipFile(path, 'w') as archive:
        for name, data, compression in (
            ('detail.pmtiles', tiles, ZIP_STORED),
            ('entrances.json', entrances, ZIP_DEFLATED),
            ('index.json', detail_manifest, ZIP_DEFLATED),
            ('NOTICE.txt', notice, ZIP_DEFLATED),
            ('place-search.json', search_manifest, ZIP_DEFLATED),
            ('place-search.sqlite.gzip', compressed_search, ZIP_STORED),
        ):
            archive.writestr(ZipInfo(name), data, compress_type=compression)
    with ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError(f'Failed ZIP integrity check for {filename}')
    return dict(
        id=pack_id, name=metadata['name'], version=version, bounds=metadata['bounds'],
        url=f'{base}/{filename}', bytes=path.stat().st_size, sha256=sha256(path.read_bytes()),
        mapSchema=metadata['schema'], searchSchema=1, places=count, tiles=metadata['tiles'],
        sourceSha256=metadata['source_sha256'],
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('--version', required=True)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--pack', nargs=3, metavar=('ID', 'DETAIL_DIR', 'SEARCH_DIR'), action='append', required=True)
    args = parser.parse_args()
    base = args.base_url.rstrip('/')
    url = urlsplit(base)
    ids = [item[0] for item in args.pack]
    if url.scheme != 'https' or not url.netloc or url.query or url.fragment or url.username:
        parser.error('Use a versioned HTTPS base URL')
    if not all(re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', item) for item in [args.version, *ids]) or len(set(ids)) != len(ids):
        parser.error('Use unique lowercase hyphenated IDs and version')
    args.output.mkdir(parents=True, exist_ok=True)
    packs = [package(pack_id, Path(detail), Path(search), args.output, args.version, base)
             for pack_id, detail, search in sorted(args.pack)]
    (args.output / 'catalog.json').write_text(json.dumps(dict(schema=1, packs=packs), indent=2) + '\n', encoding='utf8')
    for pack in packs:
        print(f"{pack['id']}: {pack['bytes']:,} bytes, {pack['tiles']:,} tiles, {pack['places']:,} places")


if __name__ == '__main__':
    main()
