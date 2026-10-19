"""Audit direct-reference cold pairs, preserving native voltage-limit discrepancies."""
import base64
import gzip
import json
from pathlib import Path
import struct
from audit_hplntd_control_modes import digest, read_run
from hplntd_reference_limit_probe import MODES, variant


def audit(root):
    resource = root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native'
    baseline = base64.b64decode((resource / 'plant-low-window-coarse.dyr.b64').read_bytes())
    profiles = []
    identity = None
    for mode, (_, events) in MODES.items():
        for label, step in [('coarse', .0005), ('fine', .00025)]:
            runs = [root / 'target' / ('hplntd-control-reference-reference-limit-' + mode + '-' + label + '-' + repeat) for repeat in ('a', 'b')]
            offsets = tuple(offset for offset, _ in events)
            first, rows = read_run(runs[0], offsets)
            second, repeated = read_run(runs[1], offsets)
            if rows != repeated or first['files']['native.csv'] != second['files']['native.csv']:
                raise ValueError('Changed raw cold reference: ' + mode)
            for run, manifest in zip(runs, (first, second)):
                if manifest['profile'] != mode or manifest['step_s'] != step:
                    raise ValueError('Changed profile identity')
                current = tuple(manifest[key] for key in ('source_document_sha256', 'library_sha256', 'wrapper_sha256', 'python_sha256'))
                if identity is None:
                    identity = current
                if identity != current:
                    raise ValueError('Mixed native reference versions')
                converted = [struct.unpack('f', struct.pack('f', v))[0] for v in manifest['declared_constants']]
                if len(converted) != 61 or converted != manifest['actual_constants']:
                    raise ValueError('Native tool changed entered constants')
                if (run / 'dynamic.dyr').read_bytes() != variant(baseline, mode):
                    raise ValueError('Changed exact input tokens')
                for archived, source in [('probe.py', 'hplntd_reference_limit_probe.py'), ('hplntd_fault_probe.py', 'hplntd_fault_probe.py')]:
                    if digest(run / archived) != digest(root / 'tools' / source):
                        raise ValueError('Changed generating probe')
                for recorded, (offset, delta) in zip(manifest['events'], events):
                    initial = rows[0]['PLANT_VAR' + str(offset)]
                    if abs(recorded['value'] - (initial + delta)) > 1e-10:
                        raise ValueError('Changed declared external reference step')
            name = 'reference-limit-' + mode + '-' + label
            if gzip.decompress(base64.b64decode((resource / (name + '.csv.b64')).read_bytes())) != (runs[0] / 'native.csv').read_bytes():
                raise ValueError('Embedded raw output differs')
            if base64.b64decode((resource / (name + '.dyr.b64')).read_bytes()) != (runs[0] / 'dynamic.dyr').read_bytes():
                raise ValueError('Embedded input differs')
            diagnostic = None
            if mode.startswith('v-'):
                c = first['declared_constants']
                def error(value):
                    shifted = value - c[12] if value > c[12] else value - c[11] if value < c[11] else 0.
                    return max(c[10], min(c[9], shifted))
                raw = []
                published = []
                for row in rows:
                    reference, measured = row['PLANT_VAR0'], row['PLANT_STATE0']
                    raw.append((abs(error(reference + row['PLANT_VAR10'] - measured) - row['PLANT_VAR7']), row['time_s']))
                    limited = max(c[28], min(c[27], reference))
                    published.append((abs(error(limited + row['PLANT_VAR10'] - measured) - row['PLANT_VAR7']), row['time_s']))
                if max(raw)[0] >= 1e-10 or max(published)[0] <= .005:
                    raise ValueError('Voltage discriminator no longer distinguishes the contracts')
                diagnostic = dict(raw_equation_max_error=max(raw)[0], raw_equation_error_time_s=max(raw)[1],
                                  published_equation_max_difference=max(published)[0], published_difference_time_s=max(published)[1])
            profiles.append(dict(profile=name, samples=len(rows), step_s=step,
                                 role='q-pf-trajectory-acceptance' if mode in ('q-upper', 'pf-lower') else 'native-voltage-bypass-diagnostic',
                                 csv_sha256=first['files']['native.csv'], input_sha256=first['files']['dynamic.dyr'],
                                 voltage_discriminator=diagnostic,
                                 runs=[run.relative_to(root).as_posix() for run in runs]))
    defaults = []
    for label, fault_expected in [('coarse', '4c1413ee008f5723c9dfc367ef1b5c911694523f8dc3a3ae008d4a5b832d0093'),
                                  ('fine', '15426437f89a366d1ece11a9f124b8ad770f0395918275377d01e4e82bb4abaa')]:
        fault = root / 'target' / ('hplntd-fault-reference-limit-extension-' + label)
        manifest = json.loads((fault / 'manifest.json').read_text())
        if manifest['status'] != 'native-converged-profile' or digest(fault / 'native.csv') != fault_expected:
            raise ValueError('Default fault generator behavior changed')
        for name, expected in manifest['files'].items():
            if Path(name).name != name or digest(fault / name) != expected:
                raise ValueError('Changed default fault artifact')
        control = root / 'target' / ('hplntd-control-reference-limit-extension-closed-' + label)
        read_run(control)
        previous = root / 'target' / ('hplntd-control-reference-sealed-closed-' + label + '-d')
        if digest(control / 'native.csv') != digest(previous / 'native.csv'):
            raise ValueError('Default controller generator behavior changed')
        defaults.extend([fault.relative_to(root).as_posix(), control.relative_to(root).as_posix()])
    return dict(status='audited-reference-limit-evidence-not-voltage-parity', profile_count=16,
                cold_runs=32, trajectory_profiles=4, voltage_diagnostic_profiles=12,
                profiles=profiles, unchanged_default_runs=defaults)


if __name__ == '__main__':
    print(json.dumps(audit(Path(__file__).resolve().parents[1]), indent=2))
