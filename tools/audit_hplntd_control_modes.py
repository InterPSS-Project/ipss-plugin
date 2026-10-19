"""Audit cold-paired control discriminators without changing reference outputs.

Run from repository root. Prints JSON; does not establish runtime conformance.
"""
import csv
import hashlib
import json
import math
from pathlib import Path
import struct

from hplntd_control_mode_probe import MODES, variant


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_run(directory):
    manifest = json.loads((directory / 'manifest.json').read_text())
    if manifest['status'] != 'native-converged-profile':
        raise ValueError('Rejected reference quality: ' + directory.name)
    for name, expected in manifest['files'].items():
        path = Path(name)
        if path.is_absolute() or len(path.parts) != 1 or digest(directory / path) != expected:
            raise ValueError('Invalid artifact hash/path: ' + name)
    with (directory / 'native.csv').open(encoding='utf-8', newline='') as stream:
        rows = list(csv.DictReader(stream))
    expected_rows = 405 if manifest['step_s'] == .0005 else 805
    if len(rows) != expected_rows or manifest['sample_count'] != expected_rows:
        raise ValueError('Unexpected sample count')
    if manifest['channel_count'] != 89 or len(rows[0]) != 90:
        raise ValueError('Missing raw channels')
    numeric = [{key: float(value) for key, value in row.items()} for row in rows]
    if not all(math.isfinite(value) for row in numeric for value in row.values()):
        raise ValueError('Nonfinite raw sample')
    times = [row['time_s'] for row in numeric]
    if any(b < a for a, b in zip(times, times[1:])) or times[-1] < .199:
        raise ValueError('Incomplete/nonmonotonic trajectory')
    if len([1 for a, b in zip(times, times[1:]) if a == b and a >= 0]) != 2:
        raise ValueError('Lost duplicate event/pause samples')
    if any(event.get('action') for event in manifest['events']):
        raise ValueError('Unexpected fault in selector-only discriminator')
    if [(e['time_s'], e['offset']) for e in manifest['events']] != [(.05, 3), (.05, 1)]:
        raise ValueError('Changed reference events')
    return manifest, numeric


def audit(root):
    target = root / 'target'
    profiles = []
    identity = None
    for step_name, step in [('coarse', .0005), ('fine', .00025)]:
        closed = (target / ('hplntd-control-reference-sealed-closed-' + step_name + '-d') / 'dynamic.dyr').read_bytes()
        for mode in MODES:
            runs = [target / ('hplntd-control-reference-sealed-' + mode + '-' + step_name + '-' + repeat) for repeat in ('d', 'e')]
            first, rows = read_run(runs[0])
            second, repeated = read_run(runs[1])
            if first['step_s'] != step or first['profile'] != mode or second['profile'] != mode:
                raise ValueError('Changed profile identity')
            for run, manifest in zip(runs, (first, second)):
                current = tuple(manifest[k] for k in ('source_document_sha256', 'library_sha256', 'python_sha256', 'wrapper_sha256'))
                if identity is None:
                    identity = current
                if current != identity:
                    raise ValueError('Mixed reference runtime/source versions')
                converted = [struct.unpack('f', struct.pack('f', value))[0] for value in manifest['declared_constants']]
                if len(converted) != 61 or converted != manifest['actual_constants']:
                    raise ValueError('Reference tool changed entered plant constants')
                if (run / 'dynamic.dyr').read_bytes() != variant(closed, mode):
                    raise ValueError('Unexpected input mutation')
                for archived, source in [('probe.py', 'hplntd_control_mode_probe.py'), ('hplntd_fault_probe.py', 'hplntd_fault_probe.py')]:
                    if digest(run / archived) != digest(root / 'tools' / source):
                        raise ValueError('Probe source changed since generation')
            if first['files']['native.csv'] != second['files']['native.csv'] or rows != repeated:
                raise ValueError('Cold repeat changed raw output')
            if step_name == 'coarse':
                exploratory = target / ('hplntd-control-reference-probe-' + mode + '-coarse-a') / 'native.csv'
                if digest(exploratory) != first['files']['native.csv']:
                    raise ValueError('Hardened generator changed initial exploratory output')
            disabled = []
            if mode in ('p-disabled', 'both-disabled'):
                disabled += [4, 5, 6, 9, 10]
                axes = [(39, 41, 43)]
            else:
                axes = []
            if mode in ('q-disabled', 'both-disabled'):
                disabled += [0, 1, 2, 3, 8, 11, 12]
                axes += [(40, 42, 44)]
            for row in rows:
                for index in disabled:
                    if row['PLANT_STATE' + str(index)] != 0:
                        raise ValueError('Disabled path advanced state ' + str(index))
                for command, initial, increment in axes:
                    if row['PLANT_VAR' + str(command)] != rows[0]['PLANT_VAR' + str(initial)] or row['PLANT_VAR' + str(increment)] != 0:
                        raise ValueError('Disabled path lost initial command or published an increment')
                for index, modes in [(2, ('ki-zero', 'both-zero')), (5, ('kig-zero', 'both-zero'))]:
                    if mode in modes and row['PLANT_STATE' + str(index)] != 0:
                        raise ValueError('Zero-gain integral storage is not zero')
            profiles.append(dict(mode=mode, step_s=step, samples=len(rows), channels=89,
                                 csv_sha256=first['files']['native.csv'],
                                 initial_states=[rows[0]['PLANT_STATE' + str(i)] for i in range(13)],
                                 state_spans=[max(row['PLANT_STATE' + str(i)] for row in rows) - min(row['PLANT_STATE' + str(i)] for row in rows) for i in range(13)],
                                 initial_references={str(i): rows[0]['PLANT_VAR' + str(i)] for i in (0, 1, 3, 9)},
                                 raw_runs=[str(run.relative_to(root)).replace('\\', '/') for run in runs]))
    for step_name, expected in [('coarse', '4c1413ee008f5723c9dfc367ef1b5c911694523f8dc3a3ae008d4a5b832d0093'),
                                ('fine', '15426437f89a366d1ece11a9f124b8ad770f0395918275377d01e4e82bb4abaa')]:
        directory = target / ('hplntd-fault-reference-selector-refactor-' + step_name)
        manifest = json.loads((directory / 'manifest.json').read_text())
        if manifest['status'] != 'native-converged-profile' or digest(directory / 'native.csv') != expected:
            raise ValueError('Common-runner refactor changed frozen fault output')
        for name, expected_hash in manifest['files'].items():
            if digest(directory / name) != expected_hash:
                raise ValueError('Changed frozen-fault run file')
    return dict(status='repeatable-native-discriminators-not-runtime-acceptance',
                profile_count=14, cold_runs=28, unchanged_fault_profiles=2, profiles=profiles)


if __name__ == '__main__':
    print(json.dumps(audit(Path(__file__).resolve().parents[1]), indent=2))
