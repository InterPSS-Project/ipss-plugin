"""Cold TGOV1 reference-step profiles with allocated-state and parameter readback."""
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import sys

PARAMETERS = (.05, .04, 1.2, .2, .1, .3, .5)
STEPS = {'coarse': .0005, 'fine': .00025}
COMMANDS = {'upper': .1, 'lower': -.1}


def checked(operation, result):
    code = result[0] if isinstance(result, tuple) else result
    if code != 0:
        raise RuntimeError(f'{operation}: error {code}')
    return result[1] if isinstance(result, tuple) and len(result) == 2 else None


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def profile(direction, resolution, repeat):
    if direction not in COMMANDS or resolution not in STEPS or repeat not in ('a', 'b'):
        raise ValueError('Expected upper/lower, coarse/fine and a/b')
    return dict(name=f'{direction}-{resolution}', step_s=STEPS[resolution],
                increment=COMMANDS[direction], apply_s=.05, release_s=.20, end_s=.5,
                constants=list(PARAMETERS), repeat=repeat)


def main():
    settings = profile(*sys.argv[1:])
    import psse3607
    import psspy
    import dyntools
    root = Path.cwd()
    out = root / 'target' / ('tgov1-limits-' + settings['name'] + '-' + settings['repeat'])
    out.mkdir(exist_ok=False)
    source = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB'
    shutil.copy2(source / 'SMIB_v33.raw', out / 'network.raw')
    text = (source / 'SMIB_v33_genrou_tgov1.dyr').read_text()
    original = "1 'TGOV1' 1 0.05 0.4 1.2 0.0 2.0 8.0 0.0 /"
    assert text.count(original) == 1
    text = text.replace(original, "1 'TGOV1' 1 " + ' '.join(map(str, PARAMETERS)) + ' /')
    (out / 'dynamic.dyr').write_text(text, encoding='utf-8')
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
        checked('loads', psspy.conl(0, 1, stage, [0, 0], [0.] * 4))
    checked('order', psspy.ordr(0)); checked('factor', psspy.fact()); checked('network solution', psspy.tysl(0))
    checked('dynamic input', psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', ''))
    layout = {}
    for kind in ('CON', 'STATE', 'VAR'):
        layout[kind] = checked('index ' + kind, psspy.mdlind(1, '1', 'GOV', kind))
        layout['N' + kind] = checked('count ' + kind, psspy.mdlind(1, '1', 'GOV', 'N' + kind))
    assert [layout['N' + k] for k in ('CON', 'STATE', 'VAR')] == [7, 2, 1]
    def constants():
        return [checked('constant', psspy.dsrval('CON', layout['CON'] + i)) for i in range(7)]
    before = constants()
    for i in range(2):
        checked('state', psspy.state_channel([-1, layout['STATE'] + i], f'GOV_STATE_{i + 1}'))
    checked('reference', psspy.var_channel([-1, layout['VAR']], 'GOV_REFERENCE'))
    for code, name in ((1, 'MACH_ANGLE'), (2, 'P_PU'), (3, 'Q_PU'), (6, 'PMECH'), (7, 'SPEED')):
        checked('machine', psspy.machine_array_channel([-1, code, 1], '1', name))
    for bus in (1, 2):
        checked('voltage', psspy.voltage_channel([-1, -1, -1, bus], f'V_BUS{bus}'))
    di, df = psspy.getdefaultint(), psspy.getdefaultreal()
    checked('step', psspy.dynamics_solution_param_2([di] * 8, [df, df, settings['step_s']] + [df] * 5))
    checked('start', psspy.strt_2([0, 0], 'native.out'))
    initialized = constants()
    assert all(abs(a-b) < 1e-7 * max(1, abs(b)) for a, b in zip(initialized, PARAMETERS, strict=True))
    checked('flat', psspy.run(0, settings['apply_s'], 1000000, 1, 0))
    events = []
    for name, increment, until in [('apply', settings['increment'], settings['release_s']), ('release', -settings['increment'], settings['end_s'])]:
        time = checked('clock', psspy.dsrval('TIME'))
        checked('reference increment', psspy.increment_gref(1, '1', increment))
        events.append(dict(action=name,actual_time_s=time,increment=increment))
        checked(name + ' interval', psspy.run(0, until, 1000000, 1, 0))
    _, labels, data = dyntools.CHNF('native.out').get_data()
    keys = sorted(k for k in labels if isinstance(k, int))
    assert all(math.isfinite(v) for k in ['time'] + keys for v in data[k])
    with Path('native.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n'); writer.writerow(['time_s'] + [labels[k] for k in keys])
        for i, time in enumerate(data['time']):
            writer.writerow([format(time, '.12g')] + [format(data[k][i], '.12g') for k in keys])
    log = Path('native.log').read_text(errors='replace')
    quality = 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    files = ['network.raw','dynamic.dyr','probe.py','native.log','native.out','native.csv']
    manifest = dict(status='complete' if quality else 'rejected-quality', model='TGOV1', settings=settings,
                    layout=layout, entered_constants=before, initialized_constants=initialized, events=events,
                    sample_count=len(data['time']), channel_count=len(keys), engine_module=psse3607.__name__,
                    library_sha256=sha(library), source_document_sha256=sha(document), python_sha256=sha(sys.executable),
                    wrapper_sha256=sha(psspy.__file__), files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(dict(profile=settings['name'],status=manifest['status'],layout=layout)))
    assert quality


if __name__ == '__main__': main()
