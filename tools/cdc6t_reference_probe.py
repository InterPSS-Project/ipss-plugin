"""Cold small-network CDC6T initialization and disturbance evidence."""
import argparse
import csv
import json
import math
import os
from pathlib import Path
import shutil

from tgov1_limit_probe import checked, sha


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repeat', choices=['a', 'b'], required=True)
    parser.add_argument('--profile', choices=['flat', 'fault'], default='flat')
    parser.add_argument('--batch', default='cdc6t-reference-v2')
    args = parser.parse_args()
    root = Path.cwd()
    if not args.batch.replace('-', '').isalnum():
        parser.error('Batch must contain only letters, digits and hyphens')
    out = root / 'target' / f'{args.batch}-{args.profile}-{args.repeat}'
    out.mkdir(exist_ok=False)
    source = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/Kundur_2area_LCC_HVDC_PsetOnInv.raw'
    shutil.copy2(source, out / 'network.raw')
    shutil.copy2(source, out / 'source.raw')
    # The static fixture retains an isolated PQ bus. Mark it out of service for dynamics.
    lines = (out / 'network.raw').read_text().splitlines()
    changed = 0
    for index, line in enumerate(lines[3:], 3):
        if line.strip().startswith('0 /'):
            break
        fields = line.split(',')
        if int(fields[0]) == 8:
            assert fields[3].strip() == '1'
            fields[3] = '4'
            lines[index] = ','.join(fields)
            changed += 1
    assert changed == 1
    (out / 'network.raw').write_text('\n'.join(lines)+'\n')
    constants = [5, 20, .05, .05, .2, .3, .02, .2, .3, .02,
                 250, 500, 99, 99, 400, 250, 450, 500, 2000, 425, 2000,
                 .02, -999, 999, -999, 0, 0, 999, 999, 0, 999, .05]
    (out / 'dynamic.dyr').write_text(''.join(f"{bus} 'GENCLS' '1' 99999 0 /\n" for bus in range(1, 5))
                                    + "'1' 'CDC6T' " + ' '.join(map(str, constants)) + ' /\n')
    for name in ['cdc6t_reference_probe.py', 'tgov1_limit_probe.py']:
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
    checked('read', psspy.read(0, 'network.raw'))
    checked('flow', psspy.fnsl([0, 0, 0, 1, 0, 0, 99, 0]))
    assert psspy.solved() == 0
    checked('generators', psspy.cong(0))
    for stage in [1, 2, 3]:
        checked('loads', psspy.conl(0, 1, stage, [0, 0], [0., 100., 0., 100.]))
    checked('order', psspy.ordr(0))
    checked('factor', psspy.fact())
    checked('converted', psspy.tysl(0))
    checked('models', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    layout = {kind: checked('allocation', psspy.dc2mind('1', kind))
              for kind in ['CON', 'STATE', 'VAR', 'ICON', 'NCON', 'NSTATE', 'NVAR', 'NICON']}
    assert [layout[k] for k in ['NCON', 'NSTATE', 'NVAR', 'NICON']] == [32, 3, 18, 3], layout
    for kind, fn in [('STATE', psspy.state_channel), ('VAR', psspy.var_channel)]:
        for i in range(layout['N' + kind]):
            checked('channel', fn([-1, layout[kind] + i], kind + str(i)))
    for bus in [7, 9]:
        checked('voltage', psspy.voltage_and_angle_channel([-1, -1, -1, bus], [f'V{bus}', f'A{bus}']))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('step', psspy.dynamics_solution_param_2([di]*8, [df, 1e-6, .0005]+[df]*5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    initialized = [checked('constant', psspy.dsrval('CON', layout['CON']+i)) for i in range(32)]
    checked('prefault', psspy.run(0, .05, 1000000, 1, 0))
    if args.profile == 'fault':
        checked('fault', psspy.dist_bus_fault(7, 3, 230., [0., .2*230**2/100]))
    checked('fault interval', psspy.run(0, .1, 1000000, 1, 0))
    if args.profile == 'fault':
        checked('clear', psspy.dist_clear_fault(1))
    checked('finish', psspy.run(0, .5, 1000000, 1, 0))
    _, labels, data = dyntools.CHNF('native.out').get_data()
    keys = sorted(k for k in labels if isinstance(k, int))
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w', newline='') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['time_s']+[labels[k] for k in keys])
        for i, time in enumerate(data['time']):
            writer.writerow([format(time, '.12g')]+[format(data[k][i], '.12g') for k in keys])
    checked('report', psspy.docu(0, 1, [0, 3, 1]))
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    manifest = dict(status='complete' if quality else 'rejected-quality', settings=vars(args),
                    layout=layout, entered_constants=constants, initialized_constants=initialized,
                    engine_module=psse3607.__name__, library_sha256=sha(library),
                    source_document_sha256=sha(library.parent.parent/'DOCS/ModelLibrary.pdf'),
                    files={p.name: sha(p) for p in Path('.').iterdir() if p.is_file()})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(dict(status=manifest['status'], layout=layout)), flush=True)
    assert quality


if __name__ == '__main__':
    main()
