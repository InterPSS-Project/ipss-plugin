"""Cold synthetic extended-steam-governor captures with explicit shaft events."""
import argparse
import csv
import json
import math
import os
from pathlib import Path
import shutil

from tgov1_limit_probe import checked, sha

PROFILES = {
    'base': {},
    'speed-bypass': {1: 0., 2: 0.},
    'first-turbine-bypass': {8: 0.},
    'all-turbines-bypass': {8: 0., 11: 0., 14: 0., 17: 0.},
    'valve-rates': {4: .003, 5: -.002},
    'valve-position': {6: .505, 7: .495},
    'initial-lower-block': {7: 0., 33: 1.},
    'initial-upper-block': {6: 0., 33: 2.},
    'initial-both-block': {6: 0., 7: 0., 33: 3.},
    'initial-upper-expansion': {6: .2, 7: 0.},
    'initial-lower-expansion': {6: 1.2, 7: .7},
    'initial-lower-block-conflict': {6: .2, 7: 0., 33: 1.},
    'initial-upper-block-conflict': {6: 0., 7: .7, 33: 2.},
    'initial-upper-bypass': {6: .2, 7: 0., 8: 0., 11: 0., 14: 0., 17: 0.},
    'initial-lower-bypass': {6: 1.2, 7: .7, 8: 0., 11: 0., 14: 0., 17: 0.},
}
STEP_PROFILES = {
    'speed-one-step': {1: 1., 2: .25},
    'speed-half-step': {1: .5, 2: .125},
    'speed-two-steps': {1: 2., 2: .5},
    'speed-four-steps': {1: 4., 2: 1.},
    'speed-above-one-step': {1: 1.01, 2: .2525},
    'speed-above-two-steps': {1: 2.01, 2: .5025},
    'speed-fast-lag-slow-lead': {1: 1.},
    'speed-short-no-lead': {1: 1., 2: 0.},
    'valve-one-step': {3: 1.},
    'valve-half-step': {3: .5},
    'turbine-one-step': {8: 1.},
    'turbine-half-step': {8: .5},
    'turbine-two-steps': {8: 2.},
    'turbine-four-steps': {8: 4.},
    'all-turbines-two-steps': {8: 2., 11: 2., 14: 2., 17: 2.},
    'all-turbines-above-two-steps': {8: 2.01, 11: 2.01, 14: 2.01, 17: 2.01},
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--fixture', choices=['standard', 'curve', 'dual', 'unused-lp'], required=True)
    parser.add_argument('--profile', choices=[*PROFILES, *STEP_PROFILES], default='base')
    parser.add_argument('--base-profile', choices=['entered','primary-high','secondary-high','split-fractions','system-double','independent-secondary','zero-secondary','zero-primary','zero-both','secondary-off','balanced-zero-primary','balanced-zero-both'], default='entered')
    parser.add_argument('--event', choices=['flat', 'fault', 'primary-trip', 'secondary-trip', 'reference-reversals'], required=True)
    parser.add_argument('--step', type=float, choices=[.0005, .00025, 1/240], required=True)
    parser.add_argument('--duration', type=float, choices=[.5, 1., 5.], default=.5)
    parser.add_argument('--repeat', choices=['a', 'b'], required=True)
    parser.add_argument('--pretrip-reference', type=float, choices=[0., .1], default=0.)
    parser.add_argument('--batch', default='wsieg1-shared-qualification-20261031')
    args = parser.parse_args()
    if args.event == 'secondary-trip' and args.fixture != 'dual':
        parser.error('A secondary trip requires the dual-shaft fixture')
    if args.base_profile != 'entered' and (args.fixture != 'dual' or args.profile != 'base'):
        parser.error('Base controls require the unmodified dual-shaft fixture')
    if args.event == 'reference-reversals' and args.duration < 1.:
        parser.error('The complete reference-reversal program requires at least one second')
    if args.pretrip_reference and not args.event.endswith('-trip'):
        parser.error('Pretrip excitation requires a shaft-trip profile')
    root = Path.cwd()
    source = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/HspModels'
    excitation = f'-r{args.pretrip_reference:g}' if args.pretrip_reference else ''
    tag = f'{args.fixture}-{args.event}{excitation}-h{args.step:.12g}-t{args.duration:g}-{args.repeat}'.replace('.', 'p')
    if args.profile != 'base':
        tag = args.profile + '-' + tag
    if args.base_profile != 'entered':
        tag = args.base_profile + '-' + tag
    out = root / 'target' / args.batch / tag
    if shutil.disk_usage(out.parent.parent).free < 2*1024**3 + 64*1024**2:
        raise OSError('Capture requires the free-space reserve and bounded export budget')
    out.mkdir(parents=True, exist_ok=False)
    fixture = 'wsieg1' if args.fixture == 'standard' else 'wsieg1-' + args.fixture
    shutil.copy2(source / ('dual-network.raw' if args.fixture == 'dual' else 'network.raw'), out / 'network.raw')
    shutil.copy2(source / (fixture + '.dyr'), out / 'dynamic.dyr')
    if args.base_profile != 'entered':
        lines = (out/'network.raw').read_text().splitlines(); section = 'header'
        for i,line in enumerate(lines):
            if i == 2: section = 'bus'; continue
            if 'BEGIN LOAD DATA' in line: section = 'load'; continue
            if 'BEGIN GENERATOR DATA' in line: section = 'generator'; continue
            if 'BEGIN BRANCH DATA' in line: section = 'branch'; continue
            if line.startswith('0 /'): section = 'other'; continue
            if i == 0 and args.base_profile == 'system-double':
                fields = line.split(','); fields[1] = ' 200.00'; lines[i] = ','.join(fields)
            elif section == 'bus' and args.base_profile.startswith('balanced-zero-'):
                fields = line.split(','); fields[7] = '1.0'; fields[8] = '0.0'; lines[i] = ','.join(fields)
            elif section == 'load' and args.base_profile.startswith('balanced-zero-'):
                fields = line.split(','); fields[5] = '12.0' if args.base_profile == 'balanced-zero-primary' else '0.0'; fields[6] = '0.0'; lines[i] = ','.join(fields)
            elif section == 'generator':
                fields = line.split(','); bus = int(fields[0])
                ratings = {'primary-high': {1:200.,2:50.}, 'secondary-high': {1:50.,2:200.}, 'split-fractions': {1:80.,2:120.}}.get(args.base_profile,{})
                if bus in ratings: fields[8] = str(ratings[bus])
                if args.base_profile == 'split-fractions' and bus == 2: fields[2] = '16.0'
                if args.base_profile == 'independent-secondary' and bus == 2: fields[2] = '18.0'
                if args.base_profile == 'zero-secondary' and bus == 2: fields[2] = '0.0'
                if args.base_profile == 'zero-primary' and bus == 1: fields[2] = '0.0'
                if args.base_profile == 'zero-both' and bus in [1,2]: fields[2] = '0.0'
                if args.base_profile == 'secondary-off' and bus == 2: fields[14] = '0'
                if args.base_profile.startswith('balanced-zero-'):
                    fields[2] = '12.0' if bus == 2 and args.base_profile == 'balanced-zero-primary' else '0.0'
                    fields[3] = '0.0'; fields[6] = '1.0'
                lines[i] = ','.join(fields)
            elif section == 'branch' and args.base_profile == 'system-double':
                fields = line.split(',')
                for k in [3,4]: fields[k] = str(2.*float(fields[k]))
                for k in [5,9,10,11,12]: fields[k] = str(float(fields[k])/2.)
                lines[i] = ','.join(fields)
        (out/'network.raw').write_text('\n'.join(lines)+'\n')
        if args.base_profile == 'split-fractions':
            lines = (out/'dynamic.dyr').read_text().splitlines()
            i = next(i for i,line in enumerate(lines) if "'WSIEG1'" in line); tokens = lines[i].split()
            values = [float(v) for v in tokens[5:-1]]
            for k,v in {9:.2,12:.15,15:.25,18:0.,10:.05,13:.25,16:.1,19:0.}.items(): values[k] = v
            lines[i] = ' '.join(tokens[:5])+' '+' '.join(format(v,'.17g') for v in values)+' /'
            (out/'dynamic.dyr').write_text('\n'.join(lines)+'\n')
    if args.profile != 'base':
        lines = (out / 'dynamic.dyr').read_text().splitlines()
        index = next(i for i, line in enumerate(lines) if "'WSIEG1'" in line)
        tokens = lines[index].split()
        values = [float(v) for v in tokens[5:-1]]
        assert len(values) == 34 and tokens[-1] == '/'
        changes = PROFILES[args.profile] if args.profile in PROFILES else {
            parameter: multiple * args.step for parameter, multiple in STEP_PROFILES[args.profile].items()}
        for parameter, value in changes.items():
            values[parameter] = value
        lines[index] = ' '.join(tokens[:5]) + ' ' + ' '.join(format(v, '.17g') for v in values) + ' /'
        (out / 'dynamic.dyr').write_text('\n'.join(lines) + '\n')
    for name in ['wsieg1_reference_probe.py', 'tgov1_limit_probe.py']:
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
    checked('flow', psspy.fnsl([0, 0, 0, 1, 1, 0, 99, 0])); assert psspy.solved() == 0
    checked('generators', psspy.cong(0))
    for stage in [1, 2, 3]:
        checked('loads', psspy.conl(0, 1, stage, [0, 0], [0., 100., 0., 100.]))
    checked('order', psspy.ordr(0)); checked('factor', psspy.fact()); checked('converted', psspy.tysl(0))
    checked('dynamic', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    index = lambda kind: checked('governor index', psspy.mdlind(1, '1', 'GOV', kind))
    layout = {kind: index(kind) for kind in ['CON', 'STATE', 'VAR', 'NCON', 'NSTATE', 'NVAR']}
    layout['NICON'] = 0
    assert [layout[k] for k in ['NCON', 'NSTATE', 'NVAR', 'NICON']] == [34, 6, 8, 0]
    # Initialize diagnostic storage before model initialization; some unused slots
    # otherwise expose arbitrary allocator contents in independent cold processes.
    for i in range(layout['NVAR']):
        checked('initialize diagnostic storage', psspy.change_var(layout['VAR']+i, 0.))
    entered = [checked('constant', psspy.dsrval('CON', layout['CON']+i)) for i in range(34)]
    for kind, fn in [('STATE', psspy.state_channel), ('VAR', psspy.var_channel)]:
        for i in range(layout['N'+kind]):
            checked('channel', fn([-1, layout[kind]+i], kind+str(i)))
    for bus, suffix in [(1, '')] + ([(2, '_LP')] if args.fixture == 'dual' else []):
        for code, name in [(1, 'ANGLE_DEG'), (2, 'P_PU'), (3, 'Q_PU'), (6, 'PMECH'), (7, 'SPEED')]:
            checked('machine', psspy.machine_array_channel([-1, code, bus], '1', name+suffix))
    for bus in ([1, 2, 3] if args.fixture == 'dual' else [1, 2]):
        checked('voltage', psspy.voltage_and_angle_channel([-1, -1, -1, bus], [f'V{bus}', f'A{bus}']))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('step', psspy.dynamics_solution_param_2([di]*8, [df, df, args.step]+[df]*5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    initialized = [checked('constant', psspy.dsrval('CON', layout['CON']+i)) for i in range(34)]
    initial_reference = checked('reference', psspy.dsrval('VAR', layout['VAR']))
    reference_events = [(.05, .1), (.3, -.2), (.6, .1)] if args.event == 'reference-reversals' else []
    if args.event == 'reference-reversals':
        for time, increment in reference_events:
            checked('reference boundary', psspy.run(0, time, 1000000, 1, 0))
            checked('reference increment', psspy.increment_gref(1, '1', increment))
    else:
        if args.pretrip_reference:
            reference_events = [(.025, args.pretrip_reference)]
            checked('pretrip excitation boundary', psspy.run(0, .025, 1000000, 1, 0))
            checked('pretrip reference increment', psspy.increment_gref(1, '1', args.pretrip_reference))
        checked('event boundary', psspy.run(0, .05, 1000000, 1, 0))
        if args.event == 'fault':
            kv = 230. if args.fixture == 'dual' else 100.
            checked('fault', psspy.dist_bus_fault(1, 3, kv, [0., .2*kv**2/100.]))
        elif args.event.endswith('-trip'):
            checked('shaft trip', psspy.dist_machine_trip(1 if args.event == 'primary-trip' else 2, '1'))
        checked('during', psspy.run(0, .1, 1000000, 1, 0))
        if args.event == 'fault': checked('clear', psspy.dist_clear_fault(1))
    checked('finish', psspy.run(0, args.duration, 1000000, 1, 0))
    _, labels, data = dyntools.CHNF('native.out').get_data()
    keys = sorted(k for k in labels if isinstance(k, int))
    assert 0 < len(data['time']) < 30000
    finite = all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    assert all(a <= b for a, b in zip(data['time'], data['time'][1:]))
    if finite:
        with Path('native.csv').open('w', newline='') as stream:
            writer = csv.writer(stream, lineterminator='\n'); writer.writerow(['time_s']+[labels[k] for k in keys])
            for i, time in enumerate(data['time']):
                writer.writerow([format(time, '.12g')]+[format(data[k][i], '.12g') for k in keys])
    checked('model report', psspy.docu(0, 1, [0, 3, 1]))
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and all(message not in log for message in ['Network not converged', 'Incomplete data record'])
    manifest = dict(status=('complete' if quality else 'rejected-quality') if finite else 'rejected-nonfinite', settings=vars(args), layout=layout,
        entered_constants=entered, initialized_constants=initialized, initial_reference=initial_reference,
        reference_events=reference_events, load_conversion_percent=[0., 100., 0., 100.], samples=len(data['time']),
        diagnostic_storage_initialization='All VAR slots zeroed before STRT; model initialization then owns their values',
        engine_module=psse3607.__name__, library_sha256=sha(library),
        source_document_sha256=sha(library.parent.parent/'DOCS/ModelLibrary.pdf'), source_pdf_pages=[522, 523, 524],
        files={p.name: sha(p) for p in Path('.').iterdir() if p.is_file()})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(dict(tag=tag, status=manifest['status'], samples=manifest['samples'])), flush=True)
    assert quality


if __name__ == '__main__':
    main()
