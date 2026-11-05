"""Classify overlap observations by independently known event owners, preserving ambiguity."""
import argparse,csv,hashlib,io,json,math,zipfile
from pathlib import Path
from collections import defaultdict,Counter

def compare(metadata,reference):
    h=.0005
    clock_bound=1e-5  # Existing half-second reference-clock envelope; not exact time equality.
    supported=['V7','A7','V9','A9','STATE0','STATE1','STATE2','VAR5','VAR6','VAR12','VAR7','VAR8','VAR13','VAR10','VAR9']
    keys=metadata['columns']
    if not keys or len(set(keys))!=len(keys) or not set(keys)<=set(supported):raise ValueError('Invalid channel schema')
    def finite(values):
        if not all(isinstance(v,(int,float)) and not isinstance(v,bool) and math.isfinite(v) for v in values):raise ValueError('Nonfinite or nonnumeric data')
    def ordered(times):
        finite(times)
        if any(b<a for a,b in zip(times,times[1:])):raise ValueError('Observations must be chronological')
    if not reference:raise ValueError('Empty reference')
    schema=set(reference[0])
    if not set(keys+['time_s'])<=schema:raise ValueError('Missing reference channels')
    for row in reference:
        if set(row)!=schema:raise ValueError('Changing reference schema')
        finite(row.values())
    for row in metadata['regular_rows']:
        if len(row)!=len(keys)+1:raise ValueError('Actual row width differs from channel schema')
        finite(row)
    ordered([r['time_s'] for r in reference]);ordered([r[0] for r in metadata['regular_rows']])
    ordered([e['time'] for e in metadata['internal_transitions']]);ordered(metadata['external_schedule'])
    fields={'rectifierReal','rectifierImaginary','inverterReal','inverterImaginary','measuredInverterVolts','measuredAmperes','measuredRectifierVolts','rectifierP','rectifierQ','alphaRadians','inverterP','inverterQ','gammaRadians','rectifierVolts','inverterVolts'}
    for event in metadata['internal_transitions']:
        for side in ['before','after']:
            if set(event[side])!=fields:raise ValueError('Incomplete internal boundary schema')
            finite(event[side].values())
    def tick(t):
        k=round(t/h)
        if abs(t-k*h)>clock_bound:raise ValueError('Clock outside declared sample-index residual bound')
        return k
    rg=defaultdict(list);ag=defaultdict(list);events=defaultdict(list)
    for i,r in enumerate(reference):rg[tick(r['time_s'])].append((i,r))
    for i,r in enumerate(metadata['regular_rows']):ag[tick(r[0])].append((i,r))
    for i,e in enumerate(metadata['internal_transitions']):events[tick(e['time'])].append((i,e))
    external={tick(t) for t in metadata['external_schedule']}
    records=[];maxima={k:0. for k in keys};usage=Counter();used_regular=set();used_internal=set()
    def boundary(b):
        values=[math.hypot(b['rectifierReal'],b['rectifierImaginary']),math.degrees(math.atan2(b['rectifierImaginary'],b['rectifierReal'])),
            math.hypot(b['inverterReal'],b['inverterImaginary']),math.degrees(math.atan2(b['inverterImaginary'],b['inverterReal'])),
            b['measuredInverterVolts'],b['measuredAmperes'],b['measuredRectifierVolts'],b['rectifierP'],b['rectifierQ'],math.degrees(b['alphaRadians']),
            b['inverterP'],b['inverterQ'],math.degrees(b['gammaRadians']),b['rectifierVolts'],b['inverterVolts']]
        mapped=dict(zip(supported,values));return [mapped[k] for k in keys]
    for k,group in rg.items():
        actual=ag.get(k,[]);internal=events.get(k,[])
        candidates=[];owner='AMBIGUOUS';reason='nonunique-observation-count-or-owner'
        if k in external:
            if not internal and len(group)==2 and len(actual)==2:
                owner='EXTERNAL';candidates=[(str(i),r[0],r[1:]) for i,r in actual]
        elif len(group)==2 and len(internal)==1:
            owner='INTERNAL';i,e=internal[0]
            candidates=[(str(i)+'.'+side,e['time'],boundary(e[side])) for side in ['before','after']]
        elif len(group)==1 and len(actual)==1 and not internal:
            owner='ENDPOINT';i,r=actual[0];candidates=[(str(i),r[0],r[1:])]
        for position,(index,row) in enumerate(group):
            item={'reference_row':index,'reference_time':row['time_s'],'reference_clock_residual':row['time_s']-k*h,'tick':k,'owner':owner,
                'side':('PRE' if position==0 else 'POST') if len(group)==2 else 'SINGLE',
                'reference_group_size':len(group),'actual_regular_group_size':len(actual),'actual_internal_events':len(internal),'owner_candidates':('EXTERNAL+INTERNAL' if k in external and internal else 'EXTERNAL' if k in external else 'INTERNAL' if internal or len(group)>1 else 'ENDPOINT'),
                'reason':reason if owner=='AMBIGUOUS' else 'unique-owner-and-side-count'}
            if candidates:
                identity,t,values=candidates[position]
                if len(values)!=len(keys):raise ValueError('Paired channel width mismatch')
                item.update(actual_id=identity,actual_time=t,actual_clock_residual=t-k*h,reference_clock_residual=row['time_s']-k*h,clock_difference=t-row['time_s'])
                (used_internal if owner=='INTERNAL' else used_regular).add(int(identity.split('.')[0]))
                for name,value in zip(keys,values):
                    error=value-row[name];item[name]=error;maxima[name]=max(maxima[name],abs(error))
            records.append(item);usage[owner]+=1
    assert len(records)==len(reference)
    return records,{'classified_counts':dict(usage),'all_reference_rows':len(reference),'unambiguous_max_absolute':maxima,
        'scope':'Unambiguous owners only; ambiguous rows retained without inferred pairing. No acceptance claim.',
        'clock_policy':{'mapping':'nearest integer sample index; no trajectory shifts','interval_seconds':h,'maximum_clock_residual_seconds':clock_bound},
        'unpaired_actual_regular':[{'index':i,'row':r,'clock_residual':r[0]-tick(r[0])*h,'reason':'No unique reference owner/side'} for i,r in enumerate(metadata['regular_rows']) if i not in used_regular],
        'unpaired_actual_internal':[{'index':i,'event':e,'clock_residual':e['time']-tick(e['time'])*h,'reason':'No unique reference owner/side'} for i,e in enumerate(metadata['internal_transitions']) if i not in used_internal],
        'coverage':sum(r['owner']!='AMBIGUOUS' for r in records)/len(records),
        'channel_units':{k:('V' if k in ['STATE0','STATE2','VAR9','VAR10'] else 'A' if k=='STATE1' else 'degrees' if k in ['A7','A9','VAR12','VAR13'] else 'pu') for k in keys},
        'paired_actual_regular_rows':len(used_regular),'paired_actual_internal_transitions':len(used_internal),
        'unpaired_actual_regular_count':len(metadata['regular_rows'])-len(used_regular),
        'unpaired_actual_internal_count':len(metadata['internal_transitions'])-len(used_internal),
        'actual_regular_rows':len(metadata['regular_rows']),'actual_internal_transitions':len(metadata['internal_transitions'])}

def main():
    p=argparse.ArgumentParser();p.add_argument('--archive',type=Path,required=True);p.add_argument('--metadata',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    metadata=json.loads(a.metadata.read_text());first=metadata['first_terminal']
    with a.archive.open('rb') as f:assert hashlib.file_digest(f,'sha256').hexdigest()=='7edf2478ed4e97b61bbef6f157c5d82b11c90671abb5be2ecffb0636d293f8d2'
    with zipfile.ZipFile(a.archive) as z:
        raw=z.read(first+'/a/native.csv');assert raw==z.read(first+'/b/native.csv')
        reference=[{k:float(v) for k,v in r.items()} for r in csv.DictReader(io.StringIO(raw.decode()))]
    records,summary=compare(metadata,reference);a.output.mkdir(exist_ok=True,parents=True)
    with (a.output/'owner-errors.csv').open('w',newline='') as f:
        fields=list(dict.fromkeys(k for r in records for k in r));w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(records)
    (a.output/'summary.json').write_text(json.dumps(summary,indent=2));print(json.dumps(summary))
if __name__=='__main__':main()
