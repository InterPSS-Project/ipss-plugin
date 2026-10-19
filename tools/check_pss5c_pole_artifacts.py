"""Coordinate-invariant two-state band response diagnostics, never acceptance bounds."""
import cmath
import hashlib
import json
import math
from pathlib import Path


def checked_files(folder,files):
    for name,digest in files.items():
        assert Path(name).name==name
        assert hashlib.sha256((folder/name).read_bytes()).hexdigest()==digest,name


def _pair(responses,offset):
    found={(entry['state_offset'],entry['sign']):entry for entry in responses}
    assert (offset,1) in found and (offset,-1) in found
    plus,minus=found[offset,1],found[offset,-1]
    a,b=plus['observations'],minus['observations']
    assert len(a)==len(b)==6
    for x,y in zip(a,b):
        assert x['time_s']==y['time_s'] and math.isfinite(x['time_s'])
        assert len(x['states'])==len(y['states'])==13
        assert all(math.isfinite(v) for v in x['states']+y['states'])
    assert a[0]['time_s']==a[1]['time_s']
    assert all(a[i]['time_s']>a[i-1]['time_s'] for i in range(2,6))
    delta=a[1]['states'][offset]-b[1]['states'][offset]
    assert delta>0
    return a,b,delta


def band_response(responses,band):
    assert band in range(4)
    offsets=(5+2*band,6+2*band)
    pairs=[_pair(responses,offset) for offset in offsets]
    for a,b,_ in pairs:
        assert [row['time_s'] for row in a]==[row['time_s'] for row in pairs[0][0]]
    def sensitivity(index):
        return [[(a[index]['states'][row]-b[index]['states'][row])/delta
                 for a,b,delta in pairs] for row in offsets]
    initial=sensitivity(1)
    assert initial==[[1.,0.],[0.,1.]],initial
    before,after=sensitivity(4),sensitivity(5) # Startup steps remain in the raw responses.
    determinant=before[0][0]*before[1][1]-before[0][1]*before[1][0]
    assert abs(determinant)>1e-12,'Singular state response, not silently regularized'
    inverse=[[before[1][1]/determinant,-before[0][1]/determinant],
             [-before[1][0]/determinant,before[0][0]/determinant]]
    transition=[[sum(after[i][k]*inverse[k][j] for k in range(2)) for j in range(2)] for i in range(2)]
    a,b=transition[0];c,d=transition[1]
    discriminant=cmath.sqrt((a-d)**2+4*b*c)
    multipliers=[(a+d+discriminant)/2,(a+d-discriminant)/2]
    interval=pairs[0][0][5]['time_s']-pairs[0][0][4]['time_s']
    assert interval>0 and all(abs(z)>0 for z in multipliers)
    rates=sorted([cmath.log(z)/interval for z in multipliers],key=lambda z:(z.real,z.imag))
    return dict(state_offsets=list(offsets),transition=transition,interval_s=interval,
                decay_rates=[dict(real=z.real,imaginary=z.imag) for z in rates],
                estimator='eigenvalues(log(S4*inverse(S3)))/actual_interval',
                trajectory_acceptance=False)


def verify(folder):
    m=json.loads((folder/'manifest.json').read_text())
    assert m['status']=='native-state-space-diagnostic' and not m['trajectory_acceptance']
    assert m['scope']=='all' and m['cases']==17
    checked_files(folder,m['files'])
    responses=json.loads((folder/'responses.json').read_text())
    assert len(responses)==17
    assert {(r['state_offset'],r['sign']) for r in responses}=={(None,0)}|{(i,s) for i in range(5,13) for s in (1,-1)}
    constants=responses[0]['constants'];assert len(constants)==30
    for case in responses:
        assert case['constants']==constants
        assert [case['layout']['N'+key] for key in ('ICON','CON','STATE','VAR')]==[1,30,13,6]
        assert len(case['observations'])==6
        for row in case['observations']:
            assert len(row['states'])==13 and len(row['variables'])==6
            assert all(math.isfinite(v) for v in row['states']+row['variables'])
        before,injected=case['observations'][:2]
        assert max(map(abs,before['states']))<m['amplitude']/100
        assert before['time_s']==injected['time_s']
        for index in range(13):
            if index!=case['state_offset']:assert before['states'][index]==injected['states'][index]
        log=(folder/(case['label']+'.log')).read_text(errors='replace')
        assert 'Initial conditions check O.K.' in log and 'Network not converged' not in log and 'NaN' not in log
    bands=[]
    for band in range(4):
        result=band_response(responses,band);frequency=constants[10+4*band]
        published=sorted([-constants[26]*frequency,-constants[25]*frequency])
        reciprocal=sorted([-constants[26]/frequency,-constants[25]/frequency])
        measured=[complex(z['real'],z['imaginary']) for z in result['decay_rates']]
        distance=lambda expected:max(abs(x-y) for x,y in zip(measured,expected))
        result.update(band=band,raw_frequency_field=frequency,published_poles=published,
                      reciprocal_frequency_hypothesis=reciprocal,
                      published_distance=distance(published),reciprocal_distance=distance(reciprocal),
                      hypothesis_selection='formulas-coincide' if published==reciprocal else
                      ('closer-to-published' if distance(published)<distance(reciprocal) else 'closer-to-reciprocal'))
        bands.append(result)
    return m,dict(profile=m['profile'],step_s=m['step_s'],amplitude=m['amplitude'],bands=bands,
                  scope='near-equilibrium discrete-response pole diagnostics, not native equation acceptance')


def check(root):
    results=[]
    identity=None
    names=[f'{profile}-{scale}' for profile in ('baseline','unity','half','double') for scale in ('coarse','fine')]
    names+=['baseline-amplitude-half']
    for name in names:
        folders=[root/'target'/('pss5c-poles-'+name+'-'+tag) for tag in ('a','b')]
        entries=[verify(folder) for folder in folders];a,b=(entry[0] for entry in entries)
        for key in ('profile','scope','step_s','amplitude','cases','library_sha256','source_document_sha256','python_sha256','wrapper_sha256','engine_module'):
            assert a[key]==b[key],(name,key)
        current={key:a[key] for key in ('library_sha256','source_document_sha256','python_sha256','wrapper_sha256','engine_module')}
        if identity is None:identity=current
        else:assert identity==current,(name,'mixed native engine identities')
        for file in ('responses.json','network.raw','dynamic.dyr','probe.py'):
            assert (folders[0]/file).read_bytes()==(folders[1]/file).read_bytes(),(name,file)
        assert entries[0][1]==entries[1][1]
        results.append(dict(name=name,**entries[0][1]))
    return dict(cold_pairs=len(names),independent_cases=17*2*len(names),engine_identity=identity,results=results,
                trajectory_acceptance=False,parameters_fitted=False,state_coordinates_fitted=False)


if __name__=='__main__':print(json.dumps(check(Path.cwd()),indent=2))
