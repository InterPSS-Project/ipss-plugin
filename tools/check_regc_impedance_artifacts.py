"""Check complete paired converter references and their source-reactance discriminator."""
import csv
import hashlib
import json
import math
from pathlib import Path


def verify(folder):
    manifest=json.loads((folder/'manifest.json').read_text())
    assert manifest['status']=='complete'
    for name,digest in manifest['files'].items():
        assert Path(name).name==name
        assert hashlib.sha256((folder/name).read_bytes()).hexdigest()==digest,(folder.name,name)
    assert manifest['entered_constants']==manifest['initialized_constants']
    rows=list(csv.DictReader((folder/'native.csv').open()))
    assert len(rows)==manifest['samples']==round(.2/manifest['step_s'])+5
    times=[float(row['time_s']) for row in rows]
    assert all(math.isfinite(float(v)) for row in rows for v in row.values())
    assert times==sorted(times) and sum(a==b for a,b in zip(times,times[1:]))==2
    log=(folder/'native.log').read_text(errors='replace')
    assert 'Initial conditions check O.K.' in log
    assert 'Network not converged' not in log and 'RECORD IGNORED' not in log and 'Undefined Model' not in log
    if manifest['model']=='REGCAU2':
        assert manifest['layout']['CON']==15 and manifest['layout']['STATE']==3
        assert abs(manifest['entered_constants'][-1]-manifest['xe'])<1e-6
    return manifest,rows


def main():
    folders=sorted(Path('target').glob('regc-impedance-*/manifest.json'))
    results=[]
    for path in folders:
        m,rows=verify(path.parent)
        if m['repeat']=='a':
            other=path.parent.with_name(path.parent.name[:-1]+'b')
            other_m,other_rows=verify(other)
            assert (path.parent/'native.csv').read_bytes()==(other/'native.csv').read_bytes()
            result={k:m.get(k,False) for k in ['model','source_x','xe','step_s','fixed_commands','samples','channels']}
            result['folder']=path.parent.name
            result['paired_csv_sha256']=m['files']['native.csv']
            result['maximum_current_pu']=max(math.hypot(float(r['P_PU']),float(r['Q_PU']))/float(r['V1']) for r in rows)
            if m['model']=='REGCAU2' and m['xe']>0:
                result['maximum_reactive_interface_residual_pu']=max(abs(
                    (float(r['STATE1'])-float(r['V1']))/m['xe']-float(r['Q_PU'])/float(r['V1'])) for r in rows)
                assert result['maximum_reactive_interface_residual_pu']<1e-5
            results.append(result)
    assert len(results)>=12,'Eight controlled-source and four fixed-command paired profiles required'
    first=Path('target/regc-impedance-regca1-z1-xe0-h0.0005-a/native.csv').read_bytes()
    second=Path('target/regc-impedance-regcau2-z1-xe0-h0.0005-a/native.csv').read_bytes()
    assert first==second,'Zero effective reactance must retain the current-source reference'
    output=dict(status='paired-references-and-interface-discriminator-checked',native_runs=2*len(results),profiles=results,
        zero_xe_current_source_recording_identical=True)
    Path('target/regc-impedance-verified-summary.json').write_text(json.dumps(output,indent=2)+'\n')
    print(json.dumps(output,indent=2))


if __name__=='__main__':main()
