"""Cold, controller-free two-bus machine boundary capture."""
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
    parser.add_argument('--step', type=float, choices=[.0005, 1 / 240], required=True)
    parser.add_argument('--repeat', choices=['a', 'b'], required=True)
    parser.add_argument('--batch', default='genrou-boundary')
    args = parser.parse_args()
    root = Path.cwd()
    source = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/HspModels'
    tag = ('fine' if args.step == .0005 else 'quarter') + '-' + args.repeat
    out = root / 'target' / args.batch / tag
    if shutil.disk_usage(root / 'target').free < 2 * 1024**3 + 64 * 1024**2:
        raise OSError('Free-space reserve required')
    out.mkdir(parents=True, exist_ok=False)
    shutil.copy2(source / 'network.raw', out / 'network.raw')
    machine = (source / 'hyg3u1-1.dyr').read_text().splitlines()
    (out / 'dynamic.dyr').write_text(machine[0] + '\n' + machine[-1] + '\n')
    for name in ['genrou_boundary_reference_probe.py', 'tgov1_limit_probe.py']:
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
    layout = {}
    for bus in [1, 2]:
        values = {kind: checked('machine allocation', psspy.mdlind(bus, '1', 'GEN', kind))
                  for kind in ['STATE', 'NSTATE']}
        layout[str(bus)] = values
        for index in range(values['NSTATE']):
            checked('machine state channel', psspy.state_channel(
                [-1, values['STATE'] + index], f'GEN{bus}_STATE{index}'))
        for code, name in [(1, 'ANGLE'), (2, 'P'), (3, 'Q'), (6, 'PMECH'), (7, 'SPEED'), (5, 'EFD')]:
            checked('machine channel', psspy.machine_array_channel([-1, code, bus], '1', f'M{bus}_{name}'))
        checked('voltage channel', psspy.voltage_and_angle_channel([-1, -1, -1, bus],
                [f'V{bus}', f'A{bus}']))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('step', psspy.dynamics_solution_param_2([di] * 8, [df, 1e-6, args.step] + [df] * 5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    checked('prefault', psspy.run(0, .05, 1000000, 1, 0))
    checked('fault', psspy.dist_bus_fault(1, 3, 100., [0., .2 * 100.**2 / 100.]))
    checked('during', psspy.run(0, .10, 1000000, 1, 0))
    checked('clear', psspy.dist_clear_fault(1))
    checked('finish', psspy.run(0, .15, 1000000, 1, 0))
    _, labels, data = dyntools.CHNF('native.out').get_data()
    keys = sorted(key for key in labels if isinstance(key, int))
    assert all(math.isfinite(value) for key in ['time'] + keys for value in data[key])
    with Path('native.csv').open('w', newline='') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['time_s'] + [labels[key] for key in keys])
        for index, time in enumerate(data['time']):
            writer.writerow([format(time, '.12g')] +
                            [format(data[key][index], '.12g') for key in keys])
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    manifest = dict(status='complete' if quality else 'rejected-quality', settings=vars(args),
                    machine_layouts=layout, samples=len(data['time']), channels=len(keys),
                    engine_module=psse3607.__name__, library_sha256=sha(library),
                    source_document_sha256=sha(library.parent.parent / 'DOCS/ModelLibrary.pdf'),
                    files={file.name: sha(file) for file in Path('.').iterdir() if file.is_file()})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(dict(tag=tag, status=manifest['status'], samples=manifest['samples'])), flush=True)
    assert quality


if __name__ == '__main__':
    main()
