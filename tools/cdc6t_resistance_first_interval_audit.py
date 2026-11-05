"""Retained first-event recurrence discriminator; never changes integration or acceptance gates."""
import argparse,csv,hashlib,io,json,math,zipfile
from pathlib import Path

def rows(data):return [{k:float(v) for k,v in r.items()} for r in csv.DictReader(io.StringIO(data.decode()))]
def sha(path):
    with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def ulp32(v):return 2.**(math.floor(math.log2(abs(v)))-23) if v else 2.**-149

def audit(captures,validation):
    assert sha(captures)=='ebfbee3b692c0530014813678f8284f2d4c2cab59632e0afc14caaf66278e534'
    assert sha(validation)=='c5b44a01040b53c906fa5f8cd9192cd93039a1ac1b0e61ec41ef2a33dba09562'
    result=[]
    with zipfile.ZipFile(captures) as source,zipfile.ZipFile(validation) as actual:
        for end in ['zero','rectifier','inverter']:
            prefix=end+'/fault/a/'
            ref=rows(source.read(prefix+'native.csv'))
            metadata=json.loads(source.read(prefix+'manifest.json'))
            snapshots=json.loads(source.read(prefix+'snapshots.json'))
            h=snapshots[0]['step']
            sides=[r for r in ref if abs(r['time_s']-.05)<h/4];assert len(sides)==2
            left,right=sides;prior=max((r for r in ref if r['time_s']<left['time_s']),key=lambda r:r['time_s'])
            following=next(r for r in ref if r['time_s']>right['time_s'])
            for method in ['false','true']:
                names=[n for n in actual.namelist() if n.startswith('full-comparisons/'+end+'-fault-'+method+'-') and n.endswith('/trajectory.csv')]
                assert names
                data=actual.read(names[0]);assert all(actual.read(n)==data for n in names)
                trace=rows(data);a0=[r for r in trace if abs(r['time_s']-.05)<h/4][-1]
                a1=next(r for r in trace if r['time_s']>a0['time_s'])
                item=dict(end=end,staggered=method=='true',source_rows=len(ref),actual_rows=len(trace),source_event_sides=sides,
                    source_prior=prior,source_next=following,actual_event=a0,actual_next=a1,
                    source_clock_increment=following['time_s']-right['time_s'],source_step=h,actual_clock_increment=a1['time_s']-a0['time_s'],coordinates={})
                for state,signal,constant in [('STATE0','VAR9',2),('STATE2','VAR10',31)]:
                    tau=metadata['initialized_constants'][constant]
                    assert left[state]==right[state]
                    f_previous=(prior[signal]-prior[state])/tau;f_left=(left[signal]-left[state])/tau;f_right=(right[signal]-right[state])/tau
                    assert abs(f_previous)<1e-9 and abs(f_left)<1e-9
                    # Established two-step rule uses accepted left/right averaged event rate,
                    # while its older derivative history remains the prefault rate.
                    f_event=.5*(f_left+f_right)
                    predicted=right[state]+.5*h*(3*f_event-f_previous)
                    error=following[state]-predicted
                    bound=.5*ulp32(following[state])+h/tau*ulp32(right[signal])
                    assert abs(error)<=bound,(end,state,error,bound)
                    item['coordinates'][state]=dict(previous_rate=f_previous,left_rate=f_left,right_rate=f_right,
                        accepted_event_rate=f_event,two_step_predicted=predicted,source_recurrence_error=error,representation_bound=bound,
                        actual_increment=a1[state]-a0[state],source_increment=following[state]-right[state],
                        full_right_rate_euler_increment=h*f_right)
                item['voltage_peaks']={}
                for key in ['VAR10','VAR9']:
                    paired=list(zip(ref,trace));assert len(ref)==len(trace)
                    peak=max(paired,key=lambda pair:abs(pair[1][key]-pair[0][key]))
                    item['voltage_peaks'][key]=dict(error=peak[1][key]-peak[0][key],source_time=peak[0]['time_s'],actual_time=peak[1]['time_s'],unchanged_limit_volts=10 if method=='false' else 2.9)
                    if method=='false':assert peak[0]['time_s']==following['time_s']
                result.append(item)
    return {'scope':'Known event/two-step recurrence localizes selected first interval; no full-trajectory acceptance and no fitted coefficient.',
      'method_limitation':'Actual default-ME predictor-stage input is not recorded here; Heun formula is established by the separate existing runtime contract tests, not inferred from these endpoints.',
      'units':'time seconds; voltage/state volts; rates volts per second','profiles':result}

def main():
    p=argparse.ArgumentParser();p.add_argument('--evidence',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    result=audit(a.evidence/'captures.zip',a.evidence/'validation.zip');a.output.write_text(json.dumps(result,indent=2))
    print('Six retained method/profile diagnostics passed; no simulations or native calls executed.')
if __name__=='__main__':main()
