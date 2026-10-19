"""Audit native derivative memory, event rebasing, recurrence and cold repeats."""
import csv, hashlib, json, math, struct
from pathlib import Path


def check(root):
    profiles=[]
    for location in ('local','remote'):
        for event in ('flat','fault','reference'):
            for scale in ('coarse','fine'):
                name='-'.join((location,event,scale))
                folders=[root/'target'/('ieeest-input-derivative-matrix1-'+name+'-'+repeat) for repeat in ('a','b')]
                manifests=[json.loads((folder/'manifest.json').read_text()) for folder in folders]
                for folder,m in zip(folders,manifests):
                    assert m['status']=='native-converged-profile',name
                    assert m['actual_constants']==m['final_constants'],name
                    for filename,digest in m['files'].items():assert hashlib.sha256((folder/filename).read_bytes()).hexdigest()==digest,(name,filename)
                    c=m['components']['STAB'];assert c['actual_icons']==[6,0 if location=='local' else 2]
                    assert [c['layout'][k] for k in ('NCON','NICON','NSTATE','NVAR')]==[17,2,7,2]
                    live=json.loads((folder/'live-states.json').read_text())
                    for index,entry in enumerate(live):
                        assert len(entry['states'])==18 and len(entry['variables'])==2,name
                        if entry['stage'].startswith('post-'):
                            assert entry['states']==live[index-1]['states'],(name,entry['stage'])
                            assert entry['variables']==live[index-1]['variables'],(name,entry['stage'])
                for filename in ('native.csv','network.raw','dynamic.dyr'):
                    assert (folders[0]/filename).read_bytes()==(folders[1]/filename).read_bytes(),(name,filename)
                rows=list(csv.DictReader((folders[0]/'native.csv').open()))
                assert len(rows)==manifests[0]['sample_count']==(1005 if scale=='coarse' else 2005),name
                assert len(rows[0])==39 and float(rows[0]['time_s'])<0,name
                assert all(math.isfinite(float(value)) for row in rows for value in row.values()),name
                step=struct.unpack('<f',struct.pack('<f',.0005 if scale=='coarse' else .00025))[0]
                bus='V_BUS1' if location=='local' else 'V_BUS2'
                maximum=0;duplicates=0;previous=rows[0]
                constants=manifests[0]['components']['STAB']['actual_constants']
                a1,a2,a3,a4,a5,a6,t1,t2,t3,t4,t5,t6,ks,upper,lower,_,_=constants
                assert a2>0 and a4==a5==a6==0 and t5==t6
                recurrence=[0.]*6;old_slopes=[0.]*6;output_identity=0
                dynamic_keys=('PSS_FILTER1','PSS_FILTER2','PSS_FILTER4','PSS_LL1','PSS_LL2','PSS_LAST')
                def equations(row):
                    x={k:float(v) for k,v in row.items()}
                    y1=(t1*x['PSS_FILTER4']+x['PSS_LL1'])/t2
                    y2=(t3*y1+x['PSS_LL2'])/t4
                    signal=(ks*t5*y2-x['PSS_LAST'])/t6
                    return [x['PSS_VDOT']-x['PSS_FILTER3'],x['PSS_FILTER1']-a1*x['PSS_FILTER3'],
                            (x['PSS_FILTER3']-x['PSS_FILTER4'])/a3,x['PSS_FILTER4']-y1,y1-y2,signal],signal
                for row in rows:
                    assert row['PSS_MEMORY']==row[bus],(name,row['time_s'])
                    time,oldtime=float(row['time_s']),float(previous['time_s'])
                    if time>oldtime:
                        predicted=.8*float(previous['PSS_VDOT'])+.2*(float(row[bus])-float(previous[bus]))/step
                        maximum=max(maximum,abs(predicted-float(row['PSS_VDOT'])))
                        slopes,_=equations(previous)
                        for index,key in enumerate(dynamic_keys):
                            predicted_state=float(previous[key])+step*(1.5*slopes[index]-.5*old_slopes[index])
                            recurrence[index]=max(recurrence[index],abs(float(row[key])-predicted_state))
                        old_slopes=slopes
                    elif row is not rows[0]:
                        duplicates+=1
                        assert row['PSS_VDOT']==previous['PSS_VDOT'],(name,time)
                        for key in ('PSS_FILTER1','PSS_FILTER2','PSS_FILTER3','PSS_FILTER4','PSS_LL1','PSS_LL2','PSS_LAST'):
                            assert row[key]==previous[key],(name,key,time)
                    _,signal=equations(row)
                    output_identity=max(output_identity,abs(max(lower,min(upper,signal))-float(row['VOTHSG'])))
                    previous=row
                assert duplicates==2,name
                assert maximum<1.2e-7,(name,maximum)
                for residual,bound in zip(recurrence,(2.5e-9,7.4e-11,3.7e-8,5e-9,4e-8,1.65e-7)):
                    assert residual<bound,(name,recurrence)
                assert output_identity<1.8e-6,(name,output_identity)
                profiles.append(dict(profile=name,samples=len(rows),recurrence_residual=maximum,
                                     observed_integrator_recurrence_residuals=dict(zip(dynamic_keys,recurrence)),
                                     native_output_coordinate_identity_residual=output_identity,
                                     csv_sha256=hashlib.sha256((folders[0]/'native.csv').read_bytes()).hexdigest()))
    return profiles


if __name__=='__main__':print(json.dumps(dict(status='pass',profiles=check(Path(__file__).resolve().parents[1])),indent=2))
