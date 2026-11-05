"""Cold synthetic hydro-governor captures for state-coordinate and integration qualification."""
import argparse
import csv
import json
import math
import os
from pathlib import Path
import shutil

from tgov1_limit_probe import checked, sha

PROFILES = {
    'standard': {},
    'gain-time-low': {4: .4, 6: .11, 7: .07},
    'gain-time-high': {4: 1.7, 6: .43, 7: .16},
    'valve-limits': {10: .003, 11: -.002},
    'gate-limits': {10: .1, 11: -.1, 12: .518, 13: .515},
    'backlash-small': {14: .02},
    'backlash-large': {14: 2.},
    'power-filter-bypass': {2: 0.},
    'input-filter-bypass': {3: 0.},
    'servo-bypass': {9: 0.},
    'water-flow-bypass': {29: 0.},
    'derivative-filter-zero': {7: 0.},
    'derivative-filter-fixed-small': {7: .002},
    'derivative-filter-two-steps': {},
    'derivative-filter-four-steps': {},
    'derivative-filter-one-step': {},
    'derivative-filter-half-step': {},
    'input-derivative-bypass': {3: 0., 7: 0.},
    'input-derivative-explicit': {3: 0.},
    'input-power-servo-derivative-bypass': {2: 0., 3: 0., 7: 0., 9: 0.},
}
REFERENCE_PROFILES = {'valve-limits', 'gate-limits', 'backlash-small', 'backlash-large'}
REFERENCE_PROGRAM = [( .05, .2), (.3, -.4), (.6, .2)]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', type=int, choices=[0, 1], required=True)
    parser.add_argument('--profile', choices=PROFILES, required=True)
    parser.add_argument('--step', type=float, choices=[.0005, .00025, 1/240, 1/480], required=True)
    parser.add_argument('--duration', type=float, choices=[.5, 1., 5., 10.], required=True)
    parser.add_argument('--repeat', choices=['a', 'b'], required=True)
    parser.add_argument('--batch', default='hyg3-state-qualification-20261031')
    args = parser.parse_args()
    reference_profile = args.profile in REFERENCE_PROFILES
    if reference_profile and args.duration < 1.:
        parser.error('Reference-reversal profiles require at least one simulated second')
    root = Path.cwd()
    source = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/HspModels'
    tag = f'm{args.mode}-{args.profile}-h{args.step:.12g}-t{args.duration:g}-{args.repeat}'.replace('.', 'p')
    out = root / 'target' / args.batch / tag
    if shutil.disk_usage(out.parent.parent).free < 2*1024**3 + 64*1024**2:
        raise OSError('Reference capture requires the free-space reserve and its bounded export budget')
    out.mkdir(parents=True, exist_ok=False)
    shutil.copy2(source / 'network.raw', out / 'network.raw')
    lines = (source / f'hyg3u1-{args.mode}.dyr').read_text().splitlines()
    tokens = lines[1].split()
    assert len(tokens) == 49 and tokens[-1] == '/' and tokens[3] == "'HYG3U1'"
    values = [float(v) for v in tokens[12:-1]]
    for index, value in PROFILES[args.profile].items():
        values[index] = value
    if args.profile == 'derivative-filter-two-steps':
        values[7] = 2 * args.step
    elif args.profile in {'derivative-filter-four-steps', 'input-derivative-explicit'}:
        values[7] = 4 * args.step
    elif args.profile == 'derivative-filter-one-step':
        values[7] = args.step
    elif args.profile == 'derivative-filter-half-step':
        values[7] = .5 * args.step
    lines[1] = f"1 'USRMDL' '1' 'HYG3U1' 5 0 2 36 9 5 {args.mode} 0 " + ' '.join(format(v, '.17g') for v in values) + ' /'
    (out / 'dynamic.dyr').write_text('\n'.join(lines) + '\n')
    for name in ['hyg3_reference_probe.py', 'tgov1_limit_probe.py']:
        shutil.copy2(root / 'tools' / name, out / name)

    import psse3607
    import psspy
    import dyntools
    os.chdir(out)
    checked('initialize', psspy.psseinit(50))
    for fn in [psspy.progress_output, psspy.report_output, psspy.alert_output, psspy.prompt_output]:
        checked('log', fn(2, 'native.log', [0, 0]))
    library = Path(psspy.__file__).resolve().parents[1] / 'PSSBIN/DSMDL.DLL'
    checked('library', psspy.addmodellibrary(str(library)))
    checked('network', psspy.read(0, 'network.raw'))
    checked('flow', psspy.fnsl([0, 0, 0, 1, 1, 0, 99, 0]))
    assert psspy.solved() == 0
    checked('generators', psspy.cong(0))
    for stage in [1, 2, 3]:
        checked('loads', psspy.conl(0, 1, stage, [0, 0], [0., 100., 0., 100.]))
    checked('order', psspy.ordr(0))
    checked('factor', psspy.fact())
    checked('converted', psspy.tysl(0))
    checked('dynamic', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    index = lambda kind: checked('controller index', psspy.mdlind(1, '1', 'GOV', kind))
    layout = {kind: index(kind) for kind in ['CON', 'STATE', 'VAR', 'ICON', 'NCON', 'NSTATE', 'NVAR', 'NICON']}
    assert [layout[k] for k in ['NCON', 'NSTATE', 'NVAR', 'NICON']] == [36, 9, 5, 2]
    entered = [checked('constant', psspy.dsrval('CON', layout['CON']+i)) for i in range(36)]
    for kind, fn in [('STATE', psspy.state_channel), ('VAR', psspy.var_channel)]:
        for i in range(layout['N'+kind]):
            checked('channel', fn([-1, layout[kind]+i], kind+str(i)))
    for code, name in [(1, 'ANGLE_DEG'), (2, 'P_PU'), (3, 'Q_PU'), (6, 'PMECH'), (7, 'SPEED')]:
        checked('machine', psspy.machine_array_channel([-1, code, 1], '1', name))
    for bus in [1, 2]:
        checked('voltage', psspy.voltage_and_angle_channel([-1, -1, -1, bus], [f'V{bus}', f'A{bus}']))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('step', psspy.dynamics_solution_param_2([di]*8, [df, df, args.step]+[df]*5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    initialized = [checked('constant', psspy.dsrval('CON', layout['CON']+i)) for i in range(36)]
    initial_reference = initialized[0]*checked('initial command', psspy.dsrval('VAR',layout['VAR'])) \
            + initialized[1]*checked('initial measured power', psspy.dsrval('STATE',layout['STATE']+5))
    if reference_profile:
        for time, increment in REFERENCE_PROGRAM:
            checked('reference boundary', psspy.run(0, time, 1000000, 1, 0))
            checked('reference increment', psspy.increment_gref(1, '1', increment))
        checked('after references', psspy.run(0, args.duration, 1000000, 1, 0))
    else:
        checked('prefault', psspy.run(0, .05, 1000000, 1, 0))
        checked('fault', psspy.dist_bus_fault(1, 3, 100., [0., .2*100.**2/100.]))
        checked('during', psspy.run(0, .10, 1000000, 1, 0))
        checked('clear', psspy.dist_clear_fault(1))
        checked('postfault', psspy.run(0, args.duration, 1000000, 1, 0))
    _, labels, data = dyntools.CHNF('native.out').get_data()
    keys = sorted(k for k in labels if isinstance(k, int))
    finite = all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    assert len(data['time']) < 30000
    assert all(a <= b for a, b in zip(data['time'], data['time'][1:]))
    assert sum(a == b for a, b in zip(data['time'], data['time'][1:])) >= 2
    if finite:
        with Path('native.csv').open('w', newline='') as stream:
            writer = csv.writer(stream, lineterminator='\n')
            writer.writerow(['time_s']+[labels[k] for k in keys])
            for i, time in enumerate(data['time']):
                writer.writerow([format(time, '.12g')]+[format(data[k][i], '.12g') for k in keys])
        assert Path('native.csv').stat().st_size < 16*1024**2
    checked('model report', psspy.docu(0, 1, [0, 3, 1]))
    log = Path('native.log').read_text(errors='replace')
    quality = finite and 'Initial conditions check O.K.' in log and all(message not in log for message in
            ['Network not converged', 'Incomplete data record'])
    status = 'rejected-nonfinite' if not finite else 'complete' if quality else 'rejected-quality'
    manifest = dict(status=status, finite_measurements=finite, settings=vars(args), layout=layout,
            entered_constants=entered, initialized_constants=initialized,
            load_conversion_percent=[0., 100., 0., 100.], fault_impedance_pu=None if reference_profile else [0., .2],
            event_kind='reference' if reference_profile else 'fault', reference_events=REFERENCE_PROGRAM if reference_profile else [],
            initial_reference_from_equilibrium=initial_reference,
            samples=len(data['time']), channels=len(keys), engine_module=psse3607.__name__,
            library_sha256=sha(library), source_document_sha256=sha(library.parent.parent/'DOCS/ModelLibrary.pdf'),
            files={p.name: sha(p) for p in Path('.').iterdir() if p.is_file()})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(dict(tag=tag, status=manifest['status'])), flush=True)
    assert quality


if __name__ == '__main__':
    main()
