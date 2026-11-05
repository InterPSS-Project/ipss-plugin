"""Audit complete paired converter artifacts; optionally stage lossless test fixtures."""
import argparse,base64,collections,csv,gzip,hashlib,json,math
from pathlib import Path

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,required=True);parser.add_argument('--stage',action='store_true');args=parser.parse_args()
    groups=collections.defaultdict(list);probes=set();libraries=set();documents=set()
    for file in sorted(args.root.glob('regcc-*/manifest.json')):
        m=json.loads(file.read_text());assert m['status']=='complete',file
        assert m['layout']==dict(ICON=2,CON=13,STATE=9,VAR=10)
        assert m['entered_constants']==m['initialized_constants']
        for name,sha in m['files'].items():assert digest(file.parent/name)==sha,(file,name)
        probes.add(m['files']['regcc_reference_probe.py']);libraries.add(m['library_sha256']);documents.add(m['source_document_sha256'])
        rows=[{k:float(v) for k,v in r.items()} for r in csv.DictReader((file.parent/'native.csv').open())]
        assert len(rows)==m['samples']==round(m['end_s']/m['step_s'])+5
        assert all(len(r)==m['channels']+1 and all(math.isfinite(v) for v in r.values()) for r in rows)
        duplicate=collections.Counter(round(r['time_s']/m['step_s']) for r in rows)
        assert {k:v for k,v in duplicate.items() if v>1}=={round(.05/m['step_s']):2,round(.1/m['step_s']):2}
        assert sum(r['time_s']<0 for r in rows)==2
        assert m['channels']==(42 if m['profile']=='electrical' else 27)
        key=(m['profile'],m['step_s']);groups[key].append((file,m,rows))
    assert len(groups)==15 and all(len(pair)==2 for pair in groups.values())
    assert len(probes)==len(libraries)==len(documents)==1
    profiles=[];diagnostics=[]
    destination=Path('ipss.test.plugin.core/src/test/resources/regcc')
    if args.stage:destination.mkdir(exist_ok=True)
    for (profile,step),pair in sorted(groups.items()):
        pair.sort(key=lambda item:item[1]['repeat']);(file,m,rows),(other,n,_)=pair
        assert [m['repeat'],n['repeat']]==['a','b']
        for name in ['network.raw','dynamic.dyr','native.csv']:assert m['files'][name]==n['files'][name],(profile,name)
        name=file.parent.name[6:-2]
        acceptance='diagnostic' if profile in ['limit-q','minimum','electrical'] else 'bounded-trajectory'
        info=dict(name=name,profile=profile,step_s=step,samples=m['samples'],fault=m['fault'],acceptance=acceptance,sha256=m['files']['native.csv'])
        profiles.append(info)
        if profile=='limit-q':
            jumps=[dict(time_s=b['time_s'],previous_active=a['STATE1'],previous_reactive=a['STATE0'],active=b['STATE1'])
                   for a,b in zip(rows,rows[1:]) if abs(b['STATE1']-a['STATE1'])>.1]
            assert jumps,'Retain the unresolved active-boundary state discontinuity'
            diagnostics.append(dict(profile=profile,native_active_integral_discontinuities=jumps))
        if args.stage:
            for source,suffix in [('network.raw','.raw.b64'),('dynamic.dyr','.dyr.b64'),('native.csv','.csv.gz.b64')]:
                body=(file.parent/source).read_bytes()
                if source.endswith('.csv'):body=gzip.compress(body,mtime=0)
                (destination/(name+suffix)).write_bytes(base64.encodebytes(body))
    summary=dict(status='artifact-integrity-passed',native_runs=30,profiles=profiles,diagnostics=diagnostics,
        bounded_trajectory_profiles=sum(p['acceptance']=='bounded-trajectory' for p in profiles),
        unqualified_profiles=sum(p['acceptance']=='diagnostic' for p in profiles),probe_sha256=next(iter(probes)))
    (args.root/'verified-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    if args.stage:(destination/'profiles.json').write_text(json.dumps(profiles,indent=2)+'\n')
    print(json.dumps(summary,indent=2))

if __name__=='__main__':main()
