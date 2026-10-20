"""Whole-file repeatability, exact schema translation and readback quality checks."""
import csv,hashlib,json,math,re
from pathlib import Path
from oel3c_routing_probe import configuration
from oel3c_powerworld_probe import translate,validate_readback,validate_options

def canonical(path):return path.read_bytes().replace(b'\r\n',b'\n').replace(b'\r',b'\n')
def digest(path):return hashlib.sha256(canonical(path)).hexdigest()
def trajectory(path):
    with path.open(newline='') as stream:raw=list(csv.reader(stream))
    if raw[:1]!=[['ObjectFields']]:raise ValueError('Missing metadata')
    end=raw.index(['END']);fields=raw[2:end];rows=[]
    if len(fields)!=12:raise ValueError('Unexpected channel count')
    for row in raw[end+2:]:
        if row==['END']:break
        if row and row[-1]=='':row=row[:-1]
        if len(row)!=13:raise ValueError('Unexpected numerical cardinality')
        values=list(map(float,row))
        if any(not math.isfinite(v) for v in values):raise ValueError('Nonfinite reference output')
        rows.append(values)
    times=[r[0] for r in rows]
    if times!=sorted(times) or sum(a==b for a,b in zip(times,times[1:]))!=2:raise ValueError('Event row accounting failed')
    return fields,rows

def check(root,prefix='final-v2'):
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text();results=[]
    for profile in ('efd-fault','ifd-fault','vfe-fault'):
        cfg=configuration(profile,raw)
        for scale,step in (('coarse',.0005),('fine',.00025)):
            folders=[root/'target'/('oel3c-powerworld-'+prefix+'-'+profile+'-'+scale+'-'+repeat) for repeat in ('a','b')]
            pairs=[]
            for folder in folders:
                m=json.loads((folder/'manifest.json').read_text())
                assert m['status']=='complete' and not m['auto_correct'] and not m['powerworld_model_overrides']
                assert m['simulation']==dict(start_s=0,stop_s=.5,step_s=step)
                for name,metadata in m['artifacts'].items():assert digest(folder/name)==metadata['sha256'],name
                for metadata in m['inputs'].values():assert digest(root/metadata['path'])==metadata['sha256']
                assert (folder/'source.dyr').read_text()==cfg['dynamic']
                assert (folder/'translated.dyr').read_text()==translate(cfg)
                assert canonical(folder/'inputs/powerworld.dyr')==canonical(folder/'translated.dyr')
                for marker in ('power_flow_solved','initialized'):
                    text=(folder/(marker+'-models.aux')).read_text();validate_readback(text,cfg);validate_options(text)
                log=(folder/'powerworld.log').read_text()
                assert re.search(r'Validation of .* completed with\s+0 errors and\s+0 warnings',log)
                assert 'TSAutoCorrect' not in (folder/'run.aux').read_text()
                fields,rows=trajectory(folder/'powerworld.csv')
                assert len(rows)==round(.5/step)+3 and rows[0][0]==0 and rows[-1][0]==.5
                pairs.append((fields,rows))
            assert pairs[0]==pairs[1],(profile,scale)
            assert canonical(folders[0]/'powerworld.csv')==canonical(folders[1]/'powerworld.csv')
            results.append(dict(profile=profile,step_s=step,rows_per_run=len(pairs[0][1]),channels=12,
                csv_sha256=digest(folders[0]/'powerworld.csv'),trajectory_acceptance=False,
                files=[dict(run=f.name,hashes={p.name:digest(p) for p in f.iterdir() if p.is_file() and p.suffix in ('.json','.py','.aux','.dyr','.csv','.log')}) for f in folders]))
    return dict(runs=12,cold_pairs=6,raw_rows=sum(r['rows_per_run']*2 for r in results),results=results,
        trajectory_acceptance=False,parameters_fitted=False,explicit_translation=True,load_model='Impedance',integration_method='RK2')

if __name__=='__main__':print(json.dumps(check(Path.cwd()),indent=2))
