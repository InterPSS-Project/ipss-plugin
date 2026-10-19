"""Small hybrid switching reference: physical Mvar, all ten shunts, raw timers."""
import base64
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shlex
import shutil
import struct
import sys
from hplntd_fault_probe import checked, sha

MODES = ('positive-fast', 'negative-fast', 'positive-slow', 'negative-slow',
         'disabled', 'invalid-thresholds', 'missing-branch', 'zero-delay')


def configuration(original, mode):
    if mode not in MODES:
        raise ValueError('Unknown switching mode')
    if hashlib.sha256(original).hexdigest() != '1a6bd67dbd0c25557697d469a0e7a738536f0a88d68034592eb3bae16063d77c':
        raise ValueError('Changed switching input baseline')
    negative = mode.startswith('negative')
    shunts = [(('C' if i < 5 else 'R') + str(i % 5 + 1), int(negative if i < 5 else not negative), .01 if i < 5 else -.01) for i in range(10)]
    # Three header/allocation fields, six wrapper counts, then 35 ICON/61 CON.
    changes = {18: '0' if mode == 'disabled' else '1', 19: '-2' if negative else '2', 20: '3', 21: "'1'"}
    if mode == 'missing-branch':
        changes[19], changes[20] = '1', '3'
    for i, (name, _, _) in enumerate(shunts):
        changes[22 + 2 * i], changes[23 + 2 * i] = '2', "'" + name + "'"
    changes.update({88: '-5', 89: '-20' if mode.endswith('slow') else '-10',
                    90: '5', 91: '20' if mode.endswith('slow') else '10',
                    92: '.01', 93: '.003', 94: '.004', 95: '.03'})
    if mode == 'invalid-thresholds':
        changes[89] = '-1'
    if mode == 'zero-delay':
        changes[92] = changes[93] = changes[94] = '0'
    text = original.decode()
    record, = [line for line in text.splitlines() if "'USRBUS'" in line]
    fields = list(re.finditer(r"'(?:[^']|'')*'|[^\s,]+", record))
    changed = record
    for index, value in sorted(changes.items(), reverse=True):
        field = fields[index]
        changed = changed[:field.start()] + value + changed[field.end():]
    return text.replace(record, changed).encode(), shunts


def main():
    import psse3607
    import psspy
    import dyntools
    root = Path.cwd()
    tag, entered_step, mode = sys.argv[1:]
    h = float(entered_step)
    assert h in (.0005, .00025) and tag and all(c.isalnum() or c in '-_' for c in tag)
    original = base64.b64decode((root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64').read_bytes())
    dynamic, shunts = configuration(original, mode)
    out = root / 'target' / ('hplntd-switching-reference-' + tag)
    out.mkdir(exist_ok=False)
    shutil.copy2(__file__, out / 'probe.py')
    shutil.copy2(root / 'tools/hplntd_fault_probe.py', out / 'hplntd_fault_probe.py')
    raw = (root / 'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regfma1_native.raw').read_text()
    lines = raw.splitlines()
    bus = next(s for s in lines if s.lstrip().startswith("1,'BUS1"))
    raw = raw.replace('0 / END OF BUS DATA', bus.replace("1,'BUS1", "4,'BUS4", 1) + '\n0 / END OF BUS DATA')
    gen = next(s for s in lines if s.lstrip().startswith("1,'1 "))
    fields = gen.split(','); fields[0], fields[2], fields[8] = '4', '30.0', '100.0'
    raw = raw.replace('0 / END OF GENERATOR DATA', ','.join(fields) + '\n0 / END OF GENERATOR DATA')
    branch = next(s for s in lines if "2,'1 '" in s and '1.00000E-1' in s)
    raw = raw.replace('0 / END OF BRANCH DATA', branch.replace('     1,     2,', '     4,     2,', 1) + '\n0 / END OF BRANCH DATA')
    devices = '\n'.join("2,'" + name + "'," + str(status) + ',0,' + str(b) for name, status, b in shunts)
    raw = raw.replace('0 / END OF FIXED SHUNT DATA', devices + '\n0 / END OF FIXED SHUNT DATA')
    (out / 'network.raw').write_text(raw)
    (out / 'dynamic.dyr').write_bytes(dynamic)
    record, = [line for line in dynamic.decode().splitlines() if "'USRBUS'" in line]
    tokens = shlex.split(record.rstrip().removesuffix('/'))
    assert len(tokens) == 105 and tokens[3:9] == ['504', '0', '35', '61', '13', '45']
    declared = list(map(float, tokens[44:]))
    os.chdir(out)
    checked('initialize', psspy.psseinit(50))
    for fn in (psspy.progress_output, psspy.report_output, psspy.alert_output, psspy.prompt_output):
        checked('logging', fn(2, 'native.log', [0, 0]))
    library = Path(psspy.__file__).resolve().parents[1] / 'PSSBIN/DSMDL.DLL'
    document = Path(psspy.__file__).resolve().parents[1] / 'DOCS/ModelLibrary.pdf'
    checked('library', psspy.addmodellibrary(str(library)))
    checked('read', psspy.read(0, 'network.raw'))
    checked('flow', psspy.fnsl([0, 0, 0, 1, 1, 0, 99, 0])); assert psspy.solved() == 0
    checked('convert generators', psspy.cong(0))
    for stage in (1, 2, 3):
        checked('convert loads', psspy.conl(0, 1, stage, [0, 0], [0.] * 4))
    checked('ordering', psspy.ordr(0)); checked('factorization', psspy.fact()); checked('switching solution', psspy.tysl(0))
    checked('dynamics', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    layout = {}
    for prefix, bus, counts in [('PLANT', 2, {'ICON': 35, 'CON': 61, 'STATE': 13, 'VAR': 45}),
                                ('UNIT1', 1, {'ICON': 9, 'CON': 8, 'STATE': 2, 'VAR': 7}),
                                ('UNIT4', 4, {'ICON': 9, 'CON': 8, 'STATE': 2, 'VAR': 7})]:
        query = (lambda array: psspy.cctmind_buso(bus, 'HPLNTDU', array)) if prefix == 'PLANT' else (lambda array: psspy.windmind(bus, '1', 'WAUX', array))
        layout[prefix] = {}
        for array, count in counts.items():
            start = checked(prefix + array, query(array)); assert checked(prefix + 'N' + array, query('N' + array)) == count
            layout[prefix][array], layout[prefix]['N' + array] = start, count
            if array in ('STATE', 'VAR'):
                channel = psspy.state_channel if array == 'STATE' else psspy.var_channel
                for i in range(count):
                    checked('channel', channel([-1, start + i], prefix + '_' + array + str(i)))
    for bus in (1, 2, 3, 4):
        checked('voltage', psspy.voltage_and_angle_channel([-1, -1, -1, bus], ['V' + str(bus), 'A' + str(bus) + '_DEG']))
    for bus in (1, 4):
        for code, name in ((2, 'P_PU'), (3, 'Q_PU')):
            checked('power', psspy.machine_array_channel([-1, code, bus], '1', 'UNIT' + str(bus) + '_' + name))
    checked('frequency', psspy.bus_frequency_channel([-1, 2], 'FREQ2'))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('settings', psspy.dynamics_solution_param_2([200] + [di] * 7, [df, df, h, .05] + [df] * 4))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    actual = [checked('constant', psspy.dsrval('CON', layout['PLANT']['CON'] + i)) for i in range(61)]
    assert actual == [struct.unpack('f', struct.pack('f', v))[0] for v in declared]
    snapshots = []
    def sample():
        values = [checked('clock', psspy.dsrval('TIME')), checked('Qswitch', psspy.dsrval('VAR', layout['PLANT']['VAR'] + 13))]
        for name, _, _ in shunts:
            code, status = psspy.fxsint(2, name, 'STATUS'); assert code in (0, 4)
            code, nominal = psspy.fxsdt2(2, name, 'NOM'); assert code in (0, 4)
            values.extend([status, nominal.real, nominal.imag])
        snapshots.append(values)
    sample()
    for tick in range(1, round(.1 / h) + 1):
        checked('run', psspy.run(0, round(tick * h, 9), 1000000, 1, 0)); sample()
    with Path('shunt-status.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['time_s', 'Q_SWITCH_MVAR'] + [name + '_' + field for name, _, _ in shunts for field in ('STATUS', 'G_NOM', 'B_NOM')])
        writer.writerows(snapshots)
    _, labels, channels = dyntools.CHNF('native.out').get_data()
    columns = sorted(k for k in labels if isinstance(k, int)); assert len(columns) == 89
    assert all(math.isfinite(v) for key in ['time'] + columns for v in channels[key])
    with Path('native.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n'); writer.writerow(['time_s'] + [labels[k] for k in columns])
        for i, time in enumerate(channels['time']):
            writer.writerow([format(time, '.12g')] + [format(channels[k][i], '.12g') for k in columns])
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    files = ['network.raw', 'dynamic.dyr', 'probe.py', 'hplntd_fault_probe.py', 'native.log', 'native.csv', 'native.out', 'shunt-status.csv']
    manifest = dict(status='native-converged-profile' if quality else 'rejected-native-quality', model='HPLNTDU', profile=mode,
                    step_s=h, end_s=.1, system_mva=100, machine_mva={'UNIT1': 50, 'UNIT4': 100},
                    shunts=shunts, layout=layout, declared_constants=declared, actual_constants=actual,
                    library_sha256=sha(library), source_document_sha256=sha(document), engine_module=psse3607.__name__,
                    wrapper_sha256=sha(psspy.__file__), python_sha256=sha(sys.executable),
                    sample_count=len(channels['time']), status_count=len(snapshots), channel_count=89,
                    files={name: sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(dict(status=manifest['status'], samples=manifest['sample_count'], status_samples=len(snapshots), csv_sha256=manifest['files']['native.csv'])))
    assert quality


if __name__ == '__main__':
    main()
