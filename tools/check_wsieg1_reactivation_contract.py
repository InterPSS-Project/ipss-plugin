"""Check owned lifecycle channels and retain unowned diagnostic cold-pair differences."""
import csv
import hashlib
import json
from pathlib import Path
from check_wsieg1_status_reenable import rates


def audit(root):
    results = []
    for first in sorted(root.glob('*-a')):
        second = first.with_name(first.name[:-1]+'b')
        records = []
        for folder in (first, second):
            manifest = json.loads((folder/'manifest.json').read_text())
            assert manifest['status'] == 'diagnostic-only'
            for name, digest in manifest['files'].items():
                assert hashlib.sha256((folder/name).read_bytes()).hexdigest() == digest
            request = manifest['status_reenable_diagnostic']
            assert request['return_code'] == 0
            assert request['before']['machine_status']['value'] == 0
            assert request['after']['machine_status']['value'] == 1
            assert request['before']['governor_states'] == request['after']['governor_states']
            records.append([{k: float(v) for k, v in row.items()} for row in csv.DictReader((folder/'native.csv').open())])
        a, b = records
        assert len(a) == len(b)
        differences = {k: max(abs(x[k]-y[k]) for x, y in zip(a, b)) for k in a[0]}
        assert all(value == 0 for key, value in differences.items() if key != 'GREF_LP')
        primary = manifest['settings']['event'] == 'primary-trip'
        suffix = '' if primary else '_LP'
        for row in a:
            if row['time_s'] > .051:
                assert row['EFD'+suffix] == 0
                assert abs(row['GREF']-(0 if primary else manifest['initial_reference']+.1)) < 1e-7
        result = dict(tag=first.name[:-2], samples=len(a), state_preserved_at_api=True,
                      owned_cold_channels_identical=True,
                      unowned_cold_differences={k: v for k, v in differences.items() if v})
        if primary:
            trip = next(i for i in range(1, len(a)) if a[i]['time_s'] > .04 and a[i]['time_s'] == a[i-1]['time_s'])
            off = [row for row in a[trip:] if row['PMECH'] == 0 and row['time_s'] < .101]
            h = manifest['settings']['step']; c = manifest['initialized_constants']
            last = rates(a[trip-1], c, .1); older = rates(a[trip-2], c, .1)
            error = 0
            for i in range(1, len(off)):
                for k in range(6):
                    expected = h*(1.5*last[k]-.5*older[k]) if i == 1 else h*last[k]
                    error = max(error, abs(off[i][f'STATE{k}']-off[i-1][f'STATE{k}']-expected))
                for key in ('PMECH_LP', 'VAR3', 'VAR7'):
                    assert off[i][key] == off[0][key]
            assert error < 3e-8  # Independent stored-coordinate observation resolution.
            result['retained_rate_increment_max_error'] = error
        results.append(result)
    assert len(results) == 4
    return dict(pairs=results, unowned_secondary_reference='GREF_LP has no governor owner; raw values retained, never used as the shared turbine reference.')


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(); parser.add_argument('capture_root', type=Path)
    print(json.dumps(audit(parser.parse_args().capture_root), indent=2))
