"""All-row physical/state/timer comparison on declared units and exact event sides."""
import hashlib
import json
import math
from audit_oel3c_measurement import read_measurements,rmse
from compare_oel3c_events import grid_tick,read_actual

CHANNELS={
    'V_BUS1':('Bus','1','TSVpu'),'V_BUS2':('Bus','2','TSVpu'),
    'SPEED_DEV':('Generator','1 1','TSSpeed'),'REF_SPEED_DEV':('Generator','2 1','TSSpeed'),
    'P_PU':('Generator','1 1','TSMW'),'Q_PU':('Generator','1 1','TSMvar'),
    'EFD':('Generator','1 1','TSEfd'),'IFD_EXC':('Generator','1 1','TSIfd'),
    'BAS_OUTPUT':('Generator','1 1','TSVOEL'),
}
for i,name in enumerate(('EXC_FIELD','EXC_SENSE','EXC_REGULATOR','EXC_LEAD_LAG','EXC_RATE_FEEDBACK'),1):
    CHANNELS[name]=('Generator','1 1','TSExciterState:'+str(i))
for i,name in enumerate(('BAS_INPUT','BAS_INTEGRAL','BAS_FREQUENCY'),1):CHANNELS[name]=('Generator','1 1','TSOELState:'+str(i))
OTHER_NAMES=('BAS_REFERENCE','BAS_OEL_OUTPUT','BAS_VHZ_OUTPUT',
             'BAS_T1_ACTIVE','BAS_T1_ABOVE','BAS_T1_TIME','BAS_T2_ACTIVE','BAS_T2_ABOVE','BAS_T2_TIME',
             'BAS_T3_ACTIVE','BAS_T3_ABOVE','BAS_T3_TIME')
for i,name in enumerate(OTHER_NAMES,1):CHANNELS[name]=('Generator','1 1','TSOELOther:'+str(i))
ANGLES=(('Generator','1 1','TSRotorAngle'),('Generator','2 1','TSRotorAngle'))


def initial_measurement(fields,rows,cfg):
    index={tuple(f[i].strip() for i in (0,1,4)):n+1 for n,f in enumerate(fields)}
    def value(name):return rows[0][index[('Generator','1 1',name)]]
    if not rows or rows[0][0]!=0:raise ValueError('Missing initialization')
    con=cfg['components'][0]['constants']
    if con[14]!=0 or con[16]!=0:raise ValueError('Initial Vfe identity requires the declared unsaturated exciter')
    candidates={'Efd':value('TSEfd'),'Ifd':value('TSIfd'),
                'Vfe':con[12]*value('TSExciterState:1')+con[11]*value('TSIfd')}
    intended={0:'Efd',1:'Ifd',2:'Vfe'}[cfg['components'][1]['icons'][2]]
    sensed=value('TSOELState:1');errors={name:abs(sensed-v) for name,v in candidates.items()}
    matches=[name for name,e in errors.items() if e<1e-6]
    return dict(intended=intended,observed=sensed,physical_inputs=candidates,absolute_residuals=errors,
                matching_inputs=matches,serialization_bound=1e-6,
                intended_initial_measurement_consistent=intended in matches,
                status='mismatch' if intended not in matches else 'ambiguous' if len(matches)>1 else 'consistent',
                dynamic_routing_verified=False,trajectory_acceptance=False)


def require_initial_measurement(fields,rows,cfg):
    result=initial_measurement(fields,rows,cfg)
    if not result['intended_initial_measurement_consistent']:raise ValueError('BASOEL2 physical initial input mismatch')
    return result


def compare(fields,reference,actual,step):
    if step not in (.0005,.00025):raise ValueError('Unsupported matched timestep')
    if not reference or not actual:raise ValueError('Missing trajectory')
    indices={}
    for index,field in enumerate(fields):
        if len(field)<5:raise ValueError('Malformed reference metadata')
        key=tuple(field[i].strip() for i in (0,1,4))
        if key in indices:raise ValueError('Duplicate reference field')
        indices[key]=index+1
    if set(indices)!=set(CHANNELS.values())|set(ANGLES):raise ValueError('Missing or undeclared reference channel')
    if reference[0][0]!=0 or actual[0]['time_s']!=0:raise ValueError('Missing initialization')
    if reference[-1][0]!=.5 or len(reference)!=round(.5/step)+3 or len(actual)!=len(reference):
        raise ValueError('Incomplete half-second raw fault history')
    if any(len(row)!=len(fields)+1 or not all(map(math.isfinite,row)) for row in reference):raise ValueError('Invalid raw row')
    if any(a[0]>b[0] for a,b in zip(reference,reference[1:])):raise ValueError('Nonmonotonic reference clock')
    if any(a['time_s']>b['time_s'] for a,b in zip(actual,actual[1:])):raise ValueError('Nonmonotonic actual clock')
    required=set(CHANNELS)|{'RELATIVE_ANGLE','time_s','event_side'}
    samples={}
    for row in actual:
        if not required.issubset(row):raise ValueError('Missing actual observation')
        if row['event_side'] not in ('regular','pre','post'):raise ValueError('Invalid event side')
        key=(grid_tick(row['time_s'],step),row['event_side'])
        if key in samples:raise ValueError('Duplicate actual observation')
        if any(not math.isfinite(row[name]) for name in required-{'event_side'}):raise ValueError('Nonfinite actual value')
        samples[key]=row
    event_times={a[0] for a,b in zip(reference,reference[1:]) if a[0]==b[0]}
    if event_times!={.05,.1}:raise ValueError('Expected both three-cycle fault boundaries')
    paired=[]
    for i,row in enumerate(reference):
        time=row[0];side='post' if i>0 and reference[i-1][0]==time else 'pre' if time in event_times else 'regular'
        key=(grid_tick(time,step),side)
        if key not in samples:raise ValueError('Missing matching event side: '+str(key))
        observed=samples.pop(key)
        ref={name:row[indices[field]] for name,field in CHANNELS.items()}
        for name in ('SPEED_DEV','REF_SPEED_DEV'):ref[name]-=1
        for name in ('P_PU','Q_PU'):ref[name]/=100
        ref['RELATIVE_ANGLE']=math.radians(row[indices[ANGLES[0]]]-row[indices[ANGLES[1]]])
        paired.append(dict(index=i,time_s=time,event_side=side,reference=ref,actual=observed))
    if samples:raise ValueError('Unpaired actual rows')
    metrics={}
    for name in paired[0]['reference']:
        metrics[name]={}
        for side in ('all','regular','pre','post'):
            records=[p for p in paired if side=='all' or p['event_side']==side]
            errors=[p['actual'][name]-p['reference'][name] for p in records]
            peak=max(range(len(errors)),key=lambda i:abs(errors[i]))
            metrics[name][side]=dict(rmse(errors),peak_time_s=records[peak]['time_s'],peak_event_side=records[peak]['event_side'])
    consistency={}
    for tool in ('reference','actual'):
        consistency[tool]={}
        for side in ('all','regular','pre','post'):
            records=[p for p in paired if side=='all' or p['event_side']==side]
            consistency[tool][side]=rmse(p[tool]['EXC_SENSE']-p[tool]['V_BUS1'] for p in records)
    alignment=json.dumps([(p['index'],p['time_s'],p['event_side']) for p in paired],separators=(',',':'))
    return dict(reference_rows=len(reference),actual_rows=len(actual),paired_rows=len(paired),rows_dropped=0,
        raw_reference_channels=len(fields),channels=len(metrics),metrics=metrics,zero_tr_sensor_consistency=consistency,
        unmatched_actual_diagnostic_channels=sorted(set(actual[0])-set(metrics)-{'time_s','event_side'}),
        initial_relative_angles_rad={tool:paired[0][tool]['RELATIVE_ANGLE'] for tool in ('reference','actual')},
        alignment_sha256=hashlib.sha256(alignment.encode()).hexdigest(),
        normalization='P/Q on 100 MVA base; speed deviation from nominal; relative angle radians; '
                      'timer seconds on a one-second base; timer flags dimensionless; other quantities published pu',
        numerical_rmse_pass=all(v['all']['passes_rmse'] for v in metrics.values()),trajectory_acceptance=False)


if __name__=='__main__':
    import sys
    reference,actual,step=sys.argv[1:]
    print(json.dumps(compare(*read_measurements(reference),read_actual(actual),float(step)),indent=2))
