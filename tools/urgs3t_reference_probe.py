"""Cold synthetic gas-governor captures with exact allocated-state readback."""
import argparse
import csv
import json
import math
import os
from pathlib import Path
import shutil

from tgov1_limit_probe import checked, sha

# Exact standard-record constant order; the numerical profiles are synthetic.
BASE = [.05, .1, .2, .5, 1., 2., 1.2, 0., 0., 0., 99., 99., 1., 99.,
        0., 1., 0., 0., 0., 0., 0., 1., 1., 0., 0., 0., 0., 0., 0.,
        0., 0., 0., 100.]
PROFILES = {
    'standard': {},
    'rating-idle': {8: .3, 9: .1, 32: 80.},
    'lead-lags': {14: .3, 15: 1.2, 29: 1.3, 30: .03, 31: .12},
    'curve': {19: 0., 20: 0., 21: .3, 22: .2, 23: .6, 24: .7, 25: 1., 26: 1.1},
    'rate-limits': {10: .03, 11: .001, 12: .3, 13: .005},
    'deadbands': {16: .0002, 17: .00005, 18: .0001},
    'speed-deadband': {16: .0002, 17: .00005},
    'power-deadband': {18: .0001},
    'power-deadband-wide': {18: .0004},
    'power-deadband-narrow': {18: .000025},
    'temperature-limits': {4: .501},
    'temperature-fast': {3: .05, 4: .501},
    'temperature-slow': {3: 5., 4: .501},
}


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--profile', choices=PROFILES, required=True)
    p.add_argument('--step', type=float, choices=[.0005, .00025, 1/240], required=True)
    p.add_argument('--repeat', choices=['a', 'b'], required=True)
    p.add_argument('--duration', type=float, default=.5)
    p.add_argument('--flat', action='store_true')
    p.add_argument('--reference-steps', action='store_true')
    p.add_argument('--network-tolerance', type=float,
                   help='Optional diagnostic network tolerance; omitted preserves the engine default')
    p.add_argument('--extra-pause', type=float,
                   help='Optional no-command pause during a reference-step diagnostic')
    p.add_argument('--machine-states', action='store_true')
    p.add_argument('--batch', default='urgs3t-initial-20261102')
    args = p.parse_args()
    if not 0.1 < args.duration <= 10:
        p.error('Duration must exceed clearing time and remain within ten seconds')
    if args.network_tolerance is not None and not 0 < args.network_tolerance <= .01:
        p.error('Network tolerance must be positive and at most 0.01')
    if args.extra_pause is not None and (not args.reference_steps or not .05 < args.extra_pause < .25):
        p.error('The extra pause requires reference steps and must lie between the first two commands')
    root = Path.cwd()
    if args.flat and args.reference_steps:
        p.error('Choose either flat or reference-step operation')
    tag = f'{args.profile}-h{args.step:.12g}-t{args.duration:g}-{"command" if args.reference_steps else "flat" if args.flat else "fault"}-{args.repeat}'.replace('.', 'p')
    if args.network_tolerance is not None:
        tag = tag[:-1]+f'tol{args.network_tolerance:g}-'+args.repeat
    if args.extra_pause is not None:
        tag = tag[:-1]+f'pause{args.extra_pause:g}-'+args.repeat
    if args.machine_states:
        tag = tag[:-1]+'machines-'+args.repeat
    out = root/'target'/args.batch/tag
    if shutil.disk_usage(root/'target').free < 2*1024**3 + 64*1024**2:
        raise OSError('Free-space reserve and bounded capture budget required')
    out.mkdir(parents=True, exist_ok=False)
    source = root/'ipss.test.plugin.core/testData/adpter/psse/v33/HspModels'
    shutil.copy2(source/'network.raw', out/'network.raw')
    constants = BASE.copy()
    for i, v in PROFILES[args.profile].items():
        constants[i] = v
    machine = (source/'hyg3u1-1.dyr').read_text().splitlines()
    (out/'dynamic.dyr').write_text(machine[0] + "\n1 'URGS3T' '1' " +
            ' '.join(format(v, '.17g') for v in constants) + ' /\n' + machine[-1] + '\n')
    for name in ['urgs3t_reference_probe.py', 'tgov1_limit_probe.py']:
        shutil.copy2(root/'tools'/name, out/name)
    import psse3607
    import psspy
    import dyntools
    os.chdir(out)
    checked('initialize', psspy.psseinit(50))
    for fn in [psspy.progress_output, psspy.report_output, psspy.alert_output, psspy.prompt_output]:
        checked('log', fn(2, 'native.log', [0, 0]))
    library = Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL'
    checked('library', psspy.addmodellibrary(str(library)))
    checked('network', psspy.read(0, 'network.raw'))
    checked('flow', psspy.fnsl([0, 0, 0, 1, 1, 0, 99, 0])); assert psspy.solved() == 0
    checked('generators', psspy.cong(0))
    for stage in [1, 2, 3]:
        checked('loads', psspy.conl(0, 1, stage, [0, 0], [0., 100., 0., 100.]))
    checked('order', psspy.ordr(0)); checked('factor', psspy.fact()); checked('converted', psspy.tysl(0))
    checked('dynamic', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    layout = {k: checked('layout', psspy.mdlind(1, '1', 'GOV', k))
              for k in ['CON', 'STATE', 'VAR', 'NCON', 'NSTATE', 'NVAR']}
    assert [layout[k] for k in ['NCON', 'NSTATE', 'NVAR']] == [33, 5, 5]
    entered = [checked('constant', psspy.dsrval('CON', layout['CON']+i)) for i in range(33)]
    for kind, fn in [('STATE', psspy.state_channel), ('VAR', psspy.var_channel)]:
        for i in range(layout['N'+kind]):
            checked('channel', fn([-1, layout[kind]+i], kind+str(i)))
    for code, name in [(1, 'ANGLE_DEG'), (2, 'P_PU'), (3, 'Q_PU'), (6, 'PMECH'), (7, 'SPEED'), (5, 'EFD')]:
        checked('machine', psspy.machine_array_channel([-1, code, 1], '1', name))
    machine_layouts = {}
    if args.machine_states:
        for bus in [1, 2]:
            machine_layouts[str(bus)] = {k: checked('machine layout', psspy.mdlind(bus, '1', 'GEN', k))
                                         for k in ['STATE', 'NSTATE']}
            allocation = machine_layouts[str(bus)]
            for i in range(allocation['NSTATE']):
                checked('machine state', psspy.state_channel([-1, allocation['STATE']+i], f'GEN{bus}_STATE{i}'))
        for code, name in [(1, 'ANGLE_DEG_2'), (2, 'P_PU_2'), (3, 'Q_PU_2'), (6, 'PMECH_2'), (7, 'SPEED_2'), (5, 'EFD_2')]:
            checked('machine 2', psspy.machine_array_channel([-1, code, 2], '1', name))
    for bus in [1, 2]:
        checked('voltage', psspy.voltage_and_angle_channel([-1, -1, -1, bus], [f'V{bus}', f'A{bus}']))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    tolerance = df if args.network_tolerance is None else args.network_tolerance
    checked('step', psspy.dynamics_solution_param_2([di]*8, [df, tolerance, args.step]+[df]*5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    initialized = [checked('constant', psspy.dsrval('CON', layout['CON']+i)) for i in range(33)]
    if args.reference_steps:
        for time, increment in [(.05, .2), (.25, -.4), (.4, .2)]:
            checked('reference boundary', psspy.run(0, time, 1000000, 1, 0))
            checked('reference increment', psspy.increment_gref(1, '1', increment))
            if time == .05 and args.extra_pause is not None:
                checked('no-command pause', psspy.run(0, args.extra_pause, 1000000, 1, 0))
    elif not args.flat:
        checked('prefault', psspy.run(0, .05, 1000000, 1, 0))
        checked('fault', psspy.dist_bus_fault(1, 3, 100., [0., .2*100.**2/100.]))
        checked('during', psspy.run(0, .10, 1000000, 1, 0))
        checked('clear', psspy.dist_clear_fault(1))
    checked('finish', psspy.run(0, args.duration, 1000000, 1, 0))
    _, labels, data = dyntools.CHNF('native.out').get_data()
    keys = sorted(k for k in labels if isinstance(k, int))
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    assert len(data['time']) < 50000
    assert all(a <= b for a, b in zip(data['time'], data['time'][1:]))
    with Path('native.csv').open('w', newline='') as stream:
        writer = csv.writer(stream, lineterminator='\n'); writer.writerow(['time_s']+[labels[k] for k in keys])
        for i, time in enumerate(data['time']):
            writer.writerow([format(time, '.12g')]+[format(data[k][i], '.12g') for k in keys])
    assert Path('native.csv').stat().st_size < 16*1024**2
    checked('model report', psspy.docu(0, 1, [0, 3, 1]))
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and all(t not in log for t in ['Network not converged', 'Incomplete data record'])
    manifest = dict(status='complete' if quality else 'rejected-quality', settings=vars(args), layout=layout, machine_layouts=machine_layouts,
            entered_constants=entered, initialized_constants=initialized, samples=len(data['time']), channels=len(keys),
            engine_module=psse3607.__name__, library_sha256=sha(library),
            source_document_sha256=sha(library.parent.parent/'DOCS/ModelLibrary.pdf'),
            files={p.name: sha(p) for p in Path('.').iterdir() if p.is_file()})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(dict(tag=tag, status=manifest['status'])), flush=True)
    assert quality


if __name__ == '__main__':
    main()
