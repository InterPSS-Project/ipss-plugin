"""Whole-export cold repeatability and exact OEL4C readback audit, without trajectory promotion."""
import json
import math
from pathlib import Path
import re
from oel4c_powerworld_probe import PROFILES,configuration,specification,validate_readback
from audit_oel3c_measurement import read_measurements
from check_basoel2_powerworld_artifacts import digest

def observations(fields,rows,pickup,delay=0):
    def index(name):
        found=[i+1 for i,f in enumerate(fields) if f[0].strip()=='Generator' and f[1].strip()=='1 1' and f[4].strip()==name]
        if len(found)!=1:raise ValueError('Missing/duplicate OEL4C channel '+name)
        return found[0]
    output,state,active,timer,error,unused,q=[index(k) for k in
        ('TSVOEL','TSOELState:1','TSOELOther:1','TSOELOther:2','TSOELOther:3','TSOELOther:4','TSMvar')]
    physical=[];duplicates=[]
    for i,row in enumerate(rows):
        raw_error=pickup-row[q]/100
        # The printed single-precision timer can lie just below an exact delay boundary.
        # Use the exported discrete mode for this channel-identity check, not a fitted time tolerance.
        expected_controlled=raw_error if raw_error>=0 or row[active]!=0 or delay==0 else 0
        physical.append(abs(row[error]-expected_controlled))
        if i and row[0]==rows[i-1][0]:duplicates.append(row[0])
        # The reference uses at least 0/1/2 modes; do not mislabel its saturation code as a boolean.
        if row[active]<0 or row[active]!=int(row[active]) or row[timer]<0:raise ValueError('Invalid discrete observation')
        if row[output]!=row[state]:raise ValueError('Published reference state is not its PI output coordinate')
    return dict(initial_error=rows[0][error],initial_output=rows[0][output],
        first_nonzero_mode_s=next((r[0] for r in rows if r[active]!=0),None),
        observed_mode_values=sorted(set(r[active] for r in rows)),
        maximum_timer_s=max(r[timer] for r in rows),minimum_output=min(r[output] for r in rows),
        output_equals_exported_pi_state=True,unused_other_maximum=max(abs(r[unused]) for r in rows),
        controlled_error_rmse=math.sqrt(sum(v*v for v in physical)/len(physical)),
        controlled_error_peak=max(physical),controlled_error_peak_time_s=rows[physical.index(max(physical))][0],
        controlled_error_identity_uses_exported_mode=True,timer_dynamics_acceptance=False,
        post_event_times=duplicates,raw_rows=len(rows),rows_dropped=0,trajectory_acceptance=False)

def audit(root):
    root=Path(root);raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    if float(raw.splitlines()[0].split(',')[1])!=100:raise ValueError('Expected declared 100-MVA machine/network base')
    pairs=[];executables=set();total=0
    for profile in PROFILES:
        cfg=configuration(profile,raw)
        for scale,h in (('coarse',.0005),('fine',.00025)):
            folders=[root/'target'/('oel4c-powerworld-contract2-'+profile+'-'+scale+'-'+r) for r in ('a','b')]
            data=[];files=[]
            for folder in folders:
                manifest=json.loads((folder/'manifest.json').read_text())
                if manifest['status']!='complete' or manifest['auto_correct'] or manifest['powerworld_model_overrides']:
                    raise ValueError('Reference validation/correction contract changed')
                spec=specification(profile,h,(folder/'source.dyr').relative_to(root).as_posix())
                if manifest['simulation']!=spec['simulation'] or manifest['contingency']!=spec['contingency']:
                    raise ValueError('Reference clock/event contract changed')
                executables.add(manifest['powerworld_executable']['sha256'])
                for name,item in manifest['artifacts'].items():
                    if Path(name).name!=name or digest(folder/name,True)!=item['sha256']:raise ValueError('Artifact hash changed')
                for item in manifest['inputs'].values():
                    if digest(root/item['path'],True)!=item['sha256']:raise ValueError('Input hash changed')
                if (folder/'source.dyr').read_text()!=cfg['dynamic']:raise ValueError('Entered parameters changed')
                for stage in ('power_flow_solved','initialized'):validate_readback((folder/(stage+'-models.aux')).read_text(),cfg,profile)
                if not re.search(r'Validation of .* completed with\s+0 errors and\s+0 warnings',(folder/'powerworld.log').read_text()):
                    raise ValueError('Reference validation is not clean')
                fields,rows=read_measurements(folder/'powerworld.csv')
                if len(fields)!=21 or len(rows)!=round(.5/h)+(1 if profile=='flat' else 3):raise ValueError('Reference dimensions changed')
                if any(r[11]<PROFILES[profile][4]-1e-7 or r[11]>1e-7 for r in rows):raise ValueError('Output outside declared bounds')
                data.append((fields,rows));files.append({p.name:digest(p) for p in folder.iterdir() if p.is_file()})
            if data[0]!=data[1] or digest(folders[0]/'powerworld.csv')!=digest(folders[1]/'powerworld.csv'):
                raise ValueError('Whole cold export changed')
            total+=2*len(data[0][1]);pickup=PROFILES[profile][0] or 1.
            pairs.append(dict(profile=profile,step_s=h,runs=[f.name for f in folders],rows_per_run=len(data[0][1]),
                whole_csv_identical=True,files=files,observations=observations(*data[0],pickup,PROFILES[profile][1])))
    if len(executables)!=1:raise ValueError('Reference executable changed')
    return dict(status='repeatable-exact-reference-foundation-not-runtime-or-coupled-accepted',runs=24,cold_pairs=12,
        channels=21,raw_rows=total,executable_sha256=executables.pop(),results=pairs,parameters_fitted=False,
        trajectory_acceptance=False,automatic_import_enabled=False,full_system_dynamics_run=False)

if __name__=='__main__':print(json.dumps(audit(Path.cwd()),indent=2))
