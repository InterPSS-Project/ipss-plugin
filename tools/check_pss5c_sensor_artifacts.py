"""Exact-file sensor-order discriminators; no fitted parameters or trajectory gate."""
import cmath
import json
import math
from pathlib import Path
from check_pss5c_pole_artifacts import _pair, checked_files, two_state_response

PROFILES=(('baseline','coarse'),('baseline','fine'),('al-double','coarse'),
          ('dli-half','coarse'),('dli-half','fine'),('bl-double','fine'),
          ('explicit-low-first','coarse'),('explicit-low-first','fine'))


def low_response(responses,constants):
    pairs=[_pair(responses,i) for i in (0,1)]
    # Exact signed-response erasure is retained as evidence, not regularized.
    plus,minus,_=pairs[1]
    erased=all(a['states']==b['states'] and a['variables']==b['variables']
               for a,b in zip(plus[2:],minus[2:]))
    if erased:
        plus,minus,_=pairs[0]
        before=plus[4]['states'][0]-minus[4]['states'][0]
        after=plus[5]['states'][0]-minus[5]['states'][0]
        assert before!=0 and after/before>0
        interval=plus[5]['time_s']-plus[4]['time_s']
        result=dict(observed_order=1,second_state_response_erased=True,
                    decay_rates=[dict(real=math.log(after/before)/interval,imaginary=0.)])
    else:
        result=dict(observed_order=2,second_state_response_erased=False,
                    **two_state_response(responses,(0,1)))
    a,b=constants[1:3]
    if a>0:
        root=cmath.sqrt(b*b-4*a)
        poles=[(-b-root)/(2*a),(-b+root)/(2*a)]
    else:
        assert a==0 and b>0
        poles=[complex(-1/b)]
    result.update(published_order=len(poles),published_poles=[dict(real=p.real,imaginary=p.imag) for p in poles],
                  raw_quadratic_coefficient=a,raw_linear_coefficient=b,
                  trajectory_acceptance=False,parameters_fitted=False)
    return result


def verify(folder):
    manifest=json.loads((folder/'manifest.json').read_text())
    assert manifest['status']=='native-state-space-diagnostic' and not manifest['trajectory_acceptance']
    assert manifest['scope']=='sensors' and manifest['cases']==11
    checked_files(folder,manifest['files'])
    responses=json.loads((folder/'responses.json').read_text())
    assert len(responses)==11
    assert {(r['state_offset'],r['sign']) for r in responses}=={(None,0)}|{(i,s) for i in range(5) for s in (1,-1)}
    constants=responses[0]['constants'];assert len(constants)==30
    for case in responses:
        assert case['constants']==constants
        assert [case['layout']['N'+k] for k in ('ICON','CON','STATE','VAR')]==[1,30,13,6]
        assert len(case['observations'])==6
        for row in case['observations']:
            assert len(row['states'])==13 and len(row['variables'])==6
            assert all(math.isfinite(v) for v in row['states']+row['variables'])
        before,injected=case['observations'][:2]
        assert max(map(abs,before['states']))<manifest['amplitude']/100
        for i in range(13):
            if i!=case['state_offset']:assert before['states'][i]==injected['states'][i]
        log=(folder/(case['label']+'.log')).read_text(errors='replace')
        assert 'Initial conditions check O.K.' in log and 'NaN' not in log and 'Network not converged' not in log
    return manifest,dict(profile=manifest['profile'],step_s=manifest['step_s'],low=low_response(responses,constants))


def check(root):
    from pss5c_native_probe import PSS_CON
    from pss5c_native_pole_probe import TRANSDUCERS
    results=[];identity=None
    for profile,scale in PROFILES:
        name=profile+'-'+scale
        folders=[root/'target'/('pss5c-poles-sensor-v2-'+name+'-'+tag) for tag in ('a','b')]
        entries=[verify(p) for p in folders];a,b=(v[0] for v in entries)
        for key in ('profile','scope','step_s','amplitude','cases','library_sha256','source_document_sha256','python_sha256','wrapper_sha256','engine_module'):
            assert a[key]==b[key],(name,key)
        current={k:a[k] for k in ('library_sha256','source_document_sha256','python_sha256','wrapper_sha256','engine_module')}
        if identity is None:identity=current
        else:assert identity==current
        for file in ('responses.json','network.raw','dynamic.dyr','probe.py'):
            assert (folders[0]/file).read_bytes()==(folders[1]/file).read_bytes(),(name,file)
        assert entries[0][1]==entries[1][1]
        row=json.loads((folders[0]/'responses.json').read_text())[0]
        expected=list(PSS_CON)
        if profile in TRANSDUCERS:
            i,factor=TRANSDUCERS[profile];expected[i]*=factor
        import struct
        f32=lambda v:struct.unpack('f',struct.pack('f',v))[0]
        assert row['constants']==list(map(f32,expected)),'Only the declared single coefficient changes'
        results.append(dict(name=name,**entries[0][1]))
    return dict(cold_pairs=len(results),independent_cases=22*len(results),engine_identity=identity,
                results=results,trajectory_acceptance=False,parameters_fitted=False)


if __name__=='__main__':print(json.dumps(check(Path.cwd()),indent=2))
