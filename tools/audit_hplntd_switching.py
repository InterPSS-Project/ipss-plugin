"""Audit physical-Mvar switching references; no physical-network parity claim."""
import base64
import csv
import gzip
import hashlib
import json
import math
from pathlib import Path
import shlex
import struct
from hplntd_switching_probe import MODES, configuration


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def table(path, width, count):
    with path.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream)
        if len(reader.fieldnames) != width or len(set(reader.fieldnames)) != width:
            raise ValueError('Changed reference columns')
        rows = []
        for raw in reader:
            if None in raw or any(v is None for v in raw.values()):
                raise ValueError('Ragged reference row')
            row = {k: float(v) for k, v in raw.items()}
            if not all(math.isfinite(v) for v in row.values()):
                raise ValueError('Nonfinite reference')
            rows.append(row)
    if len(rows) != count:
        raise ValueError('Changed reference sample count')
    return rows


def read_run(directory, root, mode, step, baseline):
    m = json.loads((directory / 'manifest.json').read_text())
    if (m['status'], m['model'], m['profile'], m['step_s'], m['end_s'], m['channel_count']) != ('native-converged-profile', 'HPLNTDU', mode, step, .1, 89):
        raise ValueError('Changed switching profile identity')
    required = {'network.raw', 'dynamic.dyr', 'probe.py', 'hplntd_fault_probe.py', 'native.log', 'native.csv', 'native.out', 'shunt-status.csv'}
    if set(m['files']) != required:
        raise ValueError('Incomplete switching manifest')
    for name, expected in m['files'].items():
        if digest(directory / name) != expected:
            raise ValueError('Changed raw artifact: ' + name)
    for archived, source in [('probe.py', 'hplntd_switching_probe.py'), ('hplntd_fault_probe.py', 'hplntd_fault_probe.py')]:
        if digest(directory / archived) != digest(root / 'tools' / source):
            raise ValueError('Changed generating source')
    expected, shunts = configuration(baseline, mode)
    if (directory / 'dynamic.dyr').read_bytes() != expected or m['shunts'] != [list(v) for v in shunts]:
        raise ValueError('Changed exact switching inputs')
    record, = [line for line in expected.decode().splitlines() if "'USRBUS'" in line]
    tokens = shlex.split(record.rstrip().removesuffix('/'))
    declared = list(map(float, tokens[44:]))
    if len(tokens) != 105 or len(declared) != 61 or declared != m['declared_constants']:
        raise ValueError('Changed entered constants')
    if [struct.unpack('f', struct.pack('f', v))[0] for v in declared] != m['actual_constants']:
        raise ValueError('Native constant correction')
    for prefix, counts in [('PLANT', (35, 61, 13, 45)), ('UNIT1', (9, 8, 2, 7)), ('UNIT4', (9, 8, 2, 7))]:
        if tuple(m['layout'][prefix]['N' + name] for name in ('ICON', 'CON', 'STATE', 'VAR')) != counts:
            raise ValueError('Changed allocation')
    log = (directory / 'native.log').read_text(errors='replace')
    if 'Initial conditions check O.K.' not in log or 'Network not converged' in log:
        raise ValueError('Rejected reference initialization/convergence')
    ticks = round(.1 / step)
    count = 2 * ticks + (3 if mode == 'zero-delay' else 2)
    if m['sample_count'] != count or m['status_count'] != ticks + 1:
        raise ValueError('Changed declared timeline')
    rows = table(directory / 'native.csv', 90, count)
    status = table(directory / 'shunt-status.csv', 32, ticks + 1)
    expected_channels = ['time_s']
    for prefix, ns, nv in [('PLANT', 13, 45), ('UNIT1', 2, 7), ('UNIT4', 2, 7)]:
        expected_channels += [prefix + '_STATE' + str(i) for i in range(ns)]
        expected_channels += [prefix + '_VAR' + str(i) for i in range(nv)]
    expected_channels += [v + str(bus) + ('_DEG' if v == 'A' else '') for bus in (1, 2, 3, 4) for v in ('V', 'A')]
    expected_channels += ['UNIT' + str(bus) + '_' + v + '_PU' for bus in (1, 4) for v in ('P', 'Q')]
    expected_channels += ['FREQ2']
    if list(rows[0]) != expected_channels:
        raise ValueError('Changed semantic channel layout')
    if sum(r['time_s'] < 0 for r in rows) != 2 or sum(r['time_s'] == 0 for r in rows) != (2 if mode == 'zero-delay' else 1):
        raise ValueError('Lost origin/history observation side')
    if any(a['time_s'] > b['time_s'] for a, b in zip(rows, rows[1:])):
        raise ValueError('Nonmonotone raw reference clock')
    by_tick = {}
    for row in rows:
        if row['time_s'] >= 0:
            tick = round(row['time_s'] / step)
            if abs(row['time_s'] - tick * step) > step / 10:
                raise ValueError('Changed raw sample clock')
            by_tick[tick] = by_tick.get(tick, 0) + 1
    expected_ticks = {i: (2 if i else 2 if mode == 'zero-delay' else 1) for i in range(ticks + 1)}
    expected_ticks[ticks] = 1
    if by_tick != expected_ticks:
        raise ValueError('Lost raw accepted-endpoint/duplicate sample')
    if abs(status[-1]['time_s'] - .1) > step / 10:
        raise ValueError('Truncated status clock')
    events = []
    prior = dict(status[0])
    for tick, row in enumerate(status):
        expected_time = tick * step if tick else -2 * step
        if abs(row['time_s'] - expected_time) > step / 10:
            raise ValueError('Changed status clock')
        for name, initial, nominal in shunts:
            if row[name + '_STATUS'] not in (0, 1) or row[name + '_G_NOM'] != 0 or abs(row[name + '_B_NOM'] - nominal) > 1e-8:
                raise ValueError('Changed physical device status/nominal')
            if tick == 0 and row[name + '_STATUS'] != initial:
                raise ValueError('Changed initial device status')
            if row[name + '_STATUS'] != prior[name + '_STATUS']:
                events.append(dict(device=name, time_s=row['time_s'], in_service=int(row[name + '_STATUS'])))
        prior = row
    expected_order = [name for name, _, _ in (shunts if mode.startswith('negative') else shunts[5:] + shunts[:5])]
    if mode in ('disabled', 'invalid-thresholds', 'missing-branch'):
        expected_order = []
    if [e['device'] for e in events] != expected_order:
        raise ValueError('Changed physical switching order')
    if mode.startswith(('positive', 'negative')):
        sign = -1 if mode.startswith('negative') else 1
        q = [sign * r['PLANT_VAR13'] for r in rows]
        if min(q) <= 10 or max(q) >= 20:
            raise ValueError('Lost Mvar band discriminator')
    return m, rows, status, events


def audit(root):
    resource = root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native'
    baseline = base64.b64decode((resource / 'plant-low-window-coarse.dyr.b64').read_bytes())
    profiles, identity = [], None
    for mode in MODES:
        for label, step in [('coarse', .0005), ('fine', .00025)]:
            runs = [root / 'target' / ('hplntd-switching-reference-' + mode + '-' + label + '-' + repeat) for repeat in ('a', 'b')]
            first, rows, statuses, events = read_run(runs[0], root, mode, step, baseline)
            second, repeated, repeated_status, repeated_events = read_run(runs[1], root, mode, step, baseline)
            if rows != repeated or statuses != repeated_status or events != repeated_events:
                raise ValueError('Changed cold switching reference')
            for name in ('native.csv', 'shunt-status.csv', 'dynamic.dyr', 'network.raw', 'probe.py', 'hplntd_fault_probe.py'):
                if first['files'][name] != second['files'][name]:
                    raise ValueError('Changed byte-exact cold artifact: ' + name)
            for m in (first, second):
                current = tuple(m[k] for k in ('source_document_sha256', 'library_sha256', 'wrapper_sha256', 'python_sha256'))
                if identity is None:
                    identity = current
                if current != identity:
                    raise ValueError('Mixed reference runtime identities')
            profile = 'switching-' + mode + '-' + label
            network = resource / ('switching-' + ('negative' if mode.startswith('negative') else 'positive') + '.raw.b64')
            if base64.b64decode(network.read_bytes()) != (runs[0] / 'network.raw').read_bytes():
                raise ValueError('Changed embedded exact switching network')
            for suffix, file in [('csv', 'native.csv'), ('status', 'shunt-status.csv'), ('dyr', 'dynamic.dyr')]:
                embedded = base64.b64decode((resource / (profile + '.' + suffix + '.b64')).read_bytes())
                if suffix != 'dyr':
                    embedded = gzip.decompress(embedded)
                if embedded != (runs[0] / file).read_bytes():
                    raise ValueError('Changed embedded reference: ' + suffix)
            profiles.append(dict(profile=profile, samples=len(rows), status_samples=len(statuses), events=events,
                                 csv_sha256=first['files']['native.csv'], status_sha256=first['files']['shunt-status.csv'],
                                 role='zero-delay-origin-timer-diagnostic' if mode == 'zero-delay' else 'threshold-breaker-status-kernel-acceptance',
                                 runs=[run.relative_to(root).as_posix() for run in runs]))
    return dict(status='audited-kernel-evidence-not-network-import-acceptance', profile_count=16, cold_runs=32,
                accepted_timer_profiles=14, zero_delay_diagnostics=2, profiles=profiles)


if __name__ == '__main__':
    print(json.dumps(audit(Path(__file__).resolve().parents[1]), indent=2))
