"""Validate and retain synthetic composite-load initialization discriminators."""
import argparse
import csv
import gzip
import hashlib
import io
import json
from pathlib import Path
import shutil
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--extend', type=Path,
                        help='Retain an immutable parent manifest/archive and add the feeder-floor discriminator')
    args = parser.parse_args()
    out = args.output
    references = out / 'references'
    references.mkdir(parents=True, exist_ok=False)
    names = ['unit-taps', 'no-line-comp', 'locked-ltc', 'no-substation-shunt',
             'far-end-comp', 'no-bss-far-end', 'upper-tap', 'lower-tap',
             'loading-065', 'loading-085', 'down-tap-limit', 'minimum-voltage',
             'near-minimum-voltage', 'narrow-band']
    profiles = {}
    parent = None
    if args.extend:
        parent_bytes = (args.extend / 'references/manifest.json').read_bytes()
        prior = json.loads(parent_bytes)
        assert sha((args.extend / 'captures.zip').read_bytes()) == prior['capture_archive_sha256']
        profiles.update(prior['profiles'])
        for profile in profiles.values():
            for part in profile['parts']:
                source = args.extend / 'references' / part['file']
                assert sha(source.read_bytes()) == part['archive_sha256']
                shutil.copy2(source, references / part['file'])
        parent = dict(directory=args.extend.name, manifest_sha256=sha(parent_bytes),
                      capture_archive_sha256=prior['capture_archive_sha256'])
        roots = {'feeder-floor': Path('target/cmldblu1-init-feeder-floor-20261031')}
    else:
        roots = {'nominal': Path('target/cmldblu1-shared-reference-20261031-cleared')}
        roots.update({name: Path('target/cmldblu1-init-' + name + '-20261031') for name in names})
    retained = []
    for name, root in roots.items():
        combined = None
        parts = []
        metadata = None
        for part in range(2):
            runs = [root / ('cmldblu1-fault-h0.0005-' + repeat + '-part' + str(part))
                    for repeat in ['a', 'b']]
            manifests = []
            for run in runs:
                manifest = json.loads((run / 'manifest.json').read_text())
                assert manifest['status'] == 'complete', run
                for filename, expected in manifest['files'].items():
                    assert sha((run / filename).read_bytes()) == expected, (run, filename)
                assert manifest['settings']['clear_diagnostic_storage']
                assert manifest['layout']['NCON'] == 132
                manifests.append(manifest)
                retained.append(run)
            raw = (runs[0] / 'native.csv').read_bytes()
            assert raw == (runs[1] / 'native.csv').read_bytes(), ('Cold pair differs', name, part)
            assert manifests[0]['entered_constants'] == manifests[1]['entered_constants']
            assert manifests[0]['initialized_constants'] == manifests[1]['initialized_constants']
            if metadata is None:
                metadata = manifests[0]
            else:
                assert metadata['entered_constants'] == manifests[0]['entered_constants']
                assert metadata['initialized_constants'] == manifests[0]['initialized_constants']
            rows = [{key: float(value) for key,value in row.items()}
                    for row in csv.DictReader(io.StringIO(raw.decode()))]
            assert len(rows) == 1005
            assert rows[0]['time_s'] < 0
            assert sum(rows[i]['time_s'] == rows[i-1]['time_s'] for i in range(1,len(rows))) == 2
            if combined is None:
                combined = rows
            else:
                assert len(combined) == len(rows)
                for left,right in zip(combined,rows):
                    for key,value in right.items():
                        if key in left:
                            assert left[key] == value, ('Common channel differs', name, key)
                        left[key] = value
            archive = gzip.compress(raw, mtime=0)
            filename = name + '-part' + str(part) + '.csv.gz'
            (references / filename).write_bytes(archive)
            parts.append(dict(file=filename, archive_sha256=sha(archive), raw_sha256=sha(raw),
                              rows=len(rows), cold_pair_equal=True))
        initial = next(row for row in combined if row['time_s'] == 0)
        assert len(initial) == 27 + 146 + 13
        settings = metadata['settings']
        overrides = settings.get('constant', [])
        profiles[name] = dict(overrides=overrides, initial=initial, parts=parts,
                             entered_constants=metadata['entered_constants'],
                             initialized_constants=metadata['initialized_constants'],
                             source_document_sha256=metadata['source_document_sha256'],
                             library_sha256=metadata['library_sha256'], common_channels_equal=True)
    # Keep original executable outputs, inputs, scripts and manifests, not only
    # extracted initial numbers. All channel parts and both repeats are retained.
    archive_path = out / 'captures.zip'
    with zipfile.ZipFile(archive_path, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for run in retained:
            for path in sorted(run.iterdir()):
                if path.is_file():
                    archive.write(path, str(Path(run.parent.name) / run.name / path.name))
        archive.write(__file__, 'publish_cmldblu1_initialization_references.py')
    manifest = dict(schema=1, status='cold-paired-synthetic-initialization-discriminators',
                    capture_archive_sha256=sha(archive_path.read_bytes()), profiles=profiles)
    if parent:
        manifest['parent_evidence'] = parent
    raw = (json.dumps(manifest, indent=2, sort_keys=True)+'\n').encode()
    (references / 'manifest.json').write_bytes(raw)
    print(json.dumps(dict(profiles=list(profiles), manifest_sha256=sha(raw),
                          capture_archive_sha256=manifest['capture_archive_sha256'])))


if __name__ == '__main__':
    main()
