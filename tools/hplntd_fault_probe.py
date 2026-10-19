"""Cold-repeat the exact frozen four-bus hybrid fault; run from repository root.

Usage: reference-python hplntd_fault_probe.py UNIQUE_TAG STEP_SECONDS
Outputs are private generated artifacts. Existing directories are never replaced.
"""
import base64
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import shlex
import shutil
import struct
import sys

def checked(label, result):
    code, value = result if isinstance(result, tuple) else (result, None)
    if code:
        raise RuntimeError((label, code))
    return value


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def run_case(tag, entered_step, *, dynamic=None, apply_fault=True, label='frozen-fault',
             directory_prefix='hplntd-fault-reference-', probe_source=None,
             reference_steps=((3, .05), (1, .02))):
    # Import the licensed runtime only when executing a reference case. Pure
    # input-schema tests must also run on machines without that installation.
    import psse3607
    import psspy
    import dyntools
    root = Path.cwd()
    h = float(entered_step)
    reference_steps = tuple(reference_steps)
    assert reference_steps and all(isinstance(offset, int) and offset in (0, 1, 2, 3, 9, 10, 11)
                                   and math.isfinite(delta) for offset, delta in reference_steps)
    assert h in (.0005, .00025)
    assert tag and all(c.isalnum() or c in '-_' for c in tag)
    assert directory_prefix in ('hplntd-fault-reference-', 'hplntd-control-reference-')
    out = root / 'target' / (directory_prefix + tag)
    out.mkdir(exist_ok=False)
    shutil.copy2(probe_source or __file__, out / 'probe.py')
    if probe_source:
        shutil.copy2(__file__, out / 'hplntd_fault_probe.py')
    source = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regfma1_native.raw'
    raw = source.read_text()
    lines = raw.splitlines()
    bus = next(s for s in lines if s.lstrip().startswith("1,'BUS1"))
    raw = raw.replace('0 / END OF BUS DATA', bus.replace("1,'BUS1", "4,'BUS4", 1) + '\n0 / END OF BUS DATA')
    gen = next(s for s in lines if s.lstrip().startswith("1,'1 "))
    fields = gen.split(',')
    fields[0], fields[2], fields[8] = '4', '30.0', '100.0'
    raw = raw.replace('0 / END OF GENERATOR DATA', ','.join(fields) + '\n0 / END OF GENERATOR DATA')
    branch = next(s for s in lines if "2,'1 '" in s and '1.00000E-1' in s)
    raw = raw.replace('0 / END OF BRANCH DATA', branch.replace('     1,     2,', '     4,     2,', 1) + '\n0 / END OF BRANCH DATA')
    (out / 'network.raw').write_text(raw)
    resource = root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64'
    frozen = base64.b64decode(resource.read_bytes())
    assert hashlib.sha256(frozen).hexdigest() == '1a6bd67dbd0c25557697d469a0e7a738536f0a88d68034592eb3bae16063d77c'
    dynamic = frozen if dynamic is None else dynamic
    assert isinstance(dynamic, bytes)
    (out / 'dynamic.dyr').write_bytes(dynamic)
    record = next(s for s in dynamic.decode().splitlines() if "'USRBUS'" in s)
    tokens = shlex.split(record.rstrip().removesuffix('/'))
    assert tokens[3:9] == ['504', '0', '35', '61', '13', '45'] and len(tokens) == 105
    declared = list(map(float, tokens[44:]))
    os.chdir(out)
    checked('initialize', psspy.psseinit(50))
    for fn in [psspy.progress_output, psspy.report_output, psspy.alert_output, psspy.prompt_output]:
        checked('logging', fn(2, 'native.log', [0, 0]))
    library = Path(psspy.__file__).resolve().parents[1] / 'PSSBIN/DSMDL.DLL'
    document = Path(psspy.__file__).resolve().parents[1] / 'DOCS/ModelLibrary.pdf'
    checked('model library', psspy.addmodellibrary(str(library)))
    checked('read network', psspy.read(0, 'network.raw'))
    checked('load flow', psspy.fnsl([0, 0, 0, 1, 1, 0, 99, 0]))
    assert psspy.solved() == 0
    checked('convert generators', psspy.cong(0))
    for stage in [1, 2, 3]:
        checked('convert loads', psspy.conl(0, 1, stage, [0, 0], [0.] * 4))
    checked('ordering', psspy.ordr(0))
    checked('factorization', psspy.fact())
    checked('switching solution', psspy.tysl(0))
    checked('read dynamics', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    layout = {}
    for prefix, bus, counts in [('PLANT', 2, {'ICON': 35, 'CON': 61, 'STATE': 13, 'VAR': 45}),
                                ('UNIT1', 1, {'ICON': 9, 'CON': 8, 'STATE': 2, 'VAR': 7}),
                                ('UNIT4', 4, {'ICON': 9, 'CON': 8, 'STATE': 2, 'VAR': 7})]:
        query = (lambda a: psspy.cctmind_buso(bus, 'HPLNTDU', a)) if prefix == 'PLANT' else (lambda a: psspy.windmind(bus, '1', 'WAUX', a))
        layout[prefix] = {}
        for array, count in counts.items():
            start = checked(prefix + array, query(array))
            assert checked(prefix + 'N' + array, query('N' + array)) == count
            layout[prefix][array], layout[prefix]['N' + array] = start, count
            if array in ('STATE', 'VAR'):
                channel = psspy.state_channel if array == 'STATE' else psspy.var_channel
                for i in range(count):
                    checked('state/variable channel', channel([-1, start + i], prefix + '_' + array + str(i)))
    for bus in [1, 2, 3, 4]:
        checked('voltage channel', psspy.voltage_and_angle_channel([-1, -1, -1, bus], ['V' + str(bus), 'A' + str(bus) + '_DEG']))
    for bus in [1, 4]:
        for code, name in [(2, 'P_PU'), (3, 'Q_PU')]:
            checked('power channel', psspy.machine_array_channel([-1, code, bus], '1', 'UNIT' + str(bus) + '_' + name))
    checked('frequency channel', psspy.bus_frequency_channel([-1, 2], 'FREQ2'))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('solver settings', psspy.dynamics_solution_param_2([200] + [di] * 7, [df, df, h, .05] + [df] * 4))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    actual = [checked('constant audit', psspy.dsrval('CON', layout['PLANT']['CON'] + i)) for i in range(61)]
    f32 = lambda v: struct.unpack('f', struct.pack('f', v))[0]
    assert actual == list(map(f32, declared)), ('Reference tool changed entered constants', declared, actual)
    checked('prefault', psspy.run(0, .05, 1000000, 1, 0))
    events = []
    for offset, delta in reference_steps:
        value = checked('initial reference', psspy.dsrval('VAR', layout['PLANT']['VAR'] + offset)) + delta
        checked('reference step', psspy.change_var(layout['PLANT']['VAR'] + offset, value))
        events.append(dict(time_s=.05, array='VAR', offset=offset, value=value))
    if apply_fault:
        checked('fault', psspy.dist_bus_fault(2, 3, 100., [0., 10.]))
        events.append(dict(time_s=.05, action='three-phase-fault', bus=2, units=3, base_kv=100,
                           impedance_ohm=[0, 10], impedance_system_pu=[0, .1]))
    checked('fault interval', psspy.run(0, .1, 1000000, 1, 0))
    if apply_fault:
        checked('clear fault', psspy.dist_clear_fault(1))
        events.append(dict(time_s=.1, action='clear-fault'))
    checked('recovery', psspy.run(0, .2, 1000000, 1, 0))
    _, labels, data = dyntools.CHNF('native.out').get_data()
    columns = sorted(k for k in labels if isinstance(k, int))
    assert len(columns) == 89
    assert all(math.isfinite(v) for key in ['time'] + columns for v in data[key])
    with Path('native.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['time_s'] + [labels[k] for k in columns])
        for i, time in enumerate(data['time']):
            writer.writerow([format(time, '.12g')] + [format(data[k][i], '.12g') for k in columns])
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    files = ['network.raw', 'dynamic.dyr', 'probe.py', 'native.log', 'native.csv', 'native.out']
    if probe_source:
        files.append('hplntd_fault_probe.py')
    manifest = dict(status='native-converged-profile' if quality else 'rejected-native-quality', model='HPLNTDU', profile=label,
                    downstream='REAXDU', step_s=h, end_s=.2, system_mva=100, machine_mva={'UNIT1': 50, 'UNIT4': 100},
                    events=events, layout=layout, declared_constants=declared, actual_constants=actual,
                    source_document_sha256=sha(document), library_sha256=sha(library),
                    engine_module=psse3607.__name__, wrapper_sha256=sha(psspy.__file__), python_sha256=sha(sys.executable),
                    channel_count=len(columns), sample_count=len(data['time']),
                    files={name: sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    assert quality
    print(json.dumps(dict(status=manifest['status'], samples=manifest['sample_count'], csv_sha256=manifest['files']['native.csv'])))


if __name__ == '__main__':
    run_case(*sys.argv[1:])
