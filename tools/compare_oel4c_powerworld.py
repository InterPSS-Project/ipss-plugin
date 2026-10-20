"""Exact-clock all-row typed OEL4C comparison; non-equivalent coordinates stay explicitly unscored."""
import hashlib
import json
import math
import re
from pathlib import Path
from audit_oel3c_measurement import read_measurements,rmse
from compare_oel3c_events import grid_tick,read_actual

CHANNELS={
 'V_BUS1':('Bus','1','TSVpu'),'V_BUS2':('Bus','2','TSVpu'),
 'SPEED_DEV':('Generator','1 1','TSSpeed'),'REF_SPEED_DEV':('Generator','2 1','TSSpeed'),
 'P_PU':('Generator','1 1','TSMW'),'Q_PU':('Generator','1 1','TSMvar'),
 'EFD':('Generator','1 1','TSEfd'),'IFD_EXC':('Generator','1 1','TSIfd'),
 'OEL_OUTPUT':('Generator','1 1','TSVOEL'),
 'OEL_CONTROLLED_ERROR':('Generator','1 1','TSOELOther:3'),'OEL_TIMER':('Generator','1 1','TSOELOther:2')}
for i,name in enumerate(('EXC_FIELD','EXC_SENSE','EXC_REGULATOR','EXC_LEAD_LAG','EXC_RATE_FEEDBACK'),1):
 CHANNELS[name]=('Generator','1 1','TSExciterState:'+str(i))
ANGLES=(('Generator','1 1','TSRotorAngle'),('Generator','2 1','TSRotorAngle'))
UNSCORED={('Generator','1 1','TSOELState:1'):'Duplicate exported output coordinate; TSVOEL is scored',
 ('Generator','1 1','TSOELOther:1'):'Reference integer mode is not the typed negative-error delay gate',
 ('Generator','1 1','TSOELOther:4'):'No documented equivalent physical coordinate'}

def compare(fields,reference,actual,step,profile):
 if step not in (.0005,.00025):raise ValueError('Unsupported matched timestep')
 if profile not in ('flat','delayed-fault','zero-delay-fault','integral-fault','lower-limit-fault','qmax-fault'):
  raise ValueError('Unsupported matched profile')
 indices={}
 for n,field in enumerate(fields):
  if len(field)<5:raise ValueError('Malformed reference metadata')
  key=tuple(field[i].strip() for i in (0,1,4))
  if key in indices:raise ValueError('Duplicate reference field')
  indices[key]=n+1
 if set(indices)!=set(CHANNELS.values())|set(ANGLES)|set(UNSCORED):raise ValueError('Missing or undeclared reference channel')
 expected_count=round(.5/step)+(1 if profile=='flat' else 3)
 if not reference or not actual or reference[0][0]!=0 or actual[0]['time_s']!=0:
  raise ValueError('Missing initialization')
 if len(reference)!=expected_count or len(actual)!=expected_count or reference[-1][0]!=.5:
  raise ValueError('Incomplete half-second history')
 if any(len(r)!=len(fields)+1 or not all(map(math.isfinite,r)) for r in reference):raise ValueError('Invalid raw row')
 if any(a[0]>b[0] for a,b in zip(reference,reference[1:])):raise ValueError('Nonmonotonic reference clock')
 if any(a['time_s']>b['time_s'] for a,b in zip(actual,actual[1:])):raise ValueError('Nonmonotonic actual clock')
 events={a[0] for a,b in zip(reference,reference[1:]) if a[0]==b[0]}
 if events!=(set() if profile=='flat' else {.05,.1}):raise ValueError('Missing or unexpected event occurrences')
 required=set(CHANNELS)|{'RELATIVE_ANGLE','time_s','event_side'};samples={}
 for row in actual:
  if not required.issubset(row):raise ValueError('Missing actual observation')
  if row['event_side'] not in ('regular','pre','post'):raise ValueError('Invalid event side')
  if any(not math.isfinite(row[name]) for name in required-{'event_side'}):raise ValueError('Nonfinite actual value')
  key=(grid_tick(row['time_s'],step),row['event_side'])
  if key in samples:raise ValueError('Duplicate actual sample')
  samples[key]=row
 paired=[]
 for i,row in enumerate(reference):
  time=row[0];side='post' if i>0 and reference[i-1][0]==time else 'pre' if time in events else 'regular'
  key=(grid_tick(time,step),side)
  if key not in samples:raise ValueError('Missing matching event side')
  values={name:row[indices[field]] for name,field in CHANNELS.items()}
  for name in ('SPEED_DEV','REF_SPEED_DEV'):values[name]-=1
  for name in ('P_PU','Q_PU'):values[name]/=100
  values['RELATIVE_ANGLE']=math.radians(row[indices[ANGLES[0]]]-row[indices[ANGLES[1]]])
  paired.append(dict(time_s=time,event_side=side,reference=values,actual=samples.pop(key)))
 if samples:raise ValueError('Unpaired actual rows')
 metrics={}
 for name in paired[0]['reference']:
  groups={}
  for side in ('all','regular','pre','post'):
   records=[p for p in paired if side=='all' or p['event_side']==side]
   errors=[p['actual'][name]-p['reference'][name] for p in records]
   if not records:groups[side]=dict(samples=0,rmse=None,maximum_absolute=None,passes_rmse=False,peak_time_s=None,peak_event_side=None);continue
   peak=max(range(len(errors)),key=lambda i:abs(errors[i]))
   groups[side]=dict(rmse(errors),peak_time_s=records[peak]['time_s'],peak_event_side=records[peak]['event_side'])
  metrics[name]=groups
 alignment=json.dumps([(p['time_s'],p['event_side']) for p in paired],separators=(',',':'))
 return dict(profile=profile,step_s=step,reference_rows=len(reference),actual_rows=len(actual),paired_rows=len(paired),rows_dropped=0,
  raw_reference_channels=len(fields),compared_coordinates=len(metrics),metrics=metrics,
  unscored_reference_coordinates=[dict(coordinate=list(k),reason=v) for k,v in UNSCORED.items()],
  actual_only_coordinates=sorted(set(actual[0])-set(metrics)-{'time_s','event_side'}),
  normalization='Matched 100 MVA network/machine base; speed deviation; relative angle radians; timer seconds; other coordinates published pu',
  alignment_sha256=hashlib.sha256(alignment.encode()).hexdigest(),
  numerical_rmse_pass=all(v['all']['passes_rmse'] for v in metrics.values()),trajectory_acceptance=False,parameters_fitted=False)

def validate_case(root,actual,profile,step):
 from oel4c_powerworld_probe import PROFILES,configuration
 matches=[]
 for file in Path(actual).glob('case-*/case.json'):
  case=json.loads(file.read_text())
  if case.get('profile')==profile and case.get('step_s')==step and case.get('machine_base_mva')==100:matches.append((file,case))
 if len(matches)!=1:raise ValueError('Expected one exact 100-MVA typed case')
 file,case=matches[0]
 if case.get('network_base_mva')!=100 or not case.get('typed_limiter'):raise ValueError('Undeclared base or runtime')
 data=case.get('data',{});keys=('reactivePickup','delay','proportionalGain','integralGain','minimumOutput')
 if tuple(data.get(k) for k in keys)!=PROFILES[profile]:raise ValueError('Actual limiter parameters do not match the reference')
 raw=(Path(root)/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
 gen=next(s for s in raw.splitlines() if s.startswith("1,'1',"));values=gen.split(',');values[8]='100.0'
 if (file.parent/'network.raw').read_text()!=raw.replace(gen,','.join(values)):raise ValueError('Actual network changed')
 def records(body):
  result=[]
  for line in body.splitlines():
   tokens=re.findall(r"'[^']*'|[^\s/]+",line)
   if tokens:result.append(tokens[:3]+[float(v) for v in tokens[3:]])
  return result
 expected='\n'.join(s for s in configuration(profile,raw)['dynamic'].splitlines() if "'OEL4C'" not in s)
 if records((file.parent/'model.dyr').read_text())!=records(expected):raise ValueError('Actual machine/exciter records changed')
 return file

def audit(root,actual):
 from check_oel4c_powerworld_artifacts import audit as reference_audit
 value=reference_audit(root);results=[]
 for pair in value['results']:
  profile=pair['profile'];step=pair['step_s'];folder=Path(root)/'target'/pair['runs'][0]
  case=validate_case(root,actual,profile,step)
  name=profile+'-mva100.0-'+('5.0E-4' if step==.0005 else '2.5E-4')+'.csv'
  result=compare(*read_measurements(folder/'powerworld.csv'),read_actual(Path(actual)/name),step,profile)
  result['reference_sha256']=pair['files'][0]['powerworld.csv'];result['actual_sha256']=hashlib.sha256((Path(actual)/name).read_bytes()).hexdigest()
  result['actual_case_sha256']=hashlib.sha256(case.read_bytes()).hexdigest()
  results.append(result)
 return dict(status='all-row-independent-coupled-diagnostics-not-acceptance',runs=12,reference_rows=sum(r['reference_rows'] for r in results),
  compared_coordinates=17,results=results,parameters_fitted=False,trajectory_acceptance=False,full_system_dynamics_run=False)

if __name__=='__main__':
 import sys
 print(json.dumps(audit(Path.cwd(),sys.argv[1]),indent=2))
