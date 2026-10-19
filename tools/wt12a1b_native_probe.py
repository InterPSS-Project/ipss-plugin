"""Exact active-stall controller references on a public two-bus wind testbed."""
import csv
import json
import math
import os
from pathlib import Path
import shutil
import struct
import sys
from hplntd_fault_probe import checked, sha

PROFILES = ('flat', 'fault', 'direct', 'below', 'rate', 'repeat')


def configuration(profile):
    if profile not in PROFILES:
        raise ValueError('Unknown active-stall profile')
    c = [.02, .4, -.6, .2, .1, .3, .3, .3, .6, .2, .8, .1, .95, .05]
    if profile == 'direct':
        c[0] = 0.
    if profile == 'below':
        c[5] = .8
    if profile == 'rate':
        c[1], c[2] = .03, -.05
    events = [] if profile == 'flat' else [( .05, 'fault'), (.1, 'clear')]
    if profile == 'repeat':
        events += [(.35, 'fault'), (.4, 'clear')]
    dynamic = "1 'WT1G1' '1' .810 .035 3.600 .210 .150 .100 1.000 .025 1.200 .140 /\n1 'WT12T1' '1' 5.4 .05 .82 4.7 .9 /\n1 'WT12A1B' '1' " + ' '.join(map(str, c)) + " /\n2 'GENCLS' '1' 99999.0 0.0 /\n"
    return dynamic, c, events


def main():
    import psse3607
    import psspy
    import dyntools
    root = Path.cwd()
    tag, entered_step, profile = sys.argv[1:]
    h = float(entered_step)
    assert h in (.0005, .00025) and tag and all(c.isalnum() or c in '-_' for c in tag)
    dynamic, constants, events = configuration(profile)
    out = root / 'target' / ('wt12a1b-native-' + tag)
    out.mkdir(exist_ok=False)
    shutil.copy2(__file__, out / 'probe.py'); shutil.copy2(root / 'tools/hplntd_fault_probe.py', out / 'hplntd_fault_probe.py')
    shutil.copy2(root / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33_wt1g1.raw', out / 'network.raw')
    (out / 'dynamic.dyr').write_text(dynamic)
    os.chdir(out)
    checked('initialize', psspy.psseinit(50))
    for fn in (psspy.progress_output, psspy.report_output, psspy.alert_output, psspy.prompt_output):
        checked('log', fn(2, 'native.log', [0, 0]))
    library = Path(psspy.__file__).resolve().parents[1] / 'PSSBIN/DSMDL.DLL'
    document = Path(psspy.__file__).resolve().parents[1] / 'DOCS/ModelLibrary.pdf'
    checked('library', psspy.addmodellibrary(str(library))); checked('network', psspy.read(0, 'network.raw'))
    checked('flow', psspy.fnsl([0, 0, 0, 1, 1, 0, 99, 0])); assert psspy.solved() == 0
    checked('convert generators', psspy.cong(0))
    for stage in (1, 2, 3):
        checked('convert loads', psspy.conl(0, 1, stage, [0, 0], [0.] * 4))
    checked('order', psspy.ordr(0)); checked('factor', psspy.fact()); checked('switching solution', psspy.tysl(0))
    checked('dynamic input', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    layout = {}
    for category, prefix in [('WAERO', 'AERO'), ('WMECH', 'MECH'), ('WGEN', 'GEN')]:
        layout[prefix] = {}
        for kind in ('CON', 'ICON', 'STATE', 'VAR'):
            code, index = psspy.windmind(1, '1', category, kind)
            if kind == 'ICON' and code == 6:
                index, count = 0, 0
            else:
                checked(category + kind, code); count = checked(category + 'N' + kind, psspy.windmind(1, '1', category, 'N' + kind))
            layout[prefix][kind], layout[prefix]['N' + kind] = index, count
            if kind in ('STATE', 'VAR'):
                fn = psspy.state_channel if kind == 'STATE' else psspy.var_channel
                for i in range(count):
                    checked('channel', fn([-1, index + i], prefix + '_' + kind + str(i)))
    Path('allocation.json').write_text(json.dumps(layout, indent=2) + '\n')
    assert tuple(layout['AERO']['N' + field] for field in ('CON', 'ICON', 'STATE', 'VAR')) == (14, 1, 3, 2), layout
    for bus in (1, 2):
        checked('voltage', psspy.voltage_and_angle_channel([-1, -1, -1, bus], ['V' + str(bus), 'A' + str(bus) + '_DEG']))
    for code, name in ((2, 'P_PU'), (3, 'Q_PU'), (7, 'SPEED_DEV')):
        checked('machine channel', psspy.machine_array_channel([-1, code, 1], '1', name))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('settings', psspy.dynamics_solution_param_2([200] + [di] * 7, [df, df, h, .05] + [df] * 4))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    actual = [checked('actual constant', psspy.dsrval('CON', layout['AERO']['CON'] + i)) for i in range(14)]
    assert actual == [struct.unpack('f', struct.pack('f', v))[0] for v in constants]
    recorded, switches = [], []
    def sample():
        switches.append([checked('clock', psspy.dsrval('TIME')), checked('switch', psspy.dsival('ICON', layout['AERO']['ICON'])), checked('timer', psspy.dsrval('VAR', layout['AERO']['VAR'] + 1))])
    sample()
    for tick in range(1, round(1. / h) + 1):
        time = round(tick * h, 9)
        checked('run endpoint', psspy.run(0, time, 1000000, 1, 0)); sample()
        for event_time, action in events:
            if event_time != time:
                continue
            clock = checked('actual event clock', psspy.dsrval('TIME'))
            if action == 'fault':
                checked('fault', psspy.dist_bus_fault(1, 3, 100., [0., 10.]))
            else:
                checked('clear', psspy.dist_clear_fault(1))
            recorded.append(dict(time_s=time, actual_time_s=clock, action=action))
    with Path('switch-status.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n'); writer.writerow(['time_s', 'SWITCH_ICON', 'TIMER_VAR']); writer.writerows(switches)
    _, labels, channels = dyntools.CHNF('native.out').get_data()
    columns = sorted(k for k in labels if isinstance(k, int))
    assert all(math.isfinite(v) for key in ['time'] + columns for v in channels[key])
    with Path('native.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n'); writer.writerow(['time_s'] + [labels[k] for k in columns])
        for i, time in enumerate(channels['time']):
            writer.writerow([format(time, '.12g')] + [format(channels[k][i], '.12g') for k in columns])
    log = Path('native.log').read_text(errors='replace'); quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    files = ['network.raw', 'dynamic.dyr', 'probe.py', 'hplntd_fault_probe.py', 'allocation.json', 'native.log', 'native.out', 'native.csv', 'switch-status.csv']
    manifest = dict(status='native-converged-profile' if quality else 'rejected-native-quality', model='WT12A1B', profile=profile, step_s=h, end_s=1.,
                    events=recorded, fault=dict(bus=1, units=3, base_kv=100, impedance_ohm=[0, 10], impedance_system_pu=[0, .1]),
                    layout=layout, declared_constants=constants, actual_constants=actual, sample_count=len(channels['time']), channel_count=len(columns), status_count=len(switches),
                    library_sha256=sha(library), source_document_sha256=sha(document), wrapper_sha256=sha(psspy.__file__), python_sha256=sha(sys.executable), engine_module=psse3607.__name__,
                    files={name: sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(dict(status=manifest['status'], layout=layout, samples=manifest['sample_count'], channels=manifest['channel_count'])))
    assert quality


if __name__ == '__main__':
    main()
