"""Validate paired cold captures and publish lossless, compressed small-network references."""
import csv
import gzip
import hashlib
import io
import json
import math
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_immutable(path, data):
    if path.exists() and path.read_bytes() != data:
        raise ValueError(f'Refusing to replace retained reference: {path.name}; qualify a new archive instead')
    if not path.exists():
        path.write_bytes(data)


def main():
    root = Path.cwd()
    destination = root / 'ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-30-hsp-usage/references'
    destination.mkdir(parents=True, exist_ok=True)
    profiles = {}
    groups = {
        'hsp-pss-deterministic-20261030': ['pss2b', 'pss2b-remote'],
        'hsp-model-references-qualified-20261030': [
            'hyg3u1-0', 'hyg3u1-1', 'wsieg1', 'wsieg1-curve',
            'tiocr1-0', 'tiocr1-1', 'cmldblu1', 'cmldblu1-electronic'],
        'hsp-hydro-extended-20261030': [
            'hyg3u1-deadband', 'hyg3u1-deadband-zero', 'hyg3u1-asymmetric', 'hyg3u1-partial'],
        'hsp-current-load-relay-20261030': ['tiocr1-current'],
        'hsp-wsieg-dual-20261030': ['wsieg1-dual', 'wsieg1-unused-lp'],
    }
    provenance = set()
    for group, names in groups.items():
        for name in names:
            for part in ([0, 1] if name.startswith('cmldblu1') else [None]):
                suffix = '' if part is None else f'-part{part}'
                a = root / 'target' / group / f'{name}-fault-h0.0005-a{suffix}'
                b = root / 'target' / group / f'{name}-fault-h0.0005-b{suffix}'
                raw = (a / 'native.csv').read_bytes()
                if raw != (b / 'native.csv').read_bytes():
                    raise ValueError(f'Cold reference mismatch: {name}{suffix}')
                for folder in [a, b]:
                    manifest = json.loads((folder / 'manifest.json').read_text())
                    assert manifest['status'] == 'complete'
                    for file, expected in manifest['files'].items():
                        assert digest((folder / file).read_bytes()) == expected, (folder.name, file)
                    provenance.add((manifest['library_sha256'], manifest['source_document_sha256']))
                rows = list(csv.reader(io.StringIO(raw.decode('utf8'))))
                assert len(rows) == manifest['samples'] + 1
                assert len(rows[0]) == manifest['channels'] + 1
                assert all(len(row) == len(rows[0]) for row in rows)
                assert all(math.isfinite(float(v)) for row in rows[1:] for v in row)
                times = [float(row[0]) for row in rows[1:]]
                assert all(a <= b for a, b in zip(times, times[1:]))
                assert sum(a == b for a, b in zip(times, times[1:])) >= 2
                key = name + suffix
                compressed = gzip.compress(raw, mtime=0)
                write_immutable(destination / (key + '.csv.gz'), compressed)
                profiles[key] = dict(file=key+'.csv.gz', raw_sha256=digest(raw),
                    archive_sha256=digest(compressed), rows=len(rows)-1, channels=rows[0][1:],
                    step_s=.0005, fault_x_pu=manifest['fault_impedance_pu'][1],
                    network_sha256=manifest['files']['network.raw'],
                    dynamic_sha256=manifest['files']['dynamic.dyr'],
                    cold_pair_equal=True, load_conversion_percent=manifest['load_conversion_percent'], evidence_group=group)
    assert len(provenance) == 1
    for name in ['cmldblu1', 'cmldblu1-electronic']:
        channels = set().union(*(set(profiles[name+f'-part{i}']['channels']) for i in [0, 1]))
        assert all(f'STATE{i}' in channels for i in range(27))
        assert all(f'VAR{i}' in channels for i in range(146))
    manifest = dict(schema_version=1, status='paired-cold-reference-captures',
        standard_load_conversion_percent=[0,100,0,100],
        startup_policy='Retain both negative-time startup samples; compare trajectories from time zero.',
        event_policy='Retain every row and every duplicate timestamp, including relay breaker output.',
        source_document_sha256=next(iter(provenance))[1], profiles=profiles,
        private_provenance='testData/private/verification-artifacts/2026-10-30-hsp-usage')
    path = destination / 'manifest.json'
    serialized = json.dumps(manifest, indent=2)+'\n'
    # Older text-mode manifests use platform newlines. Preserve their exact bytes;
    # allow only the same text when rerunning packaging on another platform.
    if path.exists():
        if path.read_text() != serialized:
            raise ValueError('Refusing to replace retained manifest; qualify a new archive instead')
    else:
        write_immutable(path, serialized.encode('utf8'))
    print(json.dumps(dict(profiles=len(profiles), files_bytes=sum(p.stat().st_size for p in destination.iterdir()))))


if __name__ == '__main__':
    main()
