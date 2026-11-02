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
    parser.add_argument('--static-gamma', type=float)
    parser.add_argument('--profile', choices=['flat', 'rectifier-fault', 'inverter-fault', 'modulation'], default='flat')
    parser.add_argument('--flags', nargs=3, type=int, choices=[0, 1], default=[0, 0, 0])
    parser.add_argument('--step', type=float, choices=[.0005, .00025, 1/240], default=.0005)
    parser.add_argument('--repeat', choices=['a', 'b'], required=True)
    parser.add_argument('--batch', default='chvdc2-initial-20261102')
    args = parser.parse_args()
    if args.static_gamma is not None and (not math.isfinite(args.static_gamma) or not 0 < args.static_gamma < 90):
        parser.error('Static gamma must be finite and strictly between zero and ninety degrees')
    if not args.batch.replace('-', '').isalnum():
        parser.error('Batch must contain only letters, digits and hyphens')
    root = Path.cwd()
    tag = f'{args.form}-{args.profile}-flags'+''.join(map(str, args.flags))+f'-h{args.step:.12g}-{args.repeat}'
    if args.control != 'inverter-power':
        tag = tag[:-1]+args.control+'-'+args.repeat
    if args.network == 'stiff':
        tag = 'stiff-'+tag
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
    (out/'dynamic.dyr').write_text(''.join(f"{bus} 'GENCLS' '1' 99999 0 /\n" for bus in range(1, 5))+
                                 header+' '.join(map(str, args.flags+CONSTANTS))+' /\n')
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
    checked('load flow', psspy.fnsl([0, 0, 0, 1, 0, 0, 99, 0])); assert psspy.solved() == 0
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
    checked('step', psspy.dynamics_solution_param_2([di]*8, [df, 1e-6, args.step]+[df]*5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    initialized = [checked('constant', psspy.dsrval('CON', layout['CON']+i)) for i in range(48)]
    checked('before event', psspy.run(0, .05, 1000000, 1, 0))
    if args.profile.endswith('fault'):
        bus = 7 if args.profile.startswith('rectifier') else 9
        checked('fault', psspy.dist_bus_fault(bus, 3, 230., [0., .2*230**2/100]))
    elif args.profile == 'modulation':
        checked('modulation', psspy.change_var(layout['VAR'], 10.))
    checked('during event', psspy.run(0, .1, 1000000, 1, 0))
    if args.profile.endswith('fault'):
        checked('clear', psspy.dist_clear_fault(1))
    elif args.profile == 'modulation':
        checked('release modulation', psspy.change_var(layout['VAR'], 0.))
    checked('finish', psspy.run(0, .5, 1000000, 1, 0))
    _, labels, data = dyntools.CHNF('native.out').get_data()
    keys = sorted(k for k in labels if isinstance(k, int))
    assert len(data['time']) < 5000 and all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w', newline='') as f:
        w = csv.writer(f, lineterminator='\n'); w.writerow(['time_s']+[labels[k] for k in keys])
        for i, t in enumerate(data['time']):
            w.writerow([format(t, '.12g')]+[format(data[k][i], '.12g') for k in keys])
    assert Path('native.csv').stat().st_size < 16*1024**2
    checked('report', psspy.docu(0, 1, [0, 3, 1]))
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    manifest = dict(status='complete' if quality else 'rejected-quality', settings=vars(args), layout=layout,
                    entered_constants=entered, initialized_constants=initialized, samples=len(data['time']), channels=len(keys),
                    engine_module=psse3607.__name__, library_sha256=sha(library),
                    source_document_sha256=sha(library.parent.parent/'DOCS/ModelLibrary.pdf'),
                    files={p.name: sha(p) for p in Path('.').iterdir() if p.is_file()})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(dict(tag=out.name, status=manifest['status'])), flush=True)
    assert quality


if __name__ == '__main__':
    main()
