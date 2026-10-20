"""All-row hybrid-switch diagnostics; preserve pointwise errors and do not fit clocks."""
import bisect
import csv
import json
import math
from pathlib import Path

def proportional(integral,gained_error,kp):
    return kp*gained_error if integral<0 else 0.

def read(path):
    with path.open(newline='') as stream:
        rows=[{k:float(v) for k,v in row.items()} for row in csv.DictReader(stream)]
    if not rows or any(not math.isfinite(v) for row in rows for v in row.values()):raise ValueError('Invalid trajectory')
    times=[r['time_s'] for r in rows]
    if times!=sorted(times):raise ValueError('Nonmonotonic trajectory')
    return rows

def transitions(rows):
    return [dict(before=a['time_s'],after=b['time_s'],active=b['OEL_INTEGRAL']<0)
        for a,b in zip(rows,rows[1:]) if (a['OEL_INTEGRAL']<0)!=(b['OEL_INTEGRAL']<0)]

def diagnose(reference,actual,kp=1.):
    if any(abs(r['OEL_PROPORTIONAL']-proportional(r['OEL_INTEGRAL'],r['OEL_GAINED_ERROR'],kp))>1e-12
        for rows in (reference,actual) for r in rows):raise ValueError('Proportional equation violated')
    times=[r['time_s'] for r in actual];witnesses=[];maxima={};counts={}
    event_times={a['time_s'] for a,b in zip(reference,reference[1:]) if a['time_s']==b['time_s']}
    for index,row in enumerate(reference):
        time=row['time_s'];hi=min(len(actual)-1,bisect.bisect_left(times,time));lo=max(0,hi-1)
        fraction=0 if hi==lo else max(0,min(1,(time-times[lo])/(times[hi]-times[lo])))
        interp=lambda key:actual[lo][key]+fraction*(actual[hi][key]-actual[lo][key])
        a_integral=interp('OEL_INTEGRAL');a_error=interp('OEL_GAINED_ERROR')
        matching_branch=(a_integral<0)==(row['OEL_INTEGRAL']<0)
        side='history' if time<0 else 'event-occurrence' if time in event_times else 'terminal-hold' if time>times[-1] else 'same-branch' if matching_branch else 'different-branch'
        counts[side]=counts.get(side,0)+1
        linear_error=abs(interp('OEL_PROPORTIONAL')-row['OEL_PROPORTIONAL'])
        algebraic_error=abs(proportional(a_integral,a_error,kp)-row['OEL_PROPORTIONAL'])
        for kind,error in (('linear',linear_error),('algebraic',algebraic_error)):
            key=side+':'+kind
            if key not in maxima or error>maxima[key]['absolute']:maxima[key]=dict(absolute=error,time_s=time,raw_index=index)
        if not matching_branch or (actual[lo]['OEL_INTEGRAL']<0)!=(actual[hi]['OEL_INTEGRAL']<0):
            witnesses.append(dict(raw_index=index,raw_time_s=time,actual_bracket=[times[lo],times[hi]],fraction=fraction,
                actual_integral=a_integral,reference_integral=row['OEL_INTEGRAL'],matching_branch=matching_branch,
                linear_error=linear_error,algebraic_error=algebraic_error))
    a_transitions=transitions(actual);r_transitions=transitions(reference)
    return dict(reference_rows=len(reference),actual_rows=len(actual),counts=counts,maxima=maxima,witnesses=witnesses,
        actual_transitions=a_transitions,reference_transitions=r_transitions,
        exact_proportional_equation_both_traces=True,rows_dropped=0,trajectory_acceptance=False,
        observation_protocol='Unmodified raw clocks, linear interpolation versus algebraic reevaluation; all rows retained; no switch-time fitting')

def audit(root):
    result=[]
    for selector,name in ((1,'efd'),(2,'ifd'),(3,'vfe')):
        for scale,h in (('coarse',.0005),('fine',.00025)):
            profile=name+'-fault';folder=root/'target'/('genrou-native-oel3c-routing-final-v1-'+profile+'-'+scale+'-a')
            path=next(p for p in (root/'target/oel3c-runtime-smib-7').glob(f'input{selector}-tf0.0-h*-fault.csv') if float(p.name.split('-h')[1].split('-fault')[0])==h)
            result.append(dict(profile=profile,step_s=h,**diagnose(read(folder/'native.csv'),read(path))))
    return result

if __name__=='__main__':print(json.dumps(audit(Path.cwd()),indent=2))
