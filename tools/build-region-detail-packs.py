"""Tile, verify and package an extracted batch of regional detail maps."""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('extracted', type=Path)
    parser.add_argument('tooling', type=Path)
    parser.add_argument('assets', type=Path)
    parser.add_argument('intermediates', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--version', required=True)
    parser.add_argument('--previous-catalog', type=Path, required=True)
    parser.add_argument('--skip-region', action='append', default=[])
    parser.add_argument('--resume', action='store_true', help='Reuse packs whose staging checksums still match')
    args = parser.parse_args()
    tools = Path(__file__).resolve().parent
    regions = json.loads((args.assets / 'regions/index.json').read_text(encoding='utf8'))
    base = f'https://github.com/d-Lucx/Para/releases/download/map-data-{args.version}'
    staging_catalog = args.output / 'catalog.json'
    previous = staging_catalog if args.resume and staging_catalog.exists() else args.previous_catalog
    staged = {pack['id']: pack for pack in json.loads(previous.read_text(encoding='utf8'))['packs']}
    for region in regions:
        if region['id'] in args.skip_region:
            continue
        source = args.extracted / region['id']
        pack_id = f"region-{region['id']}"
        manifest = json.loads((source / 'source.json').read_text(encoding='utf8'))
        existing = staged.get(pack_id, {})
        zip_path = args.output / f'{pack_id}-{args.version}.zip'
        if (args.resume and existing.get('version') == args.version
                and existing.get('sourceSha256') == manifest['source_sha256'] and zip_path.is_file()):
            with zip_path.open('rb') as archive:
                same_zip = hashlib.file_digest(archive, 'sha256').hexdigest() == existing['sha256']
            if same_zip:
                with ZipFile(zip_path) as archive:
                    packed_manifest = json.loads(archive.read('index.json'))
                if all(packed_manifest[key] == value for key, value in manifest.items()):
                    print(f"Reusing verified {region['name']}", flush=True)
                    continue
        print(f"Building {region['name']}", flush=True)
        subprocess.run(['node', '--max-old-space-size=6144', str(tools / 'build-pilot-detail.mjs'),
                        args.tooling, source, source], check=True)
        subprocess.run(['node', str(tools / 'verify-pilot-detail.mjs'), args.tooling, source, source, '--all'], check=True)
        search = source / 'search'
        (search / 'regions').mkdir(parents=True, exist_ok=True)
        for name in ('regions/index.json', 'corridor-stops.tsv', 'region-places.tsv'):
            shutil.copyfile(args.assets / name, search / name)
        subprocess.run([sys.executable, str(tools / 'build-place-index.py'), search, args.intermediates,
                        '--coverage-manifest', source / 'source.json'], check=True)
        subprocess.run([sys.executable, str(tools / 'package-detail-packs.py'), args.output,
                        '--version', args.version, '--base-url', base, '--previous-catalog', previous,
                        '--pack', pack_id, source / 'pilot', search], check=True)
        previous = staging_catalog


if __name__ == '__main__':
    main()
