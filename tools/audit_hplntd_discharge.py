"""Audit cold fault/discharge counterexamples without promoting them to parity."""
import base64
import gzip
import json
from pathlib import Path
import struct
import shlex
from audit_hplntd_switching import digest, table
from hplntd_discharge_probe import DELAYS, EVENTS, variant


def toggles(rows):
    changes = []
    previous = rows[0]
    for row in rows[1:]:
        for device in ['C' + str(i) for i in range(1, 6)] + ['R' + str(i) for i in range(1, 6)]:
            field = device + '_STATUS'
            if row[field] != previous[field]:
                changes.append(dict(device=device, time_s=row['time_s'], in_service=int(row[field])))
        previous = row
    return changes


def audit(root):
    resource = root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native'
    baseline = base64.b64decode((resource / 'plant-low-window-coarse.dyr.b64').read_bytes())
    network = base64.b64decode((resource / 'switching-positive.raw.b64').read_bytes())
    profiles, by_step, identity = [], {}, None
    for mode, delay in DELAYS.items():
        for label, step in [('coarse', .0005), ('fine', .00025)]:
            runs = [root / 'target' / ('hplntd-discharge-reference-' + mode + '-' + label + '-' + repeat) for repeat in ('a', 'b')]
            original = None
            for run in runs:
                m = json.loads((run / 'manifest.json').read_text())
                if (m['status'], m['model'], m['profile'], m['step_s'], m['end_s'], m['events']) != ('native-converged-profile', 'HPLNTDU', mode, step, .3, EVENTS):
                    raise ValueError('Changed discharge reference identity/events')
                expected_files = {'network.raw', 'dynamic.dyr', 'probe.py', 'hplntd_switching_probe.py', 'hplntd_fault_probe.py', 'native.log', 'native.csv', 'native.out', 'shunt-status.csv'}
                if set(m['files']) != expected_files:
                    raise ValueError('Incomplete discharge manifest')
                for name, sha in m['files'].items():
                    if digest(run / name) != sha:
                        raise ValueError('Changed raw discharge artifact: ' + name)
                for archived, source in [('probe.py', 'hplntd_discharge_probe.py'), ('hplntd_switching_probe.py', 'hplntd_switching_probe.py'), ('hplntd_fault_probe.py', 'hplntd_fault_probe.py')]:
                    if digest(run / archived) != digest(root / 'tools' / source):
                        raise ValueError('Changed generating source')
                expected, shunts = variant(baseline, mode)
                if (run / 'dynamic.dyr').read_bytes() != expected or (run / 'network.raw').read_bytes() != network or m['shunts'] != [list(v) for v in shunts]:
                    raise ValueError('Changed exact discharge input')
                declared = m['declared_constants']
                record, = [s for s in expected.decode().splitlines() if "'USRBUS'" in s]
                tokens = shlex.split(record.rstrip().removesuffix('/'))
                if declared != list(map(float, tokens[44:])):
                    raise ValueError('Manifest constants differ from exact entered tokens')
                if len(declared) != 61 or declared[51] != delay or [struct.unpack('f', struct.pack('f', v))[0] for v in declared] != m['actual_constants']:
                    raise ValueError('Changed entered/native delay or other constants')
                current = tuple(m[k] for k in ('source_document_sha256', 'library_sha256', 'wrapper_sha256', 'python_sha256'))
                if current[:2] != ('40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d', 'c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6'):
                    raise ValueError('Changed exact model/source version')
                if identity is None:
                    identity = current
                if current != identity:
                    raise ValueError('Mixed discharge runtime identity')
                ticks = round(.3 / step)
                for prefix, counts in [('PLANT', (35, 61, 13, 45)), ('UNIT1', (9, 8, 2, 7)), ('UNIT4', (9, 8, 2, 7))]:
                    if tuple(m['layout'][prefix]['N' + field] for field in ('ICON', 'CON', 'STATE', 'VAR')) != counts:
                        raise ValueError('Changed model allocation')
                if m['sample_count'] != 2 * ticks + 2 or m['status_count'] != ticks + 1 or m['channel_count'] != 89:
                    raise ValueError('Lost raw rows or physical queries')
                raw = table(run / 'native.csv', 90, m['sample_count'])
                status = table(run / 'shunt-status.csv', 33, m['status_count'])
                log = (run / 'native.log').read_text(errors='replace')
                if 'Initial conditions check O.K.' not in log or 'Network not converged' in log:
                    raise ValueError('Rejected native initialization/convergence')
                if sum(r['time_s'] < 0 for r in raw) != 2 or any(a['time_s'] > b['time_s'] for a, b in zip(raw, raw[1:])):
                    raise ValueError('Lost history or reordered raw time')
                for tick, row in enumerate(status):
                    expected_time = step * tick if tick else -2 * step
                    if abs(row['time_s'] - expected_time) > step / 10:
                        raise ValueError('Changed native query clock')
                    for name, initial, nominal in shunts:
                        if row[name + '_STATUS'] not in (0, 1) or row[name + '_G_NOM'] != 0 or abs(row[name + '_B_NOM'] - nominal) > 1e-8:
                            raise ValueError('Changed nominal physical shunt')
                        if tick == 0 and row[name + '_STATUS'] != initial:
                            raise ValueError('Changed initial status')
                if not min(r['Q_SWITCH_MVAR'] for r in status) < -10 < 10 < max(r['Q_SWITCH_MVAR'] for r in status):
                    raise ValueError('No real reactive-power direction reversal')
                signatures = tuple(digest(run / name) for name in ('native.csv', 'shunt-status.csv', 'dynamic.dyr', 'network.raw'))
                if original is None:
                    original = signatures
                if original != signatures:
                    raise ValueError('Changed byte-exact cold discharge reference')
            key = 'discharge-' + mode + '-' + label
            for suffix, file in [('csv', 'native.csv'), ('status', 'shunt-status.csv'), ('dyr', 'dynamic.dyr')]:
                body = base64.b64decode((resource / (key + '.' + suffix + '.b64')).read_bytes())
                if suffix != 'dyr':
                    body = gzip.decompress(body)
                if body != (runs[0] / file).read_bytes():
                    raise ValueError('Changed embedded discharge reference')
            output = original[:2]
            if label not in by_step:
                by_step[label] = output
            if by_step[label] != output:
                raise ValueError('Tout-only native invariance discriminator changed')
            changes = toggles(status)
            if len(changes) != 30:
                raise ValueError('Missing physical opening/reclose actions')
            intervals = []
            for device in ['C' + str(i) for i in range(1, 6)]:
                series = [e for e in changes if e['device'] == device]
                if [e['in_service'] for e in series] != [1, 0, 1]:
                    raise ValueError('Changed capacitor lifecycle')
                interval = series[2]['time_s'] - series[1]['time_s']
                if mode == 'long' and not interval < delay:
                    raise ValueError('Long-delay native counterexample no longer present')
                intervals.append(dict(device=device, opened_s=series[1]['time_s'], reclosed_s=series[2]['time_s'], off_duration_s=interval))
            profiles.append(dict(profile=key, step_s=step, samples=len(raw), status_samples=len(status), entered_discharge_s=delay,
                                 actual_event_clocks_s=[status[round(e['time_s'] / step)]['time_s'] for e in EVENTS],
                                 csv_sha256=original[0], status_sha256=original[1], input_sha256=original[2],
                                 capacitors=intervals, runs=[r.relative_to(root).as_posix() for r in runs]))
    return dict(status='audited-native-discharge-and-direction-timer-counterexamples-not-parity', cold_runs=12, profiles=profiles)


if __name__ == '__main__':
    print(json.dumps(audit(Path(__file__).resolve().parents[1]), indent=2))
