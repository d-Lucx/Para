"""Tile, verify and package an extracted batch of regional detail maps."""
import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

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
    args = parser.parse_args()
    tools = Path(__file__).resolve().parent
    regions = json.loads((args.assets / 'regions/index.json').read_text(encoding='utf8'))
    base = f'https://github.com/d-Lucx/Para/releases/download/map-data-{args.version}'
    packaging = [sys.executable, str(tools / 'package-detail-packs.py'), args.output, '--version',
                 args.version, '--base-url', base, '--previous-catalog', args.previous_catalog]
    for region in regions:
        if region['id'] in args.skip_region:
            continue
        source = args.extracted / region['id']
        pack_id = f"region-{region['id']}"
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
        packaging.extend(['--pack', pack_id, source / 'pilot', search])
    subprocess.run(packaging, check=True)


if __name__ == '__main__':
    main()
