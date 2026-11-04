"""Cold small-network references for dynamic-model qualification."""
import argparse
import csv
import json
import math
import os
import re
from pathlib import Path
import shutil
from tgov1_limit_probe import checked, sha


def load_fixture(name):
    return name.startswith('cmldblu1') or name in ['cmldznu2', 'acmtblu1']


def load_contract(name):
    if name == 'acmtblu1': return [35, 9, 29, 0]
    return [133, 27, 146, 2] if name == 'cmldznu2' else [132, 27, 146, 0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--fixture', required=True, choices=[
        'pss2b', 'pss2b-remote', 'hyg3u1-0', 'hyg3u1-1', 'hyg3u1-deadband', 'hyg3u1-deadband-zero', 'hyg3u1-asymmetric', 'hyg3u1-partial',
        'wsieg1', 'wsieg1-curve', 'wsieg1-dual', 'wsieg1-unused-lp', 'cmldblu1', 'cmldblu1-mixed', 'cmldblu1-electronic', 'cmldblu1-restart', 'cmldznu2', 'acmtblu1', 'tiocr1-0', 'tiocr1-1', 'tiocr1-current', 'tiocr1-load-only'])
    parser.add_argument('--profile', choices=['flat', 'fault', 'repeated-faults', 'short-repeated-faults', 'reference-step',
                                            'tap-reference-up', 'tap-reference-down',
                                            'tap-reference-reversal', 'tap-reference-deadband'], default='fault')
    parser.add_argument('--fault-x-pu', type=float, default=.2,
                        help='Finite positive fault reactance on system base')
    parser.add_argument('--second-fault-x-pu', type=float,
                        help='Optional separate reactance for a repeated-fault discriminator')
    parser.add_argument('--step', type=float, choices=[.0005, .00025, 1 / 240], default=.0005)
    parser.add_argument('--end-time', type=float, default=.5,
                        help='Final simulation time, from 0.5 through 10 seconds')
    parser.add_argument('--network-tolerance', type=float,
                        help='Optional explicit network convergence tolerance for a diagnostic control')
    parser.add_argument('--channel-part', type=int, choices=[0,1], default=0)
    parser.add_argument('--generator-states', action='store_true',
                        help='Record the coupled GENROU electrical and mechanical states in load-model diagnostics')
    parser.add_argument('--clear-diagnostic-storage', action='store_true',
                        help='Clear allocated load diagnostic storage before initialization; retain unmodified captures separately')
    parser.add_argument('--constant', action='append', default=[], metavar='INDEX=VALUE',
                        help='Controlled load-model discriminator; preserve original and modified DYR inputs')
    parser.add_argument('--relay-end', choices=['from', 'to'], default='from',
                        help='Monitored terminal of the synthetic relay branch')
    parser.add_argument('--relay-line-charging', type=float, default=0.,
                        help='Total charging susceptance of the monitored synthetic branch, pu')
    parser.add_argument('--batch', default='hsp-model-references-qualified-20261030')
    parser.add_argument('--repeat', choices=['a', 'b'], required=True)
    args = parser.parse_args()
    if (args.relay_end != 'from' or args.relay_line_charging != 0.) and not args.fixture.startswith('tiocr1-'):
        parser.error('Relay terminal controls require a relay fixture')
    if not math.isfinite(args.relay_line_charging) or not 0. <= args.relay_line_charging <= 1.:
        parser.error('--relay-line-charging must be finite and between zero and one pu')
    if args.network_tolerance is not None and (not math.isfinite(args.network_tolerance) or args.network_tolerance <= 0):
        parser.error('--network-tolerance must be finite and positive')
    if args.profile.startswith('tap-reference-') and not args.fixture.startswith('cmldblu1'):
        parser.error('Tap-reference profiles require a legacy bus composite fixture')
    if args.profile == 'short-repeated-faults' and not args.fixture.startswith('tiocr1-'):
        parser.error('The short reset/re-pickup profile requires a relay fixture')
    if not math.isfinite(args.end_time) or not .5 <= args.end_time <= 10:
        parser.error('--end-time must be finite and between 0.5 and 10 seconds')
    if not math.isfinite(args.fault_x_pu) or args.fault_x_pu <= 0:
        parser.error('--fault-x-pu must be finite and positive')
    if args.second_fault_x_pu is not None and (
            args.profile not in ['repeated-faults', 'short-repeated-faults'] or not math.isfinite(args.second_fault_x_pu)
            or args.second_fault_x_pu <= 0):
        parser.error('--second-fault-x-pu requires repeated-faults and a finite positive value')
    root = Path.cwd()
    source = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/HspModels'
    out = root / 'target' / args.batch / (
        f'{args.fixture}-{args.profile}-h{args.step:g}-{args.repeat}'+(f'-part{args.channel_part}' if load_fixture(args.fixture) else '')
        +(f'-{args.relay_end}-b{args.relay_line_charging:g}' if args.relay_end != 'from' or args.relay_line_charging != 0. else ''))
    out.mkdir(parents=True, exist_ok=False)
    load = load_fixture(args.fixture)
    if args.fixture == 'acmtblu1' and args.channel_part != 0:
        parser.error('The standalone compressor fits in one complete channel part')
    if args.clear_diagnostic_storage and not load:
        parser.error('--clear-diagnostic-storage is restricted to load-model probes')
    if args.constant and not load:
        parser.error('--constant is restricted to load-model probes')
    if args.generator_states and not load:
        parser.error('--generator-states is restricted to the reduced load-model fixture')
    relay = args.fixture.startswith('tiocr1')
    shutil.copy2(source / ('load-network.raw' if load else 'dual-network.raw' if args.fixture=='wsieg1-dual' else 'network.raw'), out / 'network.raw')
    dynamic_source = (root / 'ipss.test.plugin.core/testData/reference-fixtures/compressor-current/acmtblu1.dyr'
                      if args.fixture == 'acmtblu1' else source / (args.fixture + '.dyr'))
    shutil.copy2(dynamic_source, out / 'dynamic.dyr')
    relay_from, relay_to = (1, 2) if args.relay_end == 'from' else (2, 1)
    if relay and (args.relay_end != 'from' or args.relay_line_charging != 0.):
        shutil.copy2(out / 'network.raw', out / 'original-network.raw')
        shutil.copy2(out / 'dynamic.dyr', out / 'original-dynamic.dyr')
        path = out / 'dynamic.dyr'
        lines = path.read_text().splitlines()
        changed = 0
        for index, line in enumerate(lines):
            fields = re.findall(r"'[^']*'|[^\s,]+", line)
            if len(fields) > 2 and fields[1] == "'TIOCR1'":
                assert fields[0] == '1' and fields[2] == '2'
                fields[0], fields[2] = str(relay_from), str(relay_to)
                lines[index] = ' '.join(fields)
                changed += 1
        assert changed == 1
        path.write_text('\n'.join(lines) + '\n')
        path = out / 'network.raw'
        lines = path.read_text().splitlines()
        changed = 0
        for index, line in enumerate(lines):
            fields = line.split(',')
            if len(fields) == 18 and fields[:3] == ['1', '2', "'1'"]:
                assert float(fields[5]) == 0.
                fields[5] = format(args.relay_line_charging, '.17g')
                lines[index] = ','.join(fields)
                changed += 1
        assert changed == 1
        path.write_text('\n'.join(lines) + '\n')
    if args.constant:
        shutil.copy2(out / 'dynamic.dyr', out / 'original.dyr')
        path = out / 'dynamic.dyr'
        text = path.read_text()
        model = 'ACMTBLU1' if args.fixture == 'acmtblu1' else 'CMLDZNU2' if args.fixture == 'cmldznu2' else 'CMLDBLU1'
        match = re.search(r"(?ms)^\s*\d+\s+'USRLOD'\s+'[^']+'\s+'" + model + r"'.*?/", text)
        if match is None: parser.error('Expected the exact synthetic load record')
        fields = re.findall(r"'[^']*'|[^\s,]+", match.group()[:-1])
        count, states, variables, icons = load_contract(args.fixture)
        reserved = 16 if model == 'ACMTBLU1' else 48
        start = 11 + icons
        assert len(fields) == start + count and fields[6:11] == [str(icons), str(count), str(states), str(variables), str(reserved)]
        seen = set()
        for item in args.constant:
            index_text, value_text = item.split('=', 1)
            index, value = int(index_text), float(value_text)
            if not 0 <= index < count or index in seen or not math.isfinite(value):
                parser.error('Constants require unique valid indices and finite values')
            seen.add(index)
            fields[start + index] = format(value, '.17g')
        replacement = ' '.join(fields[:start]) + '\n' + '\n'.join(
            ' '.join(fields[i:i+8]) for i in range(start, len(fields), 8)) + ' /'
        path.write_text(text[:match.start()] + replacement + text[match.end():])
    for file in ['hsp_model_reference_probe.py', 'tgov1_limit_probe.py']:
        shutil.copy2(root / 'tools' / file, out / file)
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
    conversion=[100.,0.,0.,100.] if args.fixture in ('tiocr1-current','tiocr1-load-only') else [0.,100.,0.,100.]
    checked('generators', psspy.cong(0))
    for stage in [1, 2, 3]:
        checked('loads', psspy.conl(0, 1, stage, [0, 0], conversion))
    checked('order', psspy.ordr(0)); checked('factor', psspy.fact())
    checked('converted', psspy.tysl(0))
    checked('dynamic', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    if relay:
        index = lambda kind: checked('relay index', psspy.rmodind(relay_from, relay_to, '1', 1, kind))
    elif load:
        index = lambda kind: checked('load index', psspy.lmodind(2, '1', 'CHARAC', kind))
    else:
        category = 'STAB' if args.fixture.startswith('pss') else 'GOV'
        index = lambda kind: checked('controller index', psspy.mdlind(1, '1', category, kind))
    expected = [14,0,3,15] if relay else load_contract(args.fixture) if load else [23,17,4,6] if args.fixture.startswith('pss') else [34,6,8,0] if args.fixture.startswith('wsieg') else [36,9,5,2]
    layout = {kind:index(kind) if count else 0 for kind,count in zip(['NCON','NSTATE','NVAR','NICON'],expected)}
    assert list(layout.values()) == expected, layout
    layout.update({kind:index(kind) if layout['N'+kind] else 0 for kind in ['CON','STATE','VAR','ICON']})
    if args.fixture.startswith('pss') or args.clear_diagnostic_storage:
        for i in range(layout['NVAR']):
            checked('initialize diagnostic storage',psspy.change_var(layout['VAR']+i,0.0))
    entered = [checked('constant', psspy.dsrval('CON', layout['CON'] + i)) for i in range(layout['NCON'])]
    for kind, fn in [('STATE', psspy.state_channel), ('VAR', psspy.var_channel)]:
        for i in range(layout['N' + kind]):
            if load and kind == 'VAR' and (i // 73) != args.channel_part: continue
            checked('channel', fn([-1, layout[kind] + i], kind + str(i)))
    for code, name in [(1, 'ANGLE_DEG'), (2, 'P_PU'), (3, 'Q_PU'), (6, 'PMECH'), (7, 'SPEED'), (10,'STABILIZER')]:
        checked('machine', psspy.machine_array_channel([-1, code, 1], '1', name))
    generator_layout = None
    if args.generator_states:
        generator_layout = {kind:checked('generator state allocation',psspy.mdlind(1, '1', 'GEN', kind))
                            for kind in ['STATE', 'NSTATE']}
        assert generator_layout['NSTATE'] == 6, generator_layout
        for i in range(generator_layout['NSTATE']):
            checked('generator state', psspy.state_channel([-1, generator_layout['STATE'] + i], 'GEN_STATE' + str(i)))
        for code, name in [(5, 'GEN_EFD'), (8, 'GEN_XADIFD')]:
            checked('generator field', psspy.machine_array_channel([-1, code, 1], '1', name))
    if args.fixture=='wsieg1-dual':
        for code,name in [(6,'PMECH_LP'),(7,'SPEED_LP')]:
            checked('secondary machine',psspy.machine_array_channel([-1,code,2],'1',name))
    for bus in ([1, 2, 3] if load or args.fixture=='wsieg1-dual' else [1, 2]):
        checked('voltage', psspy.voltage_and_angle_channel([-1, -1, -1, bus], [f'V{bus}', f'A{bus}']))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('step', psspy.dynamics_solution_param_2([di]*8,
        [df, df if args.network_tolerance is None else args.network_tolerance, args.step] + [df]*5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    initialized = [checked('constant', psspy.dsrval('CON', layout['CON'] + i)) for i in range(layout['NCON'])]
    checked('prefault', psspy.run(0, .05, 1000000, 1, 0))
    if args.profile in ['fault', 'repeated-faults', 'short-repeated-faults']:
        checked('fault', psspy.dist_bus_fault(2 if load else 1, 3, 230.0 if load or args.fixture=='wsieg1-dual' else 100.0, [0., args.fault_x_pu*(230.0 if load or args.fixture=='wsieg1-dual' else 100.0)**2/100.0]))
    if args.profile == 'reference-step':
        checked('reference', psspy.increment_gref(1, '1', .03))
    def change_tap_target(time_s, delta):
        event = dict(time_s=time_s, delta_pu=delta, before={}, after={})
        for offset in [12, 13]:
            before = checked('tap target before', psspy.dsrval('CON', layout['CON'] + offset))
            checked('tap target change', psspy.change_ldmod_con(2, '1', 'CMLDBLU1', offset + 1, before + delta))
            after = checked('tap target after', psspy.dsrval('CON', layout['CON'] + offset))
            assert abs(after - (before + delta)) < 2e-7
            event['before'][str(offset)] = before
            event['after'][str(offset)] = after
        return event
    tap_reference_events = []
    if args.profile.startswith('tap-reference-'):
        delta = -.1 if args.profile == 'tap-reference-down' else .1
        tap_reference_events.append(change_tap_target(.05, delta))
    first_clear_time = .06 if args.profile == 'short-repeated-faults' else .10
    checked('during', psspy.run(0, first_clear_time, 1000000, 1, 0))
    if args.profile == 'tap-reference-reversal':
        tap_reference_events.append(change_tap_target(.10, -.2))
    if args.profile == 'tap-reference-deadband':
        tap_reference_events.append(change_tap_target(.10, -.085))
    if args.profile in ['fault', 'repeated-faults', 'short-repeated-faults']: checked('clear', psspy.dist_clear_fault(1))
    if args.profile in ['repeated-faults', 'short-repeated-faults']:
        checked('recovery', psspy.run(0, .20, 1000000, 1, 0))
        checked('second fault', psspy.dist_bus_fault(2 if load else 1, 3,
            230.0 if load or args.fixture=='wsieg1-dual' else 100.0,
            [0., (args.second_fault_x_pu if args.second_fault_x_pu is not None else args.fault_x_pu)
             *(230.0 if load or args.fixture=='wsieg1-dual' else 100.0)**2/100.0]))
        checked('second fault on', psspy.run(0, .25, 1000000, 1, 0))
        checked('second clear', psspy.dist_clear_fault(1))
    checked('postfault', psspy.run(0, args.end_time, 1000000, 1, 0))
    _, labels, data = dyntools.CHNF('native.out').get_data()
    keys = sorted(k for k in labels if isinstance(k, int))
    assert all(math.isfinite(v) for k in ['time'] + keys for v in data[k])
    with Path('native.csv').open('w', newline='') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['time_s'] + [labels[k] for k in keys])
        for i, time in enumerate(data['time']):
            writer.writerow([format(time, '.12g')] + [format(data[k][i], '.12g') for k in keys])
    checked('model report', psspy.docu(0, 1, [0, 3, 1]))
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and all(message not in log for message in ['Network not converged','Incomplete data record'])
    input_consistency = None
    if args.fixture == 'acmtblu1':
        channels = {labels[k]: k for k in keys}
        voltage_error = max(abs(a - b) for name in ['VAR0', 'VAR13']
                            for a, b in zip(data[channels[name]], data[channels['V2']]))
        input_consistency = dict(maximum_voltage_input_error_pu=voltage_error, tolerance_pu=2e-7)
        quality = quality and voltage_error <= 2e-7
    manifest = dict(status='complete' if quality else 'rejected-quality', settings=vars(args),
        tap_reference_events=tap_reference_events,
        input_consistency=input_consistency,
        layout=layout, generator_layout=generator_layout, entered_constants=entered, initialized_constants=initialized,
        load_conversion_percent=conversion, fault_impedance_pu=[0.0,args.fault_x_pu],
        first_fault_clear_time_s=first_clear_time if args.profile in ['fault', 'repeated-faults', 'short-repeated-faults'] else None,
        second_fault_impedance_pu=([0.0, args.second_fault_x_pu if args.second_fault_x_pu is not None else args.fault_x_pu]
                                   if args.profile in ['repeated-faults', 'short-repeated-faults'] else None),
        samples=len(data['time']), channels=len(keys), engine_module=psse3607.__name__,
        library_sha256=sha(library), source_document_sha256=sha(library.parent.parent/'DOCS/ModelLibrary.pdf'),
        files={p.name:sha(p) for p in Path('.').iterdir() if p.is_file()})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(dict(fixture=args.fixture, status=manifest['status'], layout=layout)), flush=True)
    assert quality


if __name__ == '__main__':
    main()
