"""Audit whole raw routing histories and exact declared/read-back controller records."""
import csv,json,math
from pathlib import Path
from check_pss5c_pole_artifacts import checked_files
from genrou_native_probe import f32
from hplntd_fault_probe import sha
from oel3c_routing_probe import configuration

def check(root):
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    results=[];rejections=[]
    for profile in ('efd-flat','ifd-flat','vfe-flat','fallback-flat','efd-fault','ifd-fault','vfe-fault','takeover-flat'):
        cfg=configuration(profile,raw)
        for scale,step in (('coarse',.0005),('fine',.00025)):
            folders=[root/'target'/('genrou-native-oel3c-routing-final-v1-'+profile+'-'+scale+'-'+repeat) for repeat in ('a','b')]
            for folder in folders:
                manifest=json.loads((folder/'manifest.json').read_text());checked_files(folder,manifest['files'])
                assert (folder/'dynamic.dyr').read_text()==cfg['dynamic']
                assert (folder/'network.raw').read_text()==cfg['raw']
                assert sha(folder/'probe.py')==sha(root/'tools/oel3c_routing_probe.py')
                assert manifest['step_s']==step
                if profile=='takeover-flat':
                    rejected=json.loads((folder/'rejected-initialization-readback.json').read_text())
                    assert rejected['status']=='rejected-routing-probe' and not rejected['trajectory_acceptance']
                    assert 'Initial conditions suspect' in (folder/'native.log').read_text()
                    rejections.append(folder.name);continue
                assert manifest['status']=='native-converged-profile'
                log=(folder/'native.log').read_text();assert 'Initial conditions check O.K.' in log
                for prohibited in ('Initial conditions suspect','Network not converged','Undefined Model','RECORD IGNORED'):
                    assert prohibited not in log
                for spec in cfg['components']:
                    c=manifest['components'][spec['kind']]
                    assert c['actual_icons']==list(spec['icons'])
                    assert c['actual_constants']==list(map(f32,spec['constants']))
                    assert [c['layout']['N'+k] for k in ('ICON','CON','STATE','VAR')]==[
                        len(spec['icons']),len(spec['constants']),len(spec['states']),len(spec['variables'])]
                with (folder/'native.csv').open(newline='') as stream:
                    reader=csv.DictReader(stream);rows=[{k:float(v) for k,v in r.items()} for r in reader]
                assert len(reader.fieldnames)==31 and len(rows)==manifest['sample_count']==round(.5/step)+5
                assert all(math.isfinite(v) for r in rows for v in r.values())
                times=[r['time_s'] for r in rows];assert times==sorted(times)
                assert sum(t<0 for t in times)==2 and sum(a==b for a,b in zip(times,times[1:]))==2
                assert all(-1<=r['OEL_INTEGRAL']<=0 for r in rows)
                results.append(dict(run=folder.name,rows=len(rows),csv_sha256=sha(folder/'native.csv')))
            if profile!='takeover-flat':
                for name in ('native.csv','dynamic.dyr','network.raw','probe.py','live-states.json'):
                    assert (folders[0]/name).read_bytes()==(folders[1]/name).read_bytes(),(profile,name)
    return dict(runs=len(results),cold_pairs=len(results)//2,raw_rows=sum(r['rows'] for r in results),
        rejected_runs=rejections,results=results,trajectory_acceptance=False,parameters_fitted=False)

if __name__=='__main__':print(json.dumps(check(Path.cwd()),indent=2))
