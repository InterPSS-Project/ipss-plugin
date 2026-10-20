"""Reject nonfinite static-low oracles; retain exact preflight and healthy controls."""
import json
import re
from pathlib import Path
from check_pss5c_pole_artifacts import checked_files
from check_pss5c_native_artifacts import verify as verify_control
from pss5c_native_probe import configuration
from genrou_native_probe import f32

PROFILES=('static-low-flat','static-low-fault')


def rejected(folder,expected):
    assert not (folder/'manifest.json').exists() and not (folder/'native.csv').exists(), 'Do not promote or fabricate an invalid trajectory'
    m=json.loads((folder/'rejected-run.json').read_text());checked_files(folder,m['files'])
    assert {'network.raw','dynamic.dyr','probe.py','genrou_native_probe.py','native.log','native.out','diagnostic-preflight.json'}<=set(m['files'])
    assert m['status']=='rejected-native-probe' and not m['trajectory_acceptance'] and m['exception_type']=='TypeError'
    assert 'diagnostic-preflight.json' in m['files']
    pre=json.loads((folder/'diagnostic-preflight.json').read_text())
    assert pre['status']=='native-diagnostic-preflight-not-trajectory-acceptance' and not pre['trajectory_acceptance']
    assert all(re.fullmatch('[0-9a-f]{64}',pre[key]) for key in ('library_sha256','source_document_sha256','python_sha256','wrapper_sha256'))
    assert pre['engine_module']
    comp=pre['components']['STAB'];assert comp['model']=='PSS5C'
    assert comp['actual_constants']==list(map(f32,expected)) and comp['actual_icons']==[0]
    assert [comp['layout']['N'+k] for k in ('ICON','CON','STATE','VAR')]==[1,30,13,6]
    fields=next(line.split() for line in (folder/'dynamic.dyr').read_text().splitlines() if "'PSS5C'" in line)
    assert len(fields[3:-1])==30 and list(map(float,fields[3:-1]))==expected
    log=(folder/'native.log').read_text(errors='replace')
    assert 'Initial conditions check O.K.' in log
    assert re.search(r'NaN\s+PSS_LOW_SIGNAL',log),'Export exception alone does not establish a model-specific nonfinite response'
    assert 'Undefined Model' not in log and 'RECORD IGNORED' not in log
    assert (folder/'native.out').stat().st_size>0
    return pre


def check(root):
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text();result=[];identity=None
    for profile in PROFILES:
        expected=configuration(profile,raw)['components'][1]['constants']
        assert expected[:3]==[0.,0.,0.]
        for scale,step in (('coarse',.0005),('fine',.00025)):
            name=profile+'-'+scale
            folders=[root/'target'/('pss5c-native-static-v3-'+name+'-'+tag) for tag in ('a','b')]
            entries=[rejected(p,expected) for p in folders]
            assert entries[0]==entries[1] and entries[0]['step_s']==step
            for file in ('network.raw','dynamic.dyr','probe.py','genrou_native_probe.py','diagnostic-preflight.json'):
                assert (folders[0]/file).read_bytes()==(folders[1]/file).read_bytes(),(name,file)
            current={key:entries[0][key] for key in ('library_sha256','source_document_sha256','python_sha256','wrapper_sha256','engine_module')}
            if identity is None:identity=current
            else:assert current==identity
            result.append(dict(profile=name,step_s=step,status='paired-nonfinite-rejection',trajectory_acceptance=False))
    controls=[root/'target'/('pss5c-native-static-v3-control-flat-'+tag) for tag in ('a','b')]
    accepted=[verify_control(p)[0] for p in controls]
    for m in accepted:
        assert m['profile']=='flat' and {key:m[key] for key in identity}==identity
    for file in ('network.raw','dynamic.dyr','native.csv','live-states.json'):
        assert (controls[0]/file).read_bytes()==(controls[1]/file).read_bytes(),file
    return dict(paired_rejections=4,rejected_native_runs=8,healthy_control_runs=2,control_samples=accepted[0]['sample_count'],
                engine_identity=identity,results=result,trajectory_acceptance=False,parameters_fitted=False,
                replay_generated_from_rejected_native=False)


if __name__=='__main__':print(json.dumps(check(Path.cwd()),indent=2))
