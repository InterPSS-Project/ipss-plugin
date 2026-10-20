"""Cold-reference integrity and full event-side diagnostics; no acceptance is inferred from finiteness."""
import hashlib
import json
import re
from pathlib import Path
from basoel2_native_probe import configuration
from basoel2_powerworld_probe import validate_readback
from compare_basoel2_powerworld import compare,read_measurements,read_actual,initial_measurement

PROFILES=('efd-fault','ifd-fault','vfe-fault','ifd-zero-filter','disabled-fault')


def digest(path,canonical=False):
    value=Path(path).read_bytes()
    if canonical:value=value.replace(b'\r\n',b'\n').replace(b'\r',b'\n')
    return hashlib.sha256(value).hexdigest()


def audit(root,actual=None):
    root=Path(root);raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    if float(raw.splitlines()[0].split(',')[1])!=100:raise ValueError('Comparison requires the declared 100 MVA base')
    pairs=[];raw_rows=0;executables=set()
    for profile in PROFILES:
        cfg=configuration(profile,raw)
        for scale,h in (('coarse',.0005),('fine',.00025)):
            folders=[root/'target'/('basoel2-powerworld-final-v1-'+profile+'-'+scale+'-'+r) for r in ('a','b')]
            data=[];files=[]
            for folder in folders:
                m=json.loads((folder/'manifest.json').read_text())
                if m['status']!='complete' or m['auto_correct'] or m['powerworld_model_overrides']:
                    raise ValueError('Reference quality/correction contract violated')
                if m['simulation']!=dict(start_s=0,stop_s=.5,step_s=h):raise ValueError('Reference clock contract changed')
                executables.add(m['powerworld_executable']['sha256'])
                for name,value in m['artifacts'].items():
                    if Path(name).name!=name or digest(folder/name,True)!=value['sha256']:raise ValueError('Artifact hash changed')
                for value in m['inputs'].values():
                    if digest(root/value['path'],True)!=value['sha256']:raise ValueError('Input hash changed')
                if (folder/'source.dyr').read_text()!=cfg['dynamic']:raise ValueError('Source parameters changed')
                for stage in ('power_flow_solved','initialized'):
                    validate_readback((folder/(stage+'-models.aux')).read_text(),cfg)
                if not re.search(r'Validation of .* completed with\s+0 errors and\s+0 warnings',(folder/'powerworld.log').read_text()):
                    raise ValueError('Reference validation was not clean')
                fields,rows=read_measurements(folder/'powerworld.csv')
                if len(fields)!=31 or len(rows)!=round(.5/h)+3:raise ValueError('Reference dimensions changed')
                data.append((fields,rows));files.append({p.name:digest(p) for p in folder.iterdir() if p.is_file()})
            if data[0]!=data[1] or digest(folders[0]/'powerworld.csv')!=digest(folders[1]/'powerworld.csv'):
                raise ValueError('Whole cold export changed')
            raw_rows+=2*len(data[0][1])
            pair=dict(profile=profile,step_s=h,runs=[f.name for f in folders],rows_per_run=len(data[0][1]),
                      channels=31,whole_csv_identical=True,files=files,
                      initial_measurement=initial_measurement(*data[0],cfg))
            if actual is not None:
                name=profile+'-POWERWORLD_DOCUMENTED-'+('5.0E-4' if h==.0005 else '2.5E-4')+'.csv'
                pair['comparison']=compare(*data[0],read_actual(Path(actual)/name),h)
            pairs.append(pair)
    if len(executables)!=1:raise ValueError('Reference executable changed during the matrix')
    return dict(status='repeatable-independent-reference-coupled-acceptance-separate',runs=20,cold_pairs=10,
                raw_rows=raw_rows,results=pairs,executable_sha256=executables.pop(),parameters_fitted=False,
                trajectory_acceptance=False,automatic_native_import_enabled=False,full_system_dynamics_run=False)


if __name__=='__main__':
    import sys
    print(json.dumps(audit(Path.cwd(),sys.argv[1] if len(sys.argv)>1 else None),indent=2))
