"""Localize retained state errors and replay the synthetic profile with recorded inputs.

The replay uses binary64 arithmetic and the unchanged source constants. It neither
rounds states to match observations nor modifies any runtime integration setting.
"""
import argparse
import csv
import io
import json
import zipfile
from pathlib import Path


def rows(data):
    return [{k:float(v) for k,v in row.items()} for row in csv.DictReader(io.StringIO(data.decode()))]


def audit(validation, captures):
    periods=[]
    with zipfile.ZipFile(validation) as z:
        for name in z.namelist():
            if not name.endswith('/comparison.json'):
                continue
            info=json.loads(z.read(name));base=name.rsplit('/',1)[0]
            actual=rows(z.read(base+'/actual.csv'));reference=rows(z.read(base+'/native.csv'))
            assert len(actual)==len(reference)
            segments={}
            for label,condition in [('pretrip',lambda t:t<.15),('disconnected',lambda t:.15<=t<.2),
                                    ('after_request',lambda t:t>=.2)]:
                indices=[i for i,row in enumerate(actual) if condition(row['actual_time_s'])]
                segments[label]={}
                for key in ['STATE0','STATE1','STATE3','GEN1_STATE4']:
                    index=max(indices,key=lambda i:abs(actual[i][key]-reference[i][key]))
                    segments[label][key]=dict(error=actual[index][key]-reference[index][key],
                                               actual_time=actual[index]['actual_time_s'])
            periods.append(dict(staggered=info['shared'],offline_control=info['control'],segments=segments))
    with zipfile.ZipFile(captures) as z:
        prefix='reenable-standard-h0p0005-t0p5-trip1-machines-a/'
        manifest=json.loads(z.read(prefix+'manifest.json'))
        reference=rows(z.read(prefix+'native.csv'))
        offline_reference=rows(z.read('offline-control-standard-h0p0005-t0p5-trip1-machines-a/native.csv'))
    c=manifest['initialized_constants'];h=manifest['settings']['step']
    assert c[0]>0 and c[1]>0 and c[2]>0 and c[12]>0
    assert c[7:10]==[0.,0.,0.] and c[14:19]==[0.,1.,0.,0.,0.]
    assert c[19:29]==[0.,0.,1.,1.,0.,0.,0.,0.,0.,0.]
    assert c[29]==0.  # Identity turbine curve and bypass governor lead/lag.
    def state(row):return [row['STATE'+str(k)] for k in range(5)]
    def rates(values,row):
        return [(row['GREF']-row['SPEED']/c[0]-values[0])/c[1],
                (values[0]-values[1])/c[2],0.,(values[0]-values[3])/c[12],0.]
    trip=next(i for i,row in enumerate(reference) if row['time_s']>.14 and row['PMECH']==0.)
    resume=next(i for i in range(trip+1,len(reference)) if reference[i]['PMECH']>0.)
    last=rates(state(reference[trip-1]),reference[trip-1]);older=rates(state(reference[trip-2]),reference[trip-2])
    values=state(reference[trip]);first=True;inactive_error=[0.]*5
    for i in range(trip+1,resume):
        if reference[i]['time_s']==reference[i-1]['time_s']:continue
        derivative=[1.5*a-.5*b for a,b in zip(last,older)] if first else last
        values=[x+h*f for x,f in zip(values,derivative)];first=False
        inactive_error=[max(old,abs(x-y)) for old,x,y in zip(inactive_error,values,state(reference[i]))]
    offline_values=state(offline_reference[trip]);offline_maximum=[0.]*5;first=True
    for i in range(trip+1,len(offline_reference)):
        if offline_reference[i]['time_s']==offline_reference[i-1]['time_s']:continue
        derivative=[1.5*a-.5*b for a,b in zip(last,older)] if first else last
        offline_values=[x+h*f for x,f in zip(offline_values,derivative)];first=False
        offline_maximum=[max(old,abs(x-y)) for old,x,y in zip(offline_maximum,offline_values,state(offline_reference[i]))]
    replay=[]
    for method in ['staggered','modified_euler']:
        for seed in ['reference_boundary','unrounded_disconnected_replay']:
            x=state(reference[resume]) if seed=='reference_boundary' else values.copy()
            history=last.copy();maximum=[0.]*5
            for i in range(resume,len(reference)-1):
                if reference[i+1]['time_s']==reference[i]['time_s']:continue
                derivative=rates(x,reference[i])
                if method=='staggered':
                    if i==resume:derivative=[.5*(a+b) for a,b in zip(derivative,history)]
                    x=[v+h*(1.5*f-.5*old) for v,f,old in zip(x,derivative,history)]
                    history=derivative
                else:
                    predictor=[v+h*f for v,f in zip(x,derivative)]
                    corrector=rates(predictor,reference[i+1])
                    x=[v+.5*h*(a+b) for v,a,b in zip(x,derivative,corrector)]
                maximum=[max(old,abs(v-w)) for old,v,w in zip(maximum,x,state(reference[i+1]))]
            replay.append(dict(method=method,seed=seed,maximum_error=maximum))
    return dict(periods=periods,prescribed_input_replay=replay,
                unrounded_disconnected_maximum=inactive_error,
                unrounded_offline_control_maximum=offline_maximum,
                parameters_modified=False,precision_fitting=False,
                runtime_qualification_is_separate=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('validation',type=Path)
    parser.add_argument('captures',type=Path)
    args=parser.parse_args()
    print(json.dumps(audit(args.validation,args.captures),indent=2))
