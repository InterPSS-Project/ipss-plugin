"""Cold synthetic named-link schema and controller-state captures."""
import argparse
import csv
import json
import math
import os
from pathlib import Path
import shutil

from tgov1_limit_probe import checked, sha

CONSTANTS = [50., 150., 90., 5., .0005, 50., 150., 110., .0005,
             .01, 5., .005, .01, .005, .02, -.05, .95, .01, 10., -10.,
             .3, 1.1, .4, .8, .05, .02, .1, .01, 3500., 500., 10., .9,
             .035, .5, -.5, 1.05, .85, .25, 90., 10., .85, .1, .9, .1,
             1500., 500., 500., 0.]


def stiff_source_network(original):
    """Synthetic two-terminal control with short AC connections and stiff classical sources."""
    dc = next(i for i, line in enumerate(original) if line.startswith('"1           ",'))
    lines = ['0,100,33,0,1,60', 'Synthetic stiff-source DC control', '']
    for bus in [1, 2, 3, 4, 7, 9]:
        kind = 3 if bus in [1, 3] else 2 if bus in [2, 4] else 1
        lines.append(f"{bus},'B{bus}',230,{kind},1,1,1,1.03,0,1.1,.9,1.1,.9")
    lines += ['0 / END BUS', '0 / END LOAD', '0 / END SHUNT']
    for bus in range(1, 5):
        lines.append(f"{bus},'1',0,0,9999,-9999,1.03,0,10000,0,.18,0,0,1,1,100,99999,-99999,1,1")
    lines.append('0 / END GENERATOR')
    for bus, end in [(1, 7), (2, 7), (3, 9), (4, 9)]:
        lines.append(f"{bus},{end},'1',0,.001,0,0,0,0,0,0,0,0,1,1,0,1,1")
    lines += ['0 / END BRANCH', '0 / END TRANSFORMER', '0 / END AREA']+original[dc:dc+3]
    lines += [line for line in original[dc+3:] if line.startswith('0 /') or line == 'Q']
    return lines


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--form', choices=['standard', 'legacy'], required=True)
    parser.add_argument('--control', choices=['inverter-power', 'current'], default='inverter-power')
    parser.add_argument('--network', choices=['coupled', 'stiff'], default='coupled')
    parser.add_argument('--controller-profile', choices=['nominal', 'angle-limited', 'rate-limited', 'zero-angle-rate-line', 'fixed-angle-line', 'dc-limit', 'ac-limit', 'zero-filters', 'raml', 'commutation'], default='nominal')
    parser.add_argument('--static-gamma', type=float)
    parser.add_argument('--dc-tap-adjustment', choices=['enabled', 'disabled'], default='enabled')
    parser.add_argument('--fault-reactance', type=float, default=.2, help='Fault reactance in pu on 230 kV / 100 MVA')
    parser.add_argument('--network-iterations', type=int, help='Explicit network iteration ceiling, at most 50')
    parser.add_argument('--commutation-duration', type=float, help='Explicit minimum short duration in seconds')
    parser.add_argument('--line-capacitance', type=float, help='Entered DC-line capacitance in microfarads')
    parser.add_argument('--measurement-time', type=float, help='Entered current/AC-voltage measurement lag in seconds')
    parser.add_argument('--boundary-probe', choices=['observations', 'split'], help='Read public state/time snapshots; split additionally pauses after the first event step')
    parser.add_argument('--event-refresh-count', type=int, choices=[0, 1, 2], default=0,
                        help='Diagnostic same-time RUN calls after each event API; ordinary captures use zero')
    parser.add_argument('--profile', choices=['flat', 'rectifier-fault', 'inverter-fault', 'modulation'], default='flat')
    parser.add_argument('--flags', nargs=3, type=int, choices=[0, 1], default=[0, 0, 0])
    parser.add_argument('--step', type=float, choices=[.0005, .00025, 1/240], default=.0005)
    parser.add_argument('--repeat', choices=['a', 'b'], required=True)
    parser.add_argument('--batch', default='chvdc2-initial-20261102')
    args = parser.parse_args()
    if args.static_gamma is not None and (not math.isfinite(args.static_gamma) or not 0 < args.static_gamma < 90):
        parser.error('Static gamma must be finite and strictly between zero and ninety degrees')
    if not math.isfinite(args.fault_reactance) or args.fault_reactance <= 0:
        parser.error('Fault reactance must be finite and positive')
    if args.network_iterations is not None and not 1 <= args.network_iterations <= 50:
        parser.error('Network iteration ceiling must be between one and fifty')
    if args.commutation_duration is not None and (not math.isfinite(args.commutation_duration) or args.commutation_duration < 0):
        parser.error('Commutation duration must be finite and nonnegative')
    if args.line_capacitance is not None and (not math.isfinite(args.line_capacitance) or args.line_capacitance < 0):
        parser.error('Line capacitance must be finite and nonnegative')
    if args.measurement_time is not None and (not math.isfinite(args.measurement_time) or args.measurement_time < 0):
        parser.error('Measurement time must be finite and nonnegative')
    if not args.batch.replace('-', '').isalnum():
        parser.error('Batch must contain only letters, digits and hyphens')
    root = Path.cwd()
    tag = f'{args.form}-{args.profile}-flags'+''.join(map(str, args.flags))+f'-h{args.step:.12g}-{args.repeat}'
    if args.control != 'inverter-power':
        tag = tag[:-1]+args.control+'-'+args.repeat
    if args.network == 'stiff':
        tag = 'stiff-'+tag
    if args.controller_profile != 'nominal':
        tag = tag[:-1]+args.controller_profile+'-'+args.repeat
    if args.static_gamma is not None:
        tag = tag[:-1]+f'gamma{args.static_gamma:g}-'+args.repeat
    if args.fault_reactance != .2:
        tag = tag[:-1]+f'faultx{args.fault_reactance:g}-'+args.repeat
    if args.network_iterations is not None:
        tag = tag[:-1]+f'netiter{args.network_iterations}-'+args.repeat
    if args.commutation_duration is not None:
        tag = tag[:-1]+f'cfhold{args.commutation_duration:g}-'+args.repeat
    if args.line_capacitance is not None:
        tag = tag[:-1]+f'cap{args.line_capacitance:g}-'+args.repeat
    if args.measurement_time is not None:
        tag = tag[:-1]+f'meas{args.measurement_time:g}-'+args.repeat
    if args.boundary_probe is not None:
        tag = tag[:-1]+args.boundary_probe+'-'+args.repeat
    if args.event_refresh_count:
        tag = tag[:-1]+f'refresh{args.event_refresh_count}-'+args.repeat
    if args.dc_tap_adjustment == 'disabled':
        tag = tag[:-1]+'fixed-dc-taps-'+args.repeat
    out = root/'target'/args.batch/tag.replace('.', 'p')
    if shutil.disk_usage(root/'target').free < 2*1024**3+64*1024**2:
        raise OSError('Free-space reserve and bounded capture budget required')
    out.mkdir(parents=True, exist_ok=False)
    source = root/'ipss.test.plugin.core/testData/adpter/psse/v33/Kundur_2area_LCC_HVDC_PsetOnInv.raw'
    lines = source.read_text().splitlines()
    changed = 0
    for index, line in enumerate(lines[3:], 3):
        if line.strip().startswith('0 /'):
            break
        fields = line.split(',')
        if int(fields[0]) == 8:
            assert fields[3].strip() == '1'
            fields[3] = '4'; lines[index] = ','.join(fields); changed += 1
    assert changed == 1
    if args.network == 'stiff':
        lines = stiff_source_network(lines)
    if args.control == 'current':
        i = next(i for i, line in enumerate(lines) if line.startswith('"1           ",1,'))
        fields = lines[i].split(','); fields[1] = '2'; fields[3] = '1000.0'
        lines[i] = ','.join(fields)
    if args.static_gamma is not None:
        i = next(i for i, line in enumerate(lines) if line.startswith('"1           ",'))
        fields = lines[i+2].split(',')
        fields[2] = fields[3] = str(args.static_gamma)
        lines[i+2] = ','.join(fields)
    (out/'network.raw').write_text('\n'.join(lines)+'\n')
    header = "'1' 'CHVDC2' " if args.form == 'standard' else "'1' 'USRDCL' 'CHVDC2U1' 18 1 3 48 18 24 "
    constants = CONSTANTS.copy()
    if args.controller_profile == 'angle-limited':
        constants[2], constants[3] = 32.74, 32.70
    elif args.controller_profile == 'rate-limited':
        constants[18], constants[19] = .0002, -.0002
    elif args.controller_profile in ['zero-angle-rate-line', 'fixed-angle-line']:
        # Isolate the DC-line update without changing its R/L/C coefficients.
        # Zero angle rates alone do not disable projection to a moving bound.
        # The nonpositive extinction threshold disables commutation failure.
        constants[18], constants[19], constants[30] = 0., 0., 0.
        if args.controller_profile == 'fixed-angle-line':
            constants[9], constants[12] = 0., 0.
    elif args.controller_profile == 'dc-limit':
        constants[20:24] = [.1, .4, .996, 1.001]
    elif args.controller_profile == 'ac-limit':
        if args.flags[0] != 1:
            parser.error('AC limiter profile requires the AC VDCOL flag')
        constants[35:38] = [.286, .1, .05]
        constants[42] = 1.03
    elif args.controller_profile == 'zero-filters':
        for index in [11, 13, 17, 25, 27]:
            constants[index] = 0.
    elif args.controller_profile == 'raml':
        if args.flags[1] != 1:
            parser.error('RAML profile requires the rectifier minimum-angle limiter flag')
        constants[38:42] = [40., .03, 1.025, .02]
    elif args.controller_profile == 'commutation':
        constants[30:33] = [31.4, 1.025, .035]
    if args.commutation_duration is not None:
        constants[32] = args.commutation_duration
    if args.line_capacitance is not None:
        constants[47] = args.line_capacitance
    if args.measurement_time is not None:
        constants[17] = args.measurement_time
    (out/'dynamic.dyr').write_text(''.join(f"{bus} 'GENCLS' '1' 99999 0 /\n" for bus in range(1, 5))+
                                 header+' '.join(map(str, args.flags+constants))+' /\n')
    for name in ['chvdc2_reference_probe.py', 'tgov1_limit_probe.py']:
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
    checked('load flow', psspy.fnsl([0, 0, 0, int(args.dc_tap_adjustment == 'enabled'), 0, 0, 99, 0])); assert psspy.solved() == 0
    equipment = {str(bus): {key: checked('equipment', psspy.dc2dat_2('1', bus, key))
                           for key in ['TAP', 'TR', 'EBASE', 'XC', 'ANGLE', 'KVDC', 'DCCUR']} for bus in [7, 9]}
    Path('solved-equipment.json').write_text(json.dumps(equipment, indent=2)+'\n')
    checked('generators', psspy.cong(0))
    for stage in [1, 2, 3]:
        checked('loads', psspy.conl(0, 1, stage, [0, 0], [0., 100., 0., 100.]))
    checked('order', psspy.ordr(0)); checked('factor', psspy.fact()); checked('converted', psspy.tysl(0))
    converted = {str(bus): {key: checked('converted equipment', psspy.dc2dat_2('1', bus, key))
                           for key in ['TAP', 'TR', 'EBASE', 'XC', 'ANGLE', 'KVDC', 'DCCUR']} for bus in [7, 9]}
    Path('converted-equipment.json').write_text(json.dumps(converted, indent=2)+'\n')
    checked('models', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    layout = {k: checked('layout', psspy.dc2mind('1', k)) for k in ['CON', 'ICON', 'STATE', 'VAR', 'NCON', 'NICON', 'NSTATE', 'NVAR']}
    Path('layout.json').write_text(json.dumps(layout, indent=2)+'\n')
    assert layout['NCON'] == 48 and layout['NICON'] == 3 and layout['NSTATE'] == 18 and layout['NVAR'] == 24, layout
    entered = [checked('constant', psspy.dsrval('CON', layout['CON']+i)) for i in range(48)]
    checked('entered report', psspy.docu(0, 1, [0, 3, 1]))
    for kind, fn in [('STATE', psspy.state_channel), ('VAR', psspy.var_channel)]:
        for i in range(layout['N'+kind]):
            checked('channel', fn([-1, layout[kind]+i], kind+str(i)))
    for bus in [7, 9]:
        checked('bus channel', psspy.voltage_and_angle_channel([-1, -1, -1, bus], [f'V{bus}', f'A{bus}']))
    for bus in range(1, 5):
        for code, label in [(1, 'ANGLE'), (2, 'P'), (3, 'Q'), (6, 'PMECH'), (7, 'SPEED')]:
            checked('machine channel', psspy.machine_array_channel([-1, code, bus], '1', f'M{bus}_{label}'))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('step', psspy.dynamics_solution_param_2([di if args.network_iterations is None else args.network_iterations]+[di]*7,
                                                [df, 1e-6, args.step]+[df]*5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    snapshots = []
    def snapshot(label):
        if args.boundary_probe is None and not args.event_refresh_count:
            return
        states = [checked('snapshot state', psspy.dsrval('STATE', layout['STATE']+i)) for i in range(18)]
        snapshots.append(dict(label=label, time_s=checked('snapshot time', psspy.dsrval('TIME', 0)),
                              states=states, derivatives_available=False,
                              variables=[checked('snapshot variable', psspy.dsrval('VAR', layout['VAR']+i)) for i in range(24)],
                              ac_voltage=[checked('snapshot voltage', psspy.busdat(bus, 'PU')) for bus in [7, 9]]))
        Path('boundary-snapshots.json').write_text(json.dumps(snapshots, indent=2)+'\n')
    snapshot('started')
    initialized = [checked('constant', psspy.dsrval('CON', layout['CON']+i)) for i in range(48)]
    checked('before event', psspy.run(0, .05, 1000000, 1, 0))
    snapshot('before event')
    if args.profile.endswith('fault'):
        bus = 7 if args.profile.startswith('rectifier') else 9
        checked('fault', psspy.dist_bus_fault(bus, 3, 230., [0., args.fault_reactance*230**2/100]))
    elif args.profile == 'modulation':
        checked('modulation', psspy.change_var(layout['VAR'], 10.))
    snapshot('event API returned')
    for refresh in range(args.event_refresh_count):
        current_time = checked('refresh time', psspy.dsrval('TIME', 0))
        checked('same-time event refresh', psspy.run(0, current_time, 1000000, 1, 0))
        snapshot(f'event same-time refresh {refresh+1}')
    if args.boundary_probe == 'split':
        checked('first event step', psspy.run(0, .05+args.step, 1000000, 1, 0))
        snapshot('first event step')
    checked('during event', psspy.run(0, .1, 1000000, 1, 0))
    snapshot('before clearing')
    if args.profile.endswith('fault'):
        checked('clear', psspy.dist_clear_fault(1))
    elif args.profile == 'modulation':
        checked('release modulation', psspy.change_var(layout['VAR'], 0.))
    snapshot('clearing API returned')
    for refresh in range(args.event_refresh_count):
        current_time = checked('refresh time', psspy.dsrval('TIME', 0))
        checked('same-time clearing refresh', psspy.run(0, current_time, 1000000, 1, 0))
        snapshot(f'clearing same-time refresh {refresh+1}')
    if args.boundary_probe == 'split':
        checked('first cleared step', psspy.run(0, .1+args.step, 1000000, 1, 0))
        snapshot('first cleared step')
    checked('finish', psspy.run(0, .5, 1000000, 1, 0))
    snapshot('finished')
    _, labels, data = dyntools.CHNF('native.out').get_data()
    keys = sorted(k for k in labels if isinstance(k, int))
    assert len(data['time']) < 5000
    nonfinite = [(i, 'time_s' if k == 'time' else labels[k])
                 for k in ['time']+keys for i, v in enumerate(data[k]) if not math.isfinite(v)]
    with Path('native.csv').open('w', newline='') as f:
        w = csv.writer(f, lineterminator='\n'); w.writerow(['time_s']+[labels[k] for k in keys])
        for i, t in enumerate(data['time']):
            w.writerow([format(t, '.12g')]+[format(data[k][i], '.12g') for k in keys])
    assert Path('native.csv').stat().st_size < 16*1024**2
    checked('report', psspy.docu(0, 1, [0, 3, 1]))
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    excitation = True
    if args.profile == 'modulation':
        channel = next(k for k in keys if labels[k] == 'VAR0')
        excitation = any(abs(v) > 1e-6 for v in data[channel])
    elif args.profile.endswith('fault'):
        channel = next(k for k in keys if labels[k] == ('V7' if args.profile.startswith('rectifier') else 'V9'))
        excitation = max(abs(v-data[channel][0]) for v in data[channel]) > 1e-5
    status = ('rejected-nonfinite' if nonfinite else 'rejected-quality' if not quality
              else 'complete' if excitation else 'rejected-excitation')
    manifest = dict(status=status, initialization_and_network_valid=quality, excitation_observed=excitation,
                    finite_measurements=not nonfinite, nonfinite_measurements=len(nonfinite),
                    first_nonfinite=min(nonfinite) if nonfinite else None,
                    settings=vars(args), layout=layout,
                    entered_constants=entered, initialized_constants=initialized, samples=len(data['time']), channels=len(keys),
                    engine_module=psse3607.__name__, library_sha256=sha(library),
                    source_document_sha256=sha(library.parent.parent/'DOCS/ModelLibrary.pdf'),
                    files={p.name: sha(p) for p in Path('.').iterdir() if p.is_file()})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(dict(tag=out.name, status=manifest['status'])), flush=True)
    assert quality and excitation and not nonfinite


if __name__ == '__main__':
    main()
