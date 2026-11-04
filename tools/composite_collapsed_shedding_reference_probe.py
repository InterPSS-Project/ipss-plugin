"""Reduced composite load and relay shedding discriminator using retained probe mechanics."""
import argparse,sys
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--fraction',type=float,choices=[0.,.25,1.],required=True);p.add_argument('--repeat',choices=['a','b','c'],required=True);p.add_argument('--omitted',choices=['both','feeder','transformer'],required=True);a=p.parse_args()
source=Path('tools/hsp_model_reference_probe.py').read_text()
source=source.replace("parser.add_argument('--repeat', choices=['a', 'b'], required=True)",
        "parser.add_argument('--repeat', choices=['a', 'b', 'c'], required=True)")
needle="    if args.constant:\n        shutil.copy2(out / 'dynamic.dyr', out / 'original.dyr')"
replacement="    with (out / 'dynamic.dyr').open('a') as f:\n        f.write(\"\\n1 'TIOCR1' 2 '1' 1 1 2 '1' 0 0 '0' 0 0 '0' 0 0 '0' .40 .1 1.1 .04 1.5 .03 2 .02 3 .01 5 .005 .01 "+str(a.fraction)+" /\\n\")\n"+needle
assert needle in source;source=source.replace(needle,replacement)
needle="    generator_layout = None"
replacement="    relay_var=checked('relay variables',psspy.rmodind(1,2,'1',1,'VAR'))\n    for i in range(3):\n        checked('relay channel',psspy.var_channel([-1,relay_var+i],'RELAY_VAR'+str(i)))\n"+needle
assert needle in source;source=source.replace(needle,replacement)
# Preserve the executed wrapper as evidence in addition to the generic probe.
needle="    import psse3607"
replacement="    shutil.copy2(root/'tools/composite_collapsed_shedding_reference_probe.py',out/'composite_collapsed_shedding_reference_probe.py')\n"+needle
source=source.replace(needle,replacement)
sys.path.insert(0,str(Path('tools').resolve()))
sys.argv=['hsp_model_reference_probe.py','--fixture','cmldblu1-restart','--profile','fault','--repeat',a.repeat,'--batch','cmld-collapsed-'+a.omitted+'-'+str(a.fraction)+'-20261104','--constant','21=0','--constant','22=.5','--constant','8=-1','--generator-states']
if a.omitted in ('both','feeder'):sys.argv.extend(['--constant','2=0','--constant','3=0'])
if a.omitted in ('both','transformer'):sys.argv.extend(['--constant','5=0'])
exec(compile(source,'tools/hsp_model_reference_probe.py','exec'),{'__name__':'__main__'})
