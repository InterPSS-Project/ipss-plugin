"""Independent converter-resistance controls; source constants and physical schedule stay explicit."""
import argparse,sys
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--end',choices=['zero','rectifier','inverter'],required=True)
p.add_argument('--profile',choices=['flat','fault'],required=True);p.add_argument('--repeat',choices=['a','b'],required=True)
a=p.parse_args()
source=Path('tools/cdc6t_reference_probe.py').read_text()
# Modify equipment before writing the prepared RAW, never the original source copy.
anchor="    if args.static_gamma is not None:"
assert source.count(anchor)==1
source=source.replace(anchor,"""    for terminal,amount in [(1,RESISTANCE_R),(2,RESISTANCE_I)]:
        fields=lines[dc_header+terminal].split(',')
        fields[4]=str(amount)
        lines[dc_header+terminal]=','.join(fields)
"""+anchor)
source=source.replace("    os.chdir(out)","    shutil.copy2(root/'tools/cdc6t_resistance_reference_probe.py',out/'cdc6t_resistance_reference_probe.py')\n    os.chdir(out)")
sys.path.insert(0,str(Path('tools').resolve()))
sys.argv=['cdc6t_reference_probe.py','--repeat',a.repeat,'--profile',a.profile,'--control','inverter-power',
 '--limit-curve','active','--batch','cdc6t-resistance-'+a.end+'-20261104']
exec(compile(source,'tools/cdc6t_reference_probe.py','exec'),{'__name__':'__main__',
 'RESISTANCE_R':.5 if a.end=='rectifier' else 0.,'RESISTANCE_I':.5 if a.end=='inverter' else 0.})
