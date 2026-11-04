"""Capture a prepared small standard-converter loadflow control without input edits."""
import argparse, os, shutil, json, hashlib
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--input',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--line-id',required=True)
parser.add_argument('--rectifier-bus',type=int,required=True)
parser.add_argument('--inverter-bus',type=int,required=True)
parser.add_argument('--tap-off',action='store_true')
parser.add_argument('--continue-solve',action='store_true')
a=parser.parse_args()
source=a.input.resolve();out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
shutil.copy2(source,out/'network.raw');shutil.copy2(__file__,out/'probe.py');os.chdir(out)
import psse3607,psspy,redirect
redirect.psse2py()
def ck(name,value):
 if isinstance(value,tuple):
  if value[0]:raise RuntimeError((name,value[0]))
  return value[1]
 if value:raise RuntimeError((name,value))
ck('init',psspy.psseinit(50))
for f in [psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output]:ck('log',f(2,'native.log',[0,0]))
flags=[0,0,0,0 if a.tap_off else 1,1,0,99,0]
ck('read',psspy.read(0,'network.raw'))
def snapshot(name):
 data={}
 for field in ['NUMBER','TYPE']:data[field]=ck(field,psspy.abusint(-1,2,[field]))[0]
 for field in ['PU','ANGLED']:data[field]=ck(field,psspy.abusreal(-1,2,[field]))[0]
 data['machine_bus']=ck('machinebus',psspy.amachint(-1,4,['NUMBER']))[0]
 for field in ['PGEN','QGEN']:data[field]=ck(field,psspy.amachreal(-1,4,[field]))[0]
 Path(name+'.json').write_text(json.dumps(data,indent=2))
 ck('rawexport',psspy.rawd_2(0,1,[1,1,1,0,0,0,0],0,name+'.raw'))
snapshot('loaded')
code=psspy.fdns(flags);solved=psspy.solved();history=[dict(call=1,return_code=code,solved=solved)];snapshot('solve-1')
if a.continue_solve:
 for call in [2,3]:
  if solved!=1:break
  code=psspy.fdns(flags);solved=psspy.solved();history.append(dict(call=call,return_code=code,solved=solved));snapshot('solve-'+str(call))
Path('solve-history.json').write_text(json.dumps(history,indent=2))
snapshot('solved')
if True:
 equipment={str(bus):{key:ck('converter '+key,psspy.dc2dat_2(a.line_id,bus,key)) for key in ['TAP','TR','EBASE','XC','ANGLE','ANGMIN','ANGMAX','KVDC','DCCUR']} for bus in [a.rectifier_bus,a.inverter_bus]}
 Path('equipment.json').write_text(json.dumps(equipment,indent=2))
Path('manifest.json').write_text(json.dumps(dict(status='complete' if code==0 and solved==0 and 'FATAL:' not in Path('native.log').read_text() else 'rejected-quality',solve_return=code,solved=solved,flags=flags,files={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in Path('.').iterdir() if f.is_file()}),indent=2))
print(json.dumps(dict(return_code=code,solved=solved)))

