"""Classify overlap observations by independently known event owners, preserving ambiguity."""
import argparse,csv,hashlib,io,json,math,zipfile
from pathlib import Path
from collections import defaultdict,Counter

def compare(metadata,reference):
    h=.0005
    def tick(t):
        k=round(t/h)
        if abs(t-k*h)>=h/2:raise ValueError("Clock does not identify a unique interval")
        return k
    rg=defaultdict(list);ag=defaultdict(list);events=defaultdict(list)
    for i,r in enumerate(reference):rg[tick(r['time_s'])].append((i,r))
    for i,r in enumerate(metadata['regular_rows']):ag[tick(r[0])].append((i,r))
    for i,e in enumerate(metadata['internal_transitions']):events[tick(e['time'])].append((i,e))
    external={tick(t) for t in metadata['external_schedule']}
    keys=metadata['columns']; records=[];maxima={k:0. for k in keys};usage=Counter()
    def boundary(b):
        return [math.hypot(b['rectifierReal'],b['rectifierImaginary']),math.degrees(math.atan2(b['rectifierImaginary'],b['rectifierReal'])),
            math.hypot(b['inverterReal'],b['inverterImaginary']),math.degrees(math.atan2(b['inverterImaginary'],b['inverterReal'])),
            b['measuredInverterVolts'],b['measuredAmperes'],b['measuredRectifierVolts'],b['rectifierP'],b['rectifierQ'],math.degrees(b['alphaRadians']),
            b['inverterP'],b['inverterQ'],math.degrees(b['gammaRadians']),b['rectifierVolts'],b['inverterVolts']]
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
            item={'reference_row':index,'reference_time':row['time_s'],'tick':k,'owner':owner,
                'side':('PRE' if position==0 else 'POST') if len(group)==2 else 'SINGLE',
                'reference_group_size':len(group),'actual_regular_group_size':len(actual),'actual_internal_events':len(internal),'owner_candidates':('EXTERNAL+INTERNAL' if k in external and internal else 'EXTERNAL' if k in external else 'INTERNAL' if internal or len(group)>1 else 'ENDPOINT'),
                'reason':reason if owner=='AMBIGUOUS' else 'unique-owner-and-side-count'}
            if candidates:
                identity,t,values=candidates[position];item.update(actual_id=identity,actual_time=t)
                for name,value in zip(keys,values):
                    error=value-row[name];item[name]=error;maxima[name]=max(maxima[name],abs(error))
            records.append(item);usage[owner]+=1
    assert len(records)==len(reference)
    return records,{'classified_counts':dict(usage),'all_reference_rows':len(reference),'unambiguous_max_absolute':maxima,
        'scope':'Unambiguous owners only; ambiguous rows retained without inferred pairing. No acceptance claim.',
        'coverage':sum(r['owner']!='AMBIGUOUS' for r in records)/len(records),
        'channel_units':{k:('V' if k in ['STATE0','STATE2','VAR9','VAR10'] else 'A' if k=='STATE1' else 'degrees' if k in ['A7','A9','VAR12','VAR13'] else 'pu') for k in keys},
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
