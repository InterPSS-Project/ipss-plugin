"""Cold typed-name/native-name allocation check; no trajectory acceptance claim."""
import json
import os
from pathlib import Path
import shutil
import sys
from psssb_dialect_probe import checked, sha


def main():
    import psse3607
    import psspy
    model, repeat = sys.argv[1:]
    assert model in ('PSS1A', 'IEEEST') and repeat and repeat.isalnum()
    root = Path.cwd()
    source = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB'
    out = root / 'target' / ('pss1a-dialect-' + model.lower() + '-' + repeat)
    out.mkdir(exist_ok=False)
    shutil.copy2(source / 'SMIB_v33.raw', out / 'network.raw')
    shutil.copy2(source / ('SMIB_v33_genrou_esst1a_' + model.lower() + '.dyr'), out / 'dynamic.dyr')
    shutil.copy2(__file__, out / 'probe.py')
    shutil.copy2(root / 'tools/psssb_dialect_probe.py', out / 'psssb_dialect_probe.py')
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
    for stage in (1, 2, 3): checked('loads', psspy.conl(0, 1, stage, [0, 0], [0.] * 4))
    checked('order', psspy.ordr(0)); checked('factor', psspy.fact()); checked('network', psspy.tysl(0))
    load_code = psspy.dyre_new([1, 1, 1, 1], 'dynamic.dyr', '', '', '')
    layout = {key: list(psspy.mdlind(1, '1', 'STAB', key))
              for key in ('ICON', 'CON', 'STATE', 'VAR', 'NICON', 'NCON', 'NSTATE', 'NVAR')}
    attached = layout['STATE'][0] == 0 and layout['NSTATE'][0] == 0 and layout['NSTATE'][1] > 0
    constants = []
    if attached:
        for i in range(layout['NCON'][1]):
            result = psspy.dsrval('CON', layout['CON'][1] + i)
            checked('constant', result); constants.append(result[1])
    manifest = dict(model=model, repeat=repeat, load_code=load_code, attached=attached,
                    layout=layout, constants=constants, evidence='allocation-only',
                    engine_module=psse3607.__name__, library_sha256=sha(library),
                    source_document_sha256=sha(document), python_sha256=sha(sys.executable),
                    wrapper_sha256=sha(psspy.__file__),
                    files={name: sha(name) for name in ('probe.py', 'psssb_dialect_probe.py', 'network.raw', 'dynamic.dyr', 'native.log')})
    Path('manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(dict(model=model, attached=attached, layout=layout)))
    assert attached == (model == 'IEEEST'), manifest


if __name__ == '__main__': main()
