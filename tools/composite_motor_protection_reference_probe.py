"""Independent repeated-fault induction-motor protection discriminator."""
import argparse,sys
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--protection',choices=['enabled','disabled'],required=True);p.add_argument('--repeat',choices=['a','b'],required=True);p.add_argument('--channel-part',choices=['0','1'],required=True);p.add_argument('--clear-diagnostic-storage',action='store_true');p.add_argument('--scenario',choices=['base','zero-flat','zero-fault','cancelled','full','overlap','overlap-zero'],default='base');a=p.parse_args()
source=Path('tools/hsp_model_reference_probe.py').read_text()
source=source.replace('    import psse3607',"    shutil.copy2(root/'tools/composite_motor_protection_reference_probe.py',out/'composite_motor_protection_reference_probe.py')\n    import psse3607")
constants={2:0,3:0,5:0,8:-1,18:.2,19:0,20:0,21:0,22:0,31:0,36:0,47:.9 if a.protection=='enabled' else 0,48:.005,49:.25,50:.95,51:.01,52:0,53:999,54:0,55:1,56:999}
if a.scenario.startswith('zero-'):constants.update({48:0,51:0})
if a.scenario=='cancelled':constants[48]=.08
if a.scenario=='full':constants[49]=1
if a.scenario.startswith('overlap'):constants.update({49:.3,50:2,51:999,52:.9,53:.01,54:.7 if a.scenario=='overlap-zero' else .8,55:2,56:999})
sys.path.insert(0,str(Path('tools').resolve()))
sys.argv=['hsp_model_reference_probe.py','--fixture','cmldblu1-mixed','--profile','flat' if a.scenario=='zero-flat' else 'repeated-faults','--repeat',a.repeat,'--channel-part',a.channel_part,'--batch','cmld-motor-uv-'+a.protection+('' if a.scenario=='base' else '-'+a.scenario)+('-cleared' if a.clear_diagnostic_storage else '')+'-20261104','--generator-states']
if a.clear_diagnostic_storage:sys.argv.append('--clear-diagnostic-storage')
for k,v in constants.items():sys.argv.extend(['--constant',str(k)+'='+str(v)])
exec(compile(source,'tools/hsp_model_reference_probe.py','exec'),{'__name__':'__main__'})
