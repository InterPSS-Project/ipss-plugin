"""Audit cold pairs and explicitly separate the observed inverter angle from physical equations."""
import argparse,csv,hashlib,io,json,math,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('archive',type=Path);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
result={'archive_sha256':hashlib.sha256(a.archive.read_bytes()).hexdigest(),'profiles':[]}
with zipfile.ZipFile(a.archive) as z:
 manifest=json.loads(z.read('manifest.json'))
 for name,digest in manifest['files'].items():assert hashlib.sha256(z.read(name)).hexdigest()==digest,name
 for end in ['zero','rectifier','inverter']:
  for profile in ['flat','fault']:
   prefix=end+'/'+profile+'/a/'
   raw=z.read(prefix+'native.csv');assert raw==z.read(end+'/'+profile+'/b/native.csv')
   rows=[{k:float(v) for k,v in r.items()} for r in csv.DictReader(io.StringIO(raw.decode()))]
   m=json.loads(z.read(prefix+'manifest.json'));assert m['status']=='complete' and m['initialization_and_network_valid']
   assert m['entered_constants']==m['initialized_constants']
   r=rows[0];e=json.loads(z.read(prefix+'solved-equipment.json'))['9']
   E=3*math.sqrt(2)/math.pi*2*e['EBASE']*1000*e['TR']/e['TAP']*r['V9']
   overlap=3/math.pi*2*e['XC'];loss=2 if end=='inverter' else 0
   physical=math.degrees(math.acos((-1000*r['VAR8']+(overlap+loss)*r['VAR7'])/E))
   diagnostic=math.degrees(math.acos((-1000*r['VAR8']+(overlap-loss)*r['VAR7'])/E))
   result['profiles'].append({'end':end,'profile':profile,'rows':len(rows),'duplicates':sum(rows[i]['time_s']==rows[i-1]['time_s'] for i in range(1,len(rows))),
     'cold_pair_equal':True,'physical_inverter_alpha_degrees':physical,'opposite_resistance_diagnostic_degrees':diagnostic,
     'source_calculated_alpha_degrees':r['STATE7'],'source_published_alpha_degrees':r['VAR3'],
     'source_static_extinction_degrees':e['ANGLE'],'source_dynamic_extinction_degrees':r['VAR4'],
     'initial_physical_angle_error_degrees':physical-r['STATE7']})
result['interpretation']='Opposite-resistance diagnostic is not an implemented equation or a qualified coordinate transform.'
a.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
