"""Distinguish exact symbolic load identities from whole-bus target selection."""
import argparse
from pathlib import Path
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--target', choices=['1', '*', '#'], required=True)
parser.add_argument('--repeat', choices=['a', 'b'], required=True)
parser.add_argument('--peer', action='store_true')
args = parser.parse_args()
source = Path('tools/hsp_model_reference_probe.py').read_text()
needle = '    import psse3607'
insertion = '''    raw_path = out / 'network.raw'
    raw = raw_path.read_text()
    old = "2,'1',1,1,1,100.000,50.000,"
    assert raw.count(old) == 1
    raw_path.write_text(raw.replace(old, "2,'" + TARGET + "',1,1,1,100.000,50.000,"))
    dyr_path = out / 'dynamic.dyr'
    lines = dyr_path.read_text().splitlines()
    changed = 0
    for i, line in enumerate(lines):
        fields = re.findall(r"'[^']*'|[^\\s,]+", line)
        if len(fields) > 2 and fields[1] == "'TIOCR1'":
            assert fields[6:8] == ['2', "'1'"]
            fields[7] = "'" + TARGET + "'"
            lines[i] = ' '.join(fields)
            changed += 1
    assert changed == 1
    dyr_path.write_text('\\n'.join(lines) + '\\n')
    shutil.copy2(root / 'tools/tiocr_literal_target_reference_probe.py', out / 'tiocr_literal_target_reference_probe.py')
'''.replace('TARGET', repr(args.target))
assert source.count(needle) == 1
source = source.replace(needle, insertion + needle)
if args.peer:
    needle = '    import psse3607'
    insertion = '''    raw_path = out / 'network.raw'
    marker = '0 / END OF LOAD DATA, BEGIN FIXED SHUNT DATA'
    raw = raw_path.read_text()
    assert raw.count(marker) == 1
    raw_path.write_text(raw.replace(marker, "2,'2',1,1,1,10.0,3.0,0,0,0,0,1,1,0\\n" + marker))
'''
    source = source.replace(needle, insertion + needle)
needle = '    generator_layout = None'
insertion = "    checked('load P', psspy.load_array_channel([-1, 1, 2], TARGET, 'LOAD_P'))\n    checked('load Q', psspy.load_array_channel([-1, 2, 2], TARGET, 'LOAD_Q'))\n".replace('TARGET', repr(args.target))
assert source.count(needle) == 1
source = source.replace(needle, insertion + needle)
if args.peer:
    source = source.replace(needle, "    checked('peer P', psspy.load_array_channel([-1, 1, 2], '2', 'PEER_P'))\n    checked('peer Q', psspy.load_array_channel([-1, 2, 2], '2', 'PEER_Q'))\n" + needle)
sys.path.insert(0, str(Path('tools').resolve()))
label = {'1': 'ordinary', '*': 'star', '#': 'hash'}[args.target]
sys.argv = ['hsp_model_reference_probe.py', '--fixture', 'tiocr1-load-only',
            '--profile', 'fault', '--repeat', args.repeat,
            '--batch', 'tiocr-literal-' + label + ('-peer' if args.peer else '') + '-20261104']
exec(compile(source, 'tools/hsp_model_reference_probe.py', 'exec'), {'__name__': '__main__'})
