"""Audit immutable cold references and actual limiter activity, not solver parity."""
import csv
import json
import math
from pathlib import Path
import re
from genrou_native_probe import f32
from hplntd_fault_probe import sha
from regfma_limit_probe import configuration, PROFILES

HEADERS=['time_s']+['STATE_'+str(i) for i in range(9)]+['VAR_'+str(i) for i in range(8)]+[
    'V_BUS1','V_BUS2','V_BUS3','P','Q','ETERM','SPEED']
FILES={'network.raw','dynamic.dyr','probe.py','hplntd_fault_probe.py','native.log','native.out','native.csv','live-states.json'}
FAILURE=re.compile(r'network\s+not\s+converged|network\s+(?:solution\s+)?(?:did\s+not|failed\s+to)\s+converge|initial\s+conditions?\s+(?:check\s+)?suspect|undefined\s+model|record\s+ignored|traceback',re.I)

def verify(folder):
    m=json.loads((folder/'manifest.json').read_text())
    assert m['status']=='native-converged-profile' and m['model']=='REGFMA1'
    assert m['trajectory_acceptance'] is False
    cfg=configuration(m['profile']);assert m['configuration']==cfg
    assert m['step_s'] in (.0005,.00025)
    assert m['actual_constants']==m['final_constants']==list(map(f32,cfg['constants']))
    assert m['actual_icons']==[1,1]
    assert [m['allocation']['N'+k] for k in ('ICON','CON','STATE','VAR')]==[2,18,9,8]
    for key in ('library_sha256','source_document_sha256','python_sha256','wrapper_sha256'):
        assert re.fullmatch('[0-9a-f]{64}',m[key])
    assert m['source_document_sha256']=='40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d'
    assert set(m['files'])==FILES
    for name,digest in m['files'].items():assert sha(folder/name)==digest,name
    fields=(folder/'dynamic.dyr').read_text().splitlines()[0].split()
    assert fields[:5]==['1',"'REGFMA1'","'1'",'1','1'] and fields[-1]=='/'
    assert list(map(float,fields[5:-1]))==cfg['constants']
    log=(folder/'native.log').read_text(errors='replace')
    assert 'Initial conditions check O.K.' in log and not FAILURE.search(log)
    with (folder/'native.csv').open(newline='') as stream:
        reader=csv.DictReader(stream);assert reader.fieldnames==HEADERS
        rows=[{key:float(value) for key,value in row.items()} for row in reader]
    assert len(rows)==m['sample_count']==round(cfg['end_s']/m['step_s'])+5
    assert m['channel_count']==24
    assert all(math.isfinite(v) for row in rows for v in row.values())
    times=[r['time_s'] for r in rows];assert all(a<=b for a,b in zip(times,times[1:]))
    assert sum(t<0 for t in times)==2 and 0 in times
    duplicated=[b for a,b in zip(times,times[1:]) if a==b];assert len(duplicated)==2
    assert [e['action'] for e in m['events']]==['shunt-on','shunt-off']
    for nominal,event,time in zip((.05,.1),m['events'],duplicated):
        assert event['nominal_s']==nominal and abs(event['actual_s']-time)<1e-11
        assert abs(time-nominal)<m['step_s']/2
    assert abs(times[-1]-.3)<m['step_s']/2
    live=json.loads((folder/'live-states.json').read_text());assert len(live)==6
    assert [x['stage'] for x in live]==['initialized','interval-end','post-shunt-on','interval-end','post-shunt-off','interval-end']
    assert all(len(x['states'])==9 and len(x['variables'])==8 for x in live)
    assert all(math.isfinite(v) for x in live for field in ('states','variables') for v in x[field])
    integral=[r['STATE_8'] for r in rows]
    if m['profile']=='lower-shunt':
        # Allow only serialization error around the exact float32 bound;
        # this is an activity guard, not a trajectory-error tolerance.
        lower=f32(cfg['constants'][5]);assert abs(min(integral)-lower)<1e-10
        assert integral[0]>lower and integral[-1]>lower+1e-5
        plateau=[r for r in rows if abs(r['STATE_8']-lower)<1e-10]
        assert len(plateau)>10 and any(r['VAR_0']<r['STATE_5'] for r in plateau)
        assert max(r['V_BUS2'] for r in rows if .05<r['time_s']<.1)>rows[0]['V_BUS2']+.01
    else:
        for index,sign in ((1,-1),(2,1),(6,-1),(7,1)):
            values=[r['STATE_'+str(index)] for r in rows]
            assert all(sign*v>=0 for v in values) and any(sign*v>1e-10 for v in values)
        assert max(integral)-min(integral)>1e-4
    return m,dict(profile=m['profile'],step_s=m['step_s'],rows=len(rows),history_rows=2,
                  event_post_rows=2,integral_min=min(integral),integral_max=max(integral),
                  final_integral=integral[-1],trajectory_acceptance=False)

def check(root):
    results=[];identity=None
    for profile in PROFILES:
        for scale in ('coarse','fine'):
            folders=[root/'target'/('regfma-limit-final-v1-'+profile+'-'+scale+'-'+tag) for tag in ('a','b')]
            checked=[verify(p) for p in folders];a,b=[x[0] for x in checked]
            assert a['files']['probe.py']==sha(root/'tools/regfma_limit_probe.py')
            assert a['files']['hplntd_fault_probe.py']==sha(root/'tools/hplntd_fault_probe.py')
            assert {k:v for k,v in a.items() if k!='files'}=={k:v for k,v in b.items() if k!='files'},'Paired metadata differs'
            for name in ('network.raw','dynamic.dyr','native.csv','live-states.json','probe.py','hplntd_fault_probe.py'):
                assert (folders[0]/name).read_bytes()==(folders[1]/name).read_bytes(),name
            current={k:a[k] for k in ('library_sha256','source_document_sha256','python_sha256','wrapper_sha256','engine_module')}
            if identity is None:identity=current
            else:assert current==identity
            assert a['step_s']==(.0005 if scale=='coarse' else .00025)
            results.append(checked[0][1])
    return dict(cold_pairs=4,native_runs=8,results=results,trajectory_acceptance=False,
                parameters_fitted=False,raw_history_and_event_rows_retained=True,engine_identity=identity)

if __name__=='__main__':print(json.dumps(check(Path.cwd()),indent=2))
