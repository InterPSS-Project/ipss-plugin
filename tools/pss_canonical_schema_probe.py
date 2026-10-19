"""Cold, exact-input PSS6C/PSS7C allocation and three-cycle SMIB references."""
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import sys

LAYOUTS = {'PSS6C': (4, 30, 10, 5), 'PSS7C': (6, 32, 19, 5)}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def checked(operation, result):
    code = result[0] if isinstance(result, tuple) else result
    if code != 0:
        raise RuntimeError(f'{operation}: error {code}')
    return result[1] if isinstance(result, tuple) and len(result) == 2 else None


def main():
    import psse3607
    import psspy
    import dyntools
    root = Path.cwd()
    model, repeat = sys.argv[1:]
    assert model in LAYOUTS and repeat in ('a', 'b')
    out = root / 'target' / ('pss-canonical-' + model.lower() + '-' + repeat)
    out.mkdir(exist_ok=False)
    source = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB'
    shutil.copy2(source / 'SMIB_v33.raw', out / 'network.raw')
    shutil.copy2(source / ('SMIB_v33_genrou_esst1a_' + model.lower() + '.dyr'), out / 'dynamic.dyr')
    shutil.copy2(__file__, out / 'probe.py')
    os.chdir(out)
    checked('initialize', psspy.psseinit(50))
    for fn in (psspy.progress_output, psspy.report_output, psspy.alert_output, psspy.prompt_output):
        checked('log', fn(2, 'native.log', [0, 0]))
    library = Path(psspy.__file__).resolve().parents[1] / 'PSSBIN/DSMDL.DLL'
    document = library.parent.parent / 'DOCS/ModelLibrary.pdf'
    checked('library', psspy.addmodellibrary(str(library)))
    checked('network', psspy.read(0, 'network.raw'))
    checked('flow', psspy.fnsl([0, 0, 0, 1, 1, 0, 99, 0]))
    assert psspy.solved() == 0
    checked('generators', psspy.cong(0))
    for stage in (1, 2, 3):
        checked('load conversion', psspy.conl(0, 1, stage, [0, 0], [0.] * 4))
    for fn in (psspy.ordr,): checked('order', fn(0))
    checked('factor', psspy.fact()); checked('network solution', psspy.tysl(0))
    checked('dynamic input', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    layout = {}
    for kind in ('ICON', 'CON', 'STATE', 'VAR'):
        layout[kind] = checked('index ' + kind, psspy.mdlind(1, '1', 'STAB', kind))
        layout['N' + kind] = checked('count ' + kind, psspy.mdlind(1, '1', 'STAB', 'N' + kind))
    assert tuple(layout['N' + k] for k in ('ICON', 'CON', 'STATE', 'VAR')) == LAYOUTS[model]
    icons = [checked('selector', psspy.dsival('ICON', layout['ICON'] + i)) for i in range(layout['NICON'])]
    constants = [checked('constant', psspy.dsrval('CON', layout['CON'] + i)) for i in range(layout['NCON'])]
    for i in range(layout['NSTATE']):
        checked('state', psspy.state_channel([-1, layout['STATE'] + i], f'PSS_STATE_{i + 1:02d}'))
    for bus in (1, 2):
        checked('voltage', psspy.voltage_channel([-1, -1, -1, bus], f'V_BUS{bus}'))
    for code, name in ((1, 'MACH_ANGLE'), (2, 'P_PU'), (3, 'Q_PU'), (5, 'EFD'), (7, 'MACH_SPEED'), (10, 'VOTHSG')):
        checked('machine', psspy.machine_array_channel([-1, code, 1], '1', name))
    for code, name in ((7, 'REF_SPEED'), (1, 'REF_ANGLE')):
        checked('reference', psspy.machine_array_channel([-1, code, 2], '1', name))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('step', psspy.dynamics_solution_param_2([di] * 8, [df, df, .0005] + [df] * 5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    checked('prefault', psspy.run(0, .05, 1000000, 1, 0))
    fault = checked('clock', psspy.dsrval('TIME'))
    checked('fault', psspy.dist_bus_fault(1, 3, 100., [0., 20.]))
    checked('fault interval', psspy.run(0, .1, 1000000, 1, 0))
    clear = checked('clock', psspy.dsrval('TIME'))
    checked('clear', psspy.dist_clear_fault(1))
    checked('recovery', psspy.run(0, 1., 1000000, 1, 0))
    _, labels, data = dyntools.CHNF('native.out').get_data()
    keys = sorted(k for k in labels if isinstance(k, int))
    assert all(math.isfinite(v) for k in ['time'] + keys for v in data[k])
    with Path('native.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['time_s'] + [labels[k] for k in keys])
        for i, time in enumerate(data['time']):
            writer.writerow([format(time, '.12g')] + [format(data[k][i], '.12g') for k in keys])
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    files = ['network.raw', 'dynamic.dyr', 'probe.py', 'native.log', 'native.out', 'native.csv']
    manifest = dict(status='complete' if quality else 'rejected-quality', model=model, layout=layout,
                    icons=icons, constants=constants, step_s=.0005, end_s=1.,
                    events=[dict(action='fault', actual_time_s=fault), dict(action='clear', actual_time_s=clear)],
                    sample_count=len(data['time']), channel_count=len(keys), engine_module=psse3607.__name__,
                    library_sha256=sha(library), source_document_sha256=sha(document),
                    python_sha256=sha(sys.executable), wrapper_sha256=sha(psspy.__file__),
                    files={name: sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(dict(model=model, status=manifest['status'], layout=layout)))
    assert quality


if __name__ == '__main__': main()
