"""Audit selector reference pairs and embedded raw histories, without fitting."""
import base64
import gzip
import hashlib
import json
from pathlib import Path
import struct

from audit_hplntd_control_modes import digest, read_run, FROZEN_RUNNER_SHA256
from hplntd_selector_probe import MODES, variant


def compare_repeats(mode, first, second):
    # Only disabled reactive PF profiles have observed uninitialized unused
    # Vref/Qref/Q-V-deadband/PFref VAR storage. Never exclude a physical state,
    # command, active reference or event row, and never rewrite either raw CSV.
    unused = ['PLANT_VAR' + str(i) for i in (0, 1, 7, 9)] if mode in ('q-disabled-pf', 'both-disabled-pf') else []
    selected = [{k: v for k, v in row.items() if k not in unused} for row in first]
    repeated = [{k: v for k, v in row.items() if k not in unused} for row in second]
    if selected != repeated:
        raise ValueError('Cold comparison changed an observed channel: ' + mode)
    canonical = json.dumps(selected, separators=(',', ':'), allow_nan=False).encode()
    return unused, hashlib.sha256(canonical).hexdigest()


def audit(root):
    baseline = base64.b64decode((root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64').read_bytes())
    results = []
    identity = None
    for mode in ('p-disabled', 'q-disabled', 'both-disabled', *MODES):
        for label, step in [('coarse', .0005), ('fine', .00025)]:
            if mode in MODES:
                tags = ['selectors-' + mode + (('-' + repeat) if label == 'coarse' else '-fine-' + repeat) for repeat in ('a', 'b')]
            else:
                tags = ['sealed-' + mode + '-' + label + '-' + repeat for repeat in ('d', 'e')]
            runs = [root / 'target' / ('hplntd-control-reference-' + tag) for tag in tags]
            first, rows = read_run(runs[0])
            second, repeated = read_run(runs[1])
            if first['profile'] != mode or second['profile'] != mode or first['step_s'] != step or second['step_s'] != step:
                raise ValueError('Changed selector identity')
            unused, canonical = compare_repeats(mode, rows, repeated)
            identical = first['files']['native.csv'] == second['files']['native.csv']
            for run, manifest in zip(runs, (first, second)):
                current = tuple(manifest[key] for key in ('source_document_sha256', 'library_sha256', 'wrapper_sha256', 'python_sha256'))
                if identity is None:
                    identity = current
                if identity != current:
                    raise ValueError('Mixed reference versions')
                converted = [struct.unpack('f', struct.pack('f', value))[0] for value in manifest['declared_constants']]
                if len(converted) != 61 or converted != manifest['actual_constants']:
                    raise ValueError('Changed entered plant constants')
                if mode in MODES and (run / 'dynamic.dyr').read_bytes() != variant(baseline, mode):
                    raise ValueError('Changed exact selector tokens')
                probe = 'hplntd_selector_probe.py' if mode in MODES else 'hplntd_control_mode_probe.py'
                if digest(run / 'probe.py') != digest(root / 'tools' / probe) or digest(run / 'hplntd_fault_probe.py') != FROZEN_RUNNER_SHA256:
                    raise ValueError('Changed probe source')
            name = 'selector-' + mode + '-' + label
            resource = root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native'
            if gzip.decompress(base64.b64decode((resource / (name + '.csv.b64')).read_bytes())) != (runs[0] / 'native.csv').read_bytes():
                raise ValueError('Embedded output differs from raw reference')
            if base64.b64decode((resource / (name + '.dyr.b64')).read_bytes()) != (runs[0] / 'dynamic.dyr').read_bytes():
                raise ValueError('Embedded input differs from raw reference')
            active_off = mode.startswith('p-disabled') or mode.startswith('both-disabled')
            reactive_off = mode.startswith('q-disabled') or mode.startswith('both-disabled')
            disabled = ([5, 6, 9, 10] if active_off else []) + ([0, 1, 2, 3, 8, 11, 12] if reactive_off else [])
            if active_off and (reactive_off or not mode.endswith('-pf')):
                disabled.append(4)
            for row in rows:
                if any(row['PLANT_STATE' + str(index)] != 0 for index in disabled):
                    raise ValueError('Disabled storage advanced')
                for command, initial, increment in ([(39, 41, 43)] if active_off else []) + ([(40, 42, 44)] if reactive_off else []):
                    if row['PLANT_VAR' + str(command)] != rows[0]['PLANT_VAR' + str(initial)] or row['PLANT_VAR' + str(increment)] != 0:
                        raise ValueError('Inactive command was not held')
            if mode == 'p-disabled-pf' and rows[0]['PLANT_STATE4'] == 0:
                raise ValueError('PF control lost shared active measurement')
            results.append(dict(profile=name, step_s=step, samples=len(rows), csv_sha256=first['files']['native.csv'],
                                input_sha256=first['files']['dynamic.dyr'],
                                byte_identical=identical, unused_disabled_variables=unused,
                                compared_history_sha256=canonical, repeated_csv_sha256=second['files']['native.csv'],
                                runs=[run.relative_to(root).as_posix() for run in runs]))
    return dict(status='audited-selector-reference-pairs', profiles=results, profile_count=len(results), cold_runs=2 * len(results))


if __name__ == '__main__':
    print(json.dumps(audit(Path(__file__).resolve().parents[1]), indent=2))
