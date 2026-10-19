"""Verify paired cold allocation evidence without inferring trajectory acceptance."""
import hashlib
import json
import math
from pathlib import Path
import sys


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(folder):
    folder = Path(folder)
    m = json.loads((folder / 'manifest.json').read_text())
    assert m['evidence'] == 'allocation-only'
    assert m['load_code'] == 0
    assert set(m['layout']) == {'ICON', 'CON', 'STATE', 'VAR', 'NICON', 'NCON', 'NSTATE', 'NVAR'}
    assert set(m['files']) == {'probe.py', 'network.raw', 'dynamic.dyr', 'native.log'}
    for name, digest in m['files'].items():
        assert Path(name).name == name
        assert sha(folder / name) == digest, name
    log = (folder / 'native.log').read_text(errors='replace')
    if m['model'] == 'PSSSB':
        assert not m['attached'] and 'Undefined ModelPSSSB' in log
        assert all(value == [4, None] for value in m['layout'].values())
        assert m['constants'] == []
    else:
        assert m['model'] == 'PSS2A' and m['attached']
        assert 'Undefined Model' not in log
        assert all(len(value) == 2 and value[0] == 0 and value[1] > 0
                   for value in m['layout'].values())
        assert [m['layout']['N' + k] for k in ('ICON', 'CON', 'STATE', 'VAR')] == [
            [0, 6], [0, 17], [0, 16], [0, 4]]
        assert len(m['constants']) == 17
        assert all(isinstance(value, (int, float)) and math.isfinite(value) for value in m['constants'])
    return m


def audit(folders):
    assert len(folders) == 4
    pairs = {}
    for folder in folders:
        m = verify(folder)
        pairs.setdefault(m['model'], []).append(m)
    assert set(pairs) == {'PSSSB', 'PSS2A'}
    for pair in pairs.values():
        assert len(pair) == 2 and pair[0]['repeat'] != pair[1]['repeat']
        a, b = pair
        for key in ('layout', 'constants', 'attached', 'engine_module', 'library_sha256',
                    'source_document_sha256', 'python_sha256', 'wrapper_sha256'):
            assert a[key] == b[key], key
        for name in ('probe.py', 'network.raw', 'dynamic.dyr'):
            assert a['files'][name] == b['files'][name], name
    a, b = (pairs[key][0] for key in ('PSSSB', 'PSS2A'))
    for key in ('library_sha256', 'source_document_sha256', 'python_sha256', 'wrapper_sha256'):
        assert a[key] == b[key], key
    assert a['files']['network.raw'] == b['files']['network.raw']
    return dict(status='allocation-discriminator-confirmed', cold_runs=4,
                psssb_attached=False, pss2a_attached=True,
                trajectory_acceptance=False,
                source_document_sha256=a['source_document_sha256'])


if __name__ == '__main__':
    print(json.dumps(audit(sys.argv[1:]), indent=2))
