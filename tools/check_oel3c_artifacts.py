"""Audit exact limiter records and cold repeatability; formula residuals stay diagnostic."""
import csv
import json
import math
from pathlib import Path
from check_pss5c_pole_artifacts import checked_files
from genrou_native_probe import f32
from hplntd_fault_probe import sha
from oel3c_native_probe import configuration,PROFILES

def validate_component(component,cfg):
    expected=cfg['components'][0]
    assert component['model']=='OEL3C'
    assert component['actual_icons']==list(expected['icons'])
    assert component['actual_constants']==list(map(f32,expected['constants']))
    assert [component['layout']['N'+k] for k in ('ICON','CON','STATE','VAR')]==[2,10,2,4]

def verify(folder,cfg):
    m=json.loads((folder/'manifest.json').read_text());checked_files(folder,m['files'])
    assert m['status']=='native-converged-profile' and m['model']=='GENROU'
    assert m['step_s'] in (.0005,.00025) and m['channel_count']==23
    validate_component(m['components']['MAXXL'],cfg)
    assert (folder/'dynamic.dyr').read_text()==cfg['dynamic']
    assert (folder/'network.raw').read_text()==cfg['raw']
    log=(folder/'native.log').read_text(errors='replace')
    assert 'Initial conditions check O.K.' in log
    assert all(word not in log for word in ('Network not converged','Initial conditions suspect','Undefined Model','RECORD IGNORED'))
    with (folder/'native.csv').open(newline='') as f:
        reader=csv.DictReader(f);assert len(reader.fieldnames)==24 and reader.fieldnames[0]=='time_s'
        rows=[{k:float(v) for k,v in r.items()} for r in reader]
    assert len(rows)==m['sample_count']==round(.5/m['step_s'])+5
    assert all(math.isfinite(v) for r in rows for v in r.values())
    times=[r['time_s'] for r in rows];assert times==sorted(times)
    assert sum(t<0 for t in times)==2 and sum(a==b for a,b in zip(times,times[1:]))==2
    assert 0 in times and abs(times[-1]-.5)<m['step_s']/2
    values=[r['OEL_INTEGRAL'] for r in rows]
    assert all(-1<=v<=0 for v in values)
    if m['profile']=='flat':assert all(v==0 for v in values)
    else:assert min(values)<0 and min(r['OEL_PROPORTIONAL'] for r in rows)<0
    p=cfg['components'][0]['constants'];exponent=cfg['components'][0]['icons'][1]
    maxima={k:dict(error=0.,time_s=0.) for k in ('reference','error','gained_error','proportional','zero_filter_input')}
    rounding_counterexamples=[]
    for row in rows:
        reference=p[0]**exponent;error=reference-row['OEL_INPUT']**exponent;gained=p[3]*error
        expected=dict(reference=(reference,row['OEL_REFERENCE']),error=(error,row['OEL_ERROR']),
            gained_error=(gained,row['OEL_GAINED_ERROR']),
            proportional=(p[5]*gained if row['OEL_INTEGRAL']<0 else 0,row['OEL_PROPORTIONAL']))
        if p[2]==0:expected['zero_filter_input']=(p[1]*row['EFD'],row['OEL_INPUT'])
        for key,(a,b) in expected.items():
            residual=abs(a-b)
            if residual>maxima[key]['error']:maxima[key]=dict(error=residual,time_s=row['time_s'])
        ref32=f32(f32(p[0])**exponent);err32=f32(ref32-f32(f32(row['OEL_INPUT'])**exponent))
        if err32!=f32(row['OEL_ERROR']):rounding_counterexamples.append(dict(time_s=row['time_s'],
            separately_rounded_error=err32,native_error=f32(row['OEL_ERROR']),sensed_input=f32(row['OEL_INPUT'])))
    return m,dict(profile=m['profile'],rows=len(rows),integral_min=min(values),formula_diagnostics=maxima,
                  separately_rounded_error_counterexamples=rounding_counterexamples,trajectory_acceptance=False)

def check(root):
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text();results=[]
    for profile in PROFILES:
        cfg=configuration(profile,raw)
        for scale,step in (('coarse',.0005),('fine',.00025)):
            folders=[root/'target'/('genrou-native-oel3c-foundation-v1-'+profile+'-'+scale+'-'+tag) for tag in ('a','b')]
            checked=[verify(p,cfg) for p in folders];a,b=[x[0] for x in checked]
            assert a['step_s']==b['step_s']==step
            for key in ('components','actual_constants','final_constants','library_sha256','source_document_sha256','python_sha256','wrapper_sha256'):
                assert a[key]==b[key],key
            for name in ('network.raw','dynamic.dyr','probe.py','genrou_native_probe.py','hplntd_fault_probe.py','native.csv','live-states.json'):
                assert (folders[0]/name).read_bytes()==(folders[1]/name).read_bytes(),name
            assert a['files']['probe.py']==sha(root/'tools/oel3c_native_probe.py')
            results.append(dict(step_s=step,**checked[0][1]))
    return dict(cold_pairs=8,native_runs=16,raw_rows=sum(r['rows']*2 for r in results),results=results,
                trajectory_acceptance=False,full_runtime_available=False,parameters_fitted=False)

if __name__=='__main__':print(json.dumps(check(Path.cwd()),indent=2))
