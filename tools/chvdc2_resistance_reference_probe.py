"""Cold CHVDC converter-resistance controls with unchanged controller constants."""
import argparse
import sys
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--end',choices=['zero','rectifier','inverter'],required=True)
parser.add_argument('--profile',choices=['flat','fault'],required=True)
parser.add_argument('--repeat',choices=['a','b'],required=True)
args=parser.parse_args()
source=Path('tools/chvdc2_reference_probe.py').read_text()
anchor="    (out/'network.raw').write_text"
assert source.count(anchor)==1
source=source.replace(anchor,"""    dc_header=next(i for i,line in enumerate(lines) if line.startswith('"1           ",'))
    for terminal,resistance in [(1,RECTIFIER_R),(2,INVERTER_R)]:
        fields=lines[dc_header+terminal].split(',');fields[4]=str(resistance)
        lines[dc_header+terminal]=','.join(fields)
"""+anchor)
source=source.replace('    os.chdir(out)',"    shutil.copy2(root/'tools/chvdc2_resistance_reference_probe.py',out/'chvdc2_resistance_reference_probe.py')\n    os.chdir(out)")
sys.path.insert(0,str(Path('tools').resolve()))
sys.argv=['chvdc2_reference_probe.py','--form','legacy','--network','stiff','--flags','0','0','1',
          '--profile','flat' if args.profile=='flat' else 'inverter-fault','--repeat',args.repeat,
          '--network-iterations','50','--step','.0005','--batch','chvdc2-resistance-'+args.end+'-20261104']
exec(compile(source,'tools/chvdc2_reference_probe.py','exec'),{'__name__':'__main__',
    'RECTIFIER_R':.5 if args.end=='rectifier' else 0.,'INVERTER_R':.5 if args.end=='inverter' else 0.})
