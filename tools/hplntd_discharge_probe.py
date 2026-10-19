"""Three-cycle direction reversal: isolate capacitor discharge/reclose semantics."""
import base64
import csv
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
from hplntd_switching_probe import configuration

DELAYS = {'zero': 0., 'short': .03, 'long': .15}
EVENTS = [dict(time_s=.03, action='three-phase-fault', bus=2, units=3, base_kv=230, impedance_ohm=[0, 529], impedance_system_pu=[0, 1]),
          dict(time_s=.08, action='clear-fault')]


def variant(original, profile):
    if profile not in DELAYS:
        raise ValueError('Unknown discharge discriminator')
    body, shunts = configuration(original, 'positive-fast')
    text = body.decode()
    record, = [v for v in text.splitlines() if "'USRBUS'" in v]
    fields = list(re.finditer(r"'(?:[^']|'')*'|[^\s,]+", record))
    field = fields[95]
    updated = record[:field.start()] + str(DELAYS[profile]) + record[field.end():]
    return text.replace(record, updated).encode(), shunts


def main():
    import psse3607
    import psspy
    import dyntools
    root = Path.cwd()
    tag, entered_step, profile = sys.argv[1:]
    h = float(entered_step)
    assert h in (.0005, .00025) and tag and all(c.isalnum() or c in '-_' for c in tag)
    resources = root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native'
    original = base64.b64decode((resources / 'plant-low-window-coarse.dyr.b64').read_bytes())
    dynamic, shunts = variant(original, profile)
    out = root / 'target' / ('hplntd-discharge-reference-' + tag)
    out.mkdir(exist_ok=False)
    for source, destination in [('hplntd_discharge_probe.py', 'probe.py'), ('hplntd_switching_probe.py', 'hplntd_switching_probe.py'), ('hplntd_fault_probe.py', 'hplntd_fault_probe.py')]:
        shutil.copy2(root / 'tools' / source, out / destination)
    (out / 'network.raw').write_bytes(base64.b64decode((resources / 'switching-positive.raw.b64').read_bytes()))
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
        values = [checked('clock', psspy.dsrval('TIME')), checked('Qswitch', psspy.dsrval('VAR', layout['PLANT']['VAR'] + 13)), checked('physical Q', psspy.brnflo(2, 3, '1')).imag]
        for name, _, _ in shunts:
            code, status = psspy.fxsint(2, name, 'STATUS'); assert code in (0, 4)
            code, nominal = psspy.fxsdt2(2, name, 'NOM'); assert code in (0, 4)
            values.extend([status, nominal.real, nominal.imag])
        snapshots.append(values)
    sample()
    for tick in range(1, round(.3 / h) + 1):
        time = round(tick * h, 9)
        checked('run', psspy.run(0, time, 1000000, 1, 0)); sample()
        if time == .03:
            checked('fault', psspy.dist_bus_fault(2, 3, 230., [0., 529.]))
        if time == .08:
            checked('clear', psspy.dist_clear_fault(1))
    with Path('shunt-status.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['time_s', 'Q_SWITCH_MVAR', 'Q_BRANCH_MVAR'] + [name + '_' + field for name, _, _ in shunts for field in ('STATUS', 'G_NOM', 'B_NOM')]); writer.writerows(snapshots)
    _, labels, channels = dyntools.CHNF('native.out').get_data()
    columns = sorted(k for k in labels if isinstance(k, int)); assert len(columns) == 89
    assert all(math.isfinite(v) for key in ['time'] + columns for v in channels[key])
    with Path('native.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n'); writer.writerow(['time_s'] + [labels[k] for k in columns])
        for i, time in enumerate(channels['time']):
            writer.writerow([format(time, '.12g')] + [format(channels[k][i], '.12g') for k in columns])
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    files = ['network.raw', 'dynamic.dyr', 'probe.py', 'hplntd_switching_probe.py', 'hplntd_fault_probe.py', 'native.log', 'native.csv', 'native.out', 'shunt-status.csv']
    manifest = dict(status='native-converged-profile' if quality else 'rejected-native-quality', model='HPLNTDU', profile=profile,
                    step_s=h, end_s=.3, system_mva=100, machine_mva={'UNIT1': 50, 'UNIT4': 100},
                    shunts=shunts, events=EVENTS, layout=layout, declared_constants=declared, actual_constants=actual,
                    library_sha256=sha(library), source_document_sha256=sha(document), engine_module=psse3607.__name__,
                    wrapper_sha256=sha(psspy.__file__), python_sha256=sha(sys.executable), sample_count=len(channels['time']), status_count=len(snapshots), channel_count=89,
                    files={name: sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(dict(status=manifest['status'], samples=manifest['sample_count'], status_samples=len(snapshots), csv_sha256=manifest['files']['native.csv'])))
    assert quality


if __name__ == '__main__':
    main()
