"""Source-first whole-bus relay ownership discriminator; preserves complete channels."""
import argparse, sys
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--target',choices=['1','*','#'],required=True)
parser.add_argument('--repeat',choices=['a','b'],required=True)
parser.add_argument('--fraction',type=float,choices=[0.,.25,1.],default=.25)
a=parser.parse_args()
source=Path('tools/hsp_model_reference_probe.py').read_text()
needle="    if args.constant:\n        shutil.copy2(out / 'dynamic.dyr', out / 'original.dyr')"
insert=r"""    shutil.copy2(out/'network.raw',out/'original-network.raw')
    raw=(out/'network.raw').read_text()
    marker='0 / END OF LOAD DATA, BEGIN FIXED SHUNT DATA'
    assert raw.count(marker)==1
    raw=raw.replace(marker,"2,'2',1,1,1,10.0,3.0,0,0,0,0,1,1,0\n"+marker)
    marker='0 / END OF FIXED SHUNT DATA, BEGIN GENERATOR DATA'
    assert raw.count(marker)==1
    raw=raw.replace(marker,"2,'F1',1,0.0,1.0\n"+marker)
    (out/'network.raw').write_text(raw)
    with (out/'dynamic.dyr').open('a') as f:
        f.write(RELAY_RECORD)
""".replace('RELAY_RECORD',repr("\n1 'TIOCR1' 2 '1' 1 1 2 '"+a.target+"' 0 0 '0' 0 0 '0' 0 0 '0' .40 .1 1.1 .04 1.5 .03 2 .02 3 .01 5 .005 .01 "+str(a.fraction)+" /\n"))
assert needle in source;source=source.replace(needle,insert+needle)
needle='    generator_layout = None'
insert="""    relay_var=checked('relay variables',psspy.rmodind(1,2,'1',1,'VAR'))
    for i in range(3):
        checked('relay channel',psspy.var_channel([-1,relay_var+i],'RELAY_VAR'+str(i)))
"""
assert needle in source;source=source.replace(needle,insert+needle)
needle='    import psse3607'
source=source.replace(needle,"    shutil.copy2(root/'tools/composite_wildcard_reference_probe.py',out/'composite_wildcard_reference_probe.py')\n"+needle)
source=source.replace("['Network not converged','Incomplete data record']","['Network not converged','Incomplete data record','invalid load specified']")
sys.path.insert(0,str(Path('tools').resolve()))
label={'1':'exact','*':'star','#':'hash'}[a.target]
sys.argv=['hsp_model_reference_probe.py','--fixture','cmldblu1-restart','--profile','fault','--repeat',a.repeat,'--batch','cmld-wildcard-'+label+'-'+str(a.fraction)+'-20261104','--constant','21=0','--constant','22=.5','--constant','8=-1','--constant','2=0','--constant','3=0','--constant','5=0','--generator-states']
exec(compile(source,'tools/hsp_model_reference_probe.py','exec'),{'__name__':'__main__'})
