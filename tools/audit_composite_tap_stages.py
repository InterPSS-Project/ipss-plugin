"""Audit first-action channel timing in the synthetic three-bus load fixture.

This diagnoses raw sample stages; it does not shift, filter, or qualify traces.
The source machine is connected only through branch 1-2, with no bus shunt.
Machine and network MVA bases must match. Input files remain unchanged.
"""
import argparse
import cmath
import csv
import hashlib
import json
import math
from pathlib import Path


def audit(capture):
    capture = Path(capture)
    manifest = json.loads((capture / 'manifest.json').read_text())
    if manifest['status'] != 'complete':
        raise ValueError('A complete independent capture is required')
    raw = (capture / 'network.raw').read_text().splitlines()
    # Reject a different topology rather than silently reconstructing wrong power.
    expected = Path('ipss.test.plugin.core/testData/adpter/psse/v33/HspModels/load-network.raw')
    if raw != expected.read_text().splitlines():
        raise ValueError('This audit requires the exact public three-bus fixture')
    rows = []
    with (capture / 'native.csv').open(newline='') as stream:
        for row in csv.DictReader(stream):
            values = {key: float(value) for key, value in row.items()}
            if not all(math.isfinite(value) for value in values.values()):
                raise ValueError('Nonfinite reference sample')
            rows.append(values)
    if any(b['time_s'] < a['time_s'] for a, b in zip(rows, rows[1:])):
        raise ValueError('Reference clock reverses')
    first = next(i for i in range(1, len(rows))
                 if rows[i]['time_s'] >= 0 and abs(rows[i]['VAR12'] - rows[i-1]['VAR12']) > 1e-6)
    before = rows[first - 1]
    step = manifest['settings']['step']
    # Thresholds exceed channel serialization noise and are reported explicitly.
    thresholds = {'VAR30': 1e-5, 'V1': 1e-6, 'V2': 1e-6,
                  'P_PU': 1e-5, 'VAR25': 1e-5, 'SPEED': 1e-9}
    response = {}
    for key, threshold in thresholds.items():
        index = next((i for i in range(first, len(rows)) if abs(rows[i][key]-before[key]) > threshold), None)
        if index is None:
            response[key] = dict(threshold=threshold, row_offset=None,
                                 time_offset_s=None, interval_offset=None)
            continue
        response[key] = dict(threshold=threshold, row_offset=index-first,
                             time_offset_s=rows[index]['time_s']-rows[first]['time_s'],
                             interval_offset=(rows[index]['time_s']-rows[first]['time_s'])/step)
    def electrical_power(row):
        v1 = cmath.rect(row['V1'], math.radians(row['A1']))
        v2 = cmath.rect(row['V2'], math.radians(row['A2']))
        return (v1 * ((v1-v2)/complex(.005, .1)).conjugate()).real
    window = [{**{key: row[key] for key in ['time_s', 'VAR12', 'VAR30', 'VAR25', 'V1', 'V2', 'P_PU', 'SPEED']},
               'power_from_bus_phasors_pu': electrical_power(row)}
              for row in rows[max(0, first-2):first+5]]
    return dict(step_s=step, samples=len(rows), duplicate_times=sum(a['time_s']==b['time_s'] for a,b in zip(rows,rows[1:])),
                first_action_time_s=rows[first]['time_s'], response=response, first_action_window=window,
                maximum_machine_power_vs_bus_phasors_error_pu=max(abs(electrical_power(row)-row['P_PU']) for row in rows),
                source_sha256={name:hashlib.sha256((capture/name).read_bytes()).hexdigest()
                               for name in ['native.csv', 'network.raw', 'dynamic.dyr', 'manifest.json']})


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    args = parser.parse_args()
    print(json.dumps(audit(args.capture), indent=2))
