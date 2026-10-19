"""Audit complete, cold-repeated bus-measurement SMIB reference runs."""
import csv
import hashlib
import json
import math
from pathlib import Path


def check(root):
    records = []
    for signal in ('frequency', 'voltage'):
        for location in ('local', 'remote'):
            for event in ('flat', 'fault'):
                for scale in ('coarse', 'fine'):
                    name = '-'.join((signal, location, event, scale))
                    runs = [root / 'target' / ('ieeest-input-measurement-matrix1-' + name + '-' + repeat)
                            for repeat in ('a', 'b')]
                    manifests = [json.loads((run / 'manifest.json').read_text()) for run in runs]
                    assert all(m['status'] == 'native-converged-profile' for m in manifests), name
                    for run, manifest in zip(runs, manifests):
                        for filename, expected in manifest['files'].items():
                            assert hashlib.sha256((run / filename).read_bytes()).hexdigest() == expected, (name, filename)
                        assert manifest['actual_constants'] == manifest['final_constants'], name
                        stab = manifest['components']['STAB']
                        assert stab['actual_icons'] == [2 if signal == 'frequency' else 5, 0 if location == 'local' else 2], name
                        assert stab['layout']['NCON'] == 17 and stab['layout']['NICON'] == 2, name
                        assert stab['layout']['NSTATE'] == 7 and stab['layout']['NVAR'] == 2, name
                    raw = [(run / 'native.csv').read_bytes() for run in runs]
                    assert raw[0] == raw[1], name
                    for filename in ('network.raw', 'dynamic.dyr'):
                        assert (runs[0]/filename).read_bytes() == (runs[1]/filename).read_bytes(), (name, filename)
                    rows = list(csv.DictReader(raw[0].decode().splitlines()))
                    assert len(rows) == manifests[0]['sample_count'], name
                    assert len(rows[0]) == 37 and float(rows[0]['time_s']) < 0, name
                    state_keys = ('EP_Q', 'EP_D', 'PSIK_D', 'PSIK_Q', 'SPEED_DEV', 'ANGLE',
                                  'EXC_VMEAS', 'EXC_LL1', 'EXC_LL2', 'EXC_VA', 'EXC_FEEDBACK',
                                  'PSS_FILTER1', 'PSS_FILTER2', 'PSS_FILTER3', 'PSS_FILTER4',
                                  'PSS_LL1', 'PSS_LL2', 'PSS_LAST')
                    assert all(key in rows[0] for key in state_keys), name
                    assert all(math.isfinite(float(value)) for row in rows for value in row.values()), name
                    step = .0005 if scale == 'coarse' else .00025
                    assert manifests[0]['step_s'] == step, name
                    assert len(rows) == (1005 if scale == 'coarse' else 2005), name
                    assert sum(a['time_s'] == b['time_s'] for a, b in zip(rows, rows[1:])) == 2, name
                    records.append(dict(profile=name, samples=len(rows), csv_sha256=hashlib.sha256(raw[0]).hexdigest()))
    return records


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    print(json.dumps(dict(status='pass', profiles=check(root)), indent=2))
