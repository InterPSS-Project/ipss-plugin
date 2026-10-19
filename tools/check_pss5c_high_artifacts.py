"""Raw high-transducer order counterexamples, never fitted acceptance bounds."""
import cmath
import json
import math
import struct
from pathlib import Path
from check_pss5c_pole_artifacts import _pair, two_state_response
from check_pss5c_sensor_artifacts import verify

PROFILES=('high-no-lag','high-no-quadratic')


def high_response(responses,constants):
    classifications={};pairs={i:_pair(responses,i) for i in range(2,5)}
    for offset,(plus,minus,delta) in pairs.items():
        erased=True;frozen=True
        for a,b in zip(plus[2:],minus[2:]):
            difference=[x-y for x,y in zip(a['states'],b['states'])]
            variable=[x-y for x,y in zip(a['variables'],b['variables'])]
            assert all(math.isfinite(v) for v in variable)
            erased &= all(v==0 for v in difference+variable)
            frozen &= all(v==(delta if i==offset else 0) for i,v in enumerate(difference)) and all(v==0 for v in variable)
        classifications[offset]='erased' if erased else ('frozen-unobserved' if frozen else 'active')
    active=[i for i in range(2,5) if classifications[i]=='active']
    result=dict(coordinate_responses=classifications,active_coordinate_count=len(active),
                observed_poles=None,observation_coordinates_certified=False)
    if len(active)==2:
        measured=two_state_response(responses,tuple(active))
        result.update(subspace=measured,observed_poles=measured['decay_rates'])
    elif len(active)==1:
        offset=active[0];a,b,_=pairs[offset]
        before=a[4]['states'][offset]-b[4]['states'][offset]
        after=a[5]['states'][offset]-b[5]['states'][offset]
        assert before!=0 and after/before>0
        interval=a[5]['time_s']-a[4]['time_s']
        result['observed_poles']=[dict(real=math.log(after/before)/interval,imaginary=0.)]
    # Three active coordinates are not reduced by selecting a convenient pair.
    quadratic=(constants[5]+constants[4]*constants[6])/constants[7]
    linear=constants[4]+constants[6]/constants[7]
    assert quadratic>0 and constants[4]*constants[5]==0
    root=cmath.sqrt(linear*linear-4*quadratic)
    result.update(published_order=2,published_poles=[dict(real=z.real,imaginary=z.imag) for z in
                  ((-linear-root)/(2*quadratic),(-linear+root)/(2*quadratic))],
                  trajectory_acceptance=False,parameters_fitted=False)
    return result


def check(root):
    from pss5c_native_probe import PSS_CON
    from pss5c_native_pole_probe import TRANSDUCERS
    results=[];identity=None
    for profile in PROFILES:
        for scale in ('coarse','fine'):
            name=profile+'-'+scale
            folders=[root/'target'/('pss5c-poles-high-v1-'+name+'-'+tag) for tag in ('a','b')]
            entries=[verify(p) for p in folders];a,b=(v[0] for v in entries)
            for key in ('profile','scope','step_s','amplitude','cases','library_sha256','source_document_sha256','python_sha256','wrapper_sha256','engine_module'):
                assert a[key]==b[key],(name,key)
            current={k:a[k] for k in ('library_sha256','source_document_sha256','python_sha256','wrapper_sha256','engine_module')}
            if identity is None:identity=current
            else:assert identity==current
            for file in ('responses.json','network.raw','dynamic.dyr','probe.py'):
                assert (folders[0]/file).read_bytes()==(folders[1]/file).read_bytes(),(name,file)
            responses=[json.loads((p/'responses.json').read_text()) for p in folders]
            expected=list(PSS_CON);offset,factor=TRANSDUCERS[profile];expected[offset]*=factor
            f32=lambda v:struct.unpack('f',struct.pack('f',v))[0]
            assert responses[0][0]['constants']==list(map(f32,expected))
            measured=[high_response(rows,rows[0]['constants']) for rows in responses]
            assert measured[0]==measured[1]
            results.append(dict(name=name,profile=profile,step_s=a['step_s'],high=measured[0]))
    return dict(cold_pairs=len(results),independent_cases=22*len(results),engine_identity=identity,
                results=results,trajectory_acceptance=False,parameters_fitted=False)


if __name__=='__main__':print(json.dumps(check(Path.cwd()),indent=2))
