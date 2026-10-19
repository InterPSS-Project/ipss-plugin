"""Audit exact thirty-constant PSS5C inputs and full, cold-repeated raw references."""
import csv
import hashlib
import json
import math
from pathlib import Path
import struct
from pss5c_native_probe import configuration


def verify(run):
    m=json.loads((run/'manifest.json').read_text())
    assert m['status']=='native-converged-profile'
    for name,digest in m['files'].items():
        assert Path(name).name==name
        assert hashlib.sha256((run/name).read_bytes()).hexdigest()==digest,(run.name,name)
    log=(run/'native.log').read_text(errors='replace')
    assert 'Initial conditions check O.K.' in log and 'Network not converged' not in log
    assert 'RECORD IGNORED' not in log and 'Undefined Model' not in log
    allocation=m['components']['STAB'];layout=allocation['layout']
    assert [layout['N'+key] for key in ('ICON','CON','STATE','VAR')]==[1,30,13,6]
    assert allocation['model']=='PSS5C' and allocation['actual_icons']==[0]
    lines=(run/'dynamic.dyr').read_text().splitlines()
    records=[line.split() for line in lines if "'PSS5C'" in line]
    assert len(records)==1 and records[0][-1]=='/' and len(records[0][3:-1])==30
    entered=[float(value) for value in records[0][3:-1]]
    f32=lambda value:struct.unpack('<f',struct.pack('<f',value))[0]
    assert allocation['actual_constants']==list(map(f32,entered))
    assert m['actual_constants']==m['final_constants']
    rows=list(csv.DictReader((run/'native.csv').read_text().splitlines()))
    assert len(rows)==m['sample_count']==(1005 if m['step_s']==.0005 else 2005)
    assert len(rows[0])==44 and float(rows[0]['time_s'])<0
    assert sum(a['time_s']==b['time_s'] for a,b in zip(rows,rows[1:]))==2
    assert all(math.isfinite(float(value)) for row in rows for value in row.values())
    assert all('PSS_STATE_'+str(i) in rows[0] for i in range(1,14))
    # Raw VAR observation semantics are not yet certified. Preserve and report
    # this counterexample rather than widening a tolerance or calling it an
    # accepted equation identity. The single-band discriminator disproves the
    # proposed running-sum interpretation as well.
    lower,upper=sorted(allocation['actual_constants'][28:30])
    maximum=0
    for row in rows:
        bands=[float(row[key]) for key in ('PSS_VVL','PSS_VL','PSS_VI','PSS_VH')]
        expected=max(lower,min(upper,sum(bands)));actual=float(row['VOTHSG'])
        error=abs(expected-actual);maximum=max(maximum,error)
    return m,maximum


def check(root):
    results=[]
    profiles=[('flat','coarse','flat-coarse')]+[(mode,scale,mode+'-'+scale) for mode in ('fault','transducer-fault','gain-fault') for scale in ('coarse','fine')]
    profiles += [('very-low-fault','coarse','very-low-coarse'),
                 ('unit-frequency-fault','coarse','unit-frequency-fault-coarse')]
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    for mode,scale,name in profiles:
        runs=[root/'target'/('pss5c-native-'+name+'-'+repeat) for repeat in ('a','b')]
        entries=[verify(run) for run in runs]
        a,b=(entry[0] for entry in entries)
        assert a['profile']==b['profile']==mode
        expected=configuration(mode,raw)['components'][1]['constants']
        f32=lambda value:struct.unpack('<f',struct.pack('<f',value))[0]
        assert a['components']['STAB']['actual_constants']==list(map(f32,expected)),name
        for key in ('components','actual_constants','final_constants','layout','step_s','sample_count','channel_count','events',
                    'library_sha256','source_document_sha256','python_sha256','wrapper_sha256'):
            assert a[key]==b[key],(name,key)
        for file in ('network.raw','dynamic.dyr','native.csv','live-states.json'):
            assert (runs[0]/file).read_bytes()==(runs[1]/file).read_bytes(),(name,file)
        results.append(dict(profile=name,samples=a['sample_count'],channels=a['channel_count'],
                            raw_csv_sha256=a['files']['native.csv'],
                            raw_variable_sum_diagnostic=max(v[1] for v in entries),trajectory_acceptance=False))
    return results


if __name__=='__main__':print(json.dumps(check(Path.cwd()),indent=2))
