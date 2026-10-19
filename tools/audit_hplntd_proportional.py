"""Audit feasible proportional reference pairs and retain rejected counterexamples."""
import base64
import gzip
import json
from pathlib import Path
import shlex
import struct
from audit_hplntd_control_modes import digest, read_run
from hplntd_proportional_probe import MODES, variant

ACCEPTED = tuple(mode for mode in MODES if not mode.startswith('reject-') and mode != 'ki-deadband')


def audit(root):
    source = root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native'
    baseline = base64.b64decode((source / 'plant-low-window-coarse.dyr.b64').read_bytes())
    profiles = []
    identity = None
    for mode in ('ki-zero', 'kig-zero', 'both-zero', *ACCEPTED):
        for label, step in [('coarse', .0005), ('fine', .00025)]:
            tags = ['proportional-' + mode + '-' + label + '-' + repeat for repeat in ('a', 'b')] if mode in ACCEPTED else ['sealed-' + mode + '-' + label + '-' + repeat for repeat in ('d', 'e')]
            runs = [root / 'target' / ('hplntd-control-reference-' + tag) for tag in tags]
            first, rows = read_run(runs[0])
            second, repeated = read_run(runs[1])
            if rows != repeated or first['files']['native.csv'] != second['files']['native.csv']:
                raise ValueError('Changed cold history: ' + mode)
            for run, manifest in zip(runs, (first, second)):
                if manifest['profile'] != mode or manifest['step_s'] != step:
                    raise ValueError('Changed proportional profile identity')
                current = tuple(manifest[key] for key in ('source_document_sha256', 'library_sha256', 'wrapper_sha256', 'python_sha256'))
                if identity is None:
                    identity = current
                if current != identity:
                    raise ValueError('Mixed reference versions')
                constants = [struct.unpack('f', struct.pack('f', v))[0] for v in manifest['declared_constants']]
                if len(constants) != 61 or constants != manifest['actual_constants']:
                    raise ValueError('Reference changed entered constants')
                if mode in ACCEPTED and (run / 'dynamic.dyr').read_bytes() != variant(baseline, mode):
                    raise ValueError('Unexpected input mutation')
                probe = 'hplntd_proportional_probe.py' if mode in ACCEPTED else 'hplntd_control_mode_probe.py'
                if digest(run / 'probe.py') != digest(root / 'tools' / probe) or digest(run / 'hplntd_fault_probe.py') != digest(root / 'tools/hplntd_fault_probe.py'):
                    raise ValueError('Changed reference probe')
            name = 'proportional-' + mode + '-' + label
            if gzip.decompress(base64.b64decode((source / (name + '.csv.b64')).read_bytes())) != (runs[0] / 'native.csv').read_bytes():
                raise ValueError('Embedded raw history changed')
            if base64.b64decode((source / (name + '.dyr.b64')).read_bytes()) != (runs[0] / 'dynamic.dyr').read_bytes():
                raise ValueError('Embedded exact input changed')
            record, = [line for line in (runs[0] / 'dynamic.dyr').read_text().splitlines() if "'USRBUS'" in line]
            tokens = shlex.split(record.rstrip().removesuffix('/'))
            c = first['declared_constants']
            measured = rows[0]['PLANT_VAR4'] / rows[0]['PLANT_VAR6']
            feedback = measured if int(tokens[16]) else 0
            feedforward = int(tokens[17])
            expected = feedback if c[16] > 0 else (rows[0]['PLANT_VAR39'] + c[15] * feedback) / (c[15] + feedforward)
            if abs(expected - rows[0]['PLANT_VAR3']) > 2e-7:
                raise ValueError('Active equilibrium differs from derived source equation')
            for row in rows:
                if c[2] == 0 and row['PLANT_STATE2'] != 0 or c[16] == 0 and row['PLANT_STATE5'] != 0:
                    raise ValueError('Zero-gain integral storage accumulated')
            profiles.append(dict(profile=name, samples=len(rows), step_s=step, csv_sha256=first['files']['native.csv'],
                                 input_sha256=first['files']['dynamic.dyr'],
                                 runs=[run.relative_to(root).as_posix() for run in runs]))
    diagnostics = []
    for mode in ('ki-deadband', 'reject-ki-zero-kp', 'reject-ki-voltage-bound', 'reject-kig-zero-kpg-noffwd'):
        run = root / 'target' / ('hplntd-control-reference-proportional-' + mode + '-coarse-a')
        manifest = json.loads((run / 'manifest.json').read_text())
        for name, expected in manifest['files'].items():
            if Path(name).name != name or digest(run / name) != expected:
                raise ValueError('Changed diagnostic raw evidence')
        if (run / 'dynamic.dyr').read_bytes() != variant(baseline, mode):
            raise ValueError('Changed diagnostic input')
        expected = 'native-converged-profile' if mode == 'reject-ki-voltage-bound' else 'rejected-native-quality'
        if manifest['status'] != expected:
            raise ValueError('Changed diagnostic classification')
        diagnostics.append(dict(mode=mode, status=expected, run=run.relative_to(root).as_posix(),
                                classification='unresolved-initial-reference-boundary' if mode == 'reject-ki-voltage-bound' else 'rejected-initialization'))
    return dict(status='bounded-proportional-reference-evidence', profile_count=len(profiles), cold_runs=2 * len(profiles), profiles=profiles, diagnostics=diagnostics)


if __name__ == '__main__':
    print(json.dumps(audit(Path(__file__).resolve().parents[1]), indent=2))
