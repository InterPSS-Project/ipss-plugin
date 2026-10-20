"""Retain complete controlled timer evidence, including counterexamples to expiry hypotheses."""
import json
from pathlib import Path
from audit_basoel2_timer_semantics import audit as phase_audit
from basoel2_timer_probe import SCHEDULES
from check_basoel2_artifacts import compare,digest,read


def audit(root):
    root=Path(root);folders=sorted(root.glob('genrou-native-basoel2-timer-pilot-v[1234]-*-a'))
    if len(folders)!=11:raise ValueError('Expected eleven retained controlled cold pairs')
    pairs=[];total=0
    for a in folders:
        b=a.with_name(a.name[:-1]+'b')
        metadata=[]
        for folder in (a,b):
            m=json.loads((folder/'manifest.json').read_text())
            if m['status']!='native-converged-profile':raise ValueError('Reference did not converge')
            for name,expected in m['files'].items():
                if Path(name).name!=name or digest(folder/name)!=expected:raise ValueError('Reference hash changed')
            header,rows=read(folder/'native.csv')
            if len(rows)!=m['sample_count'] or len(header)-1!=m['channel_count']:raise ValueError('Reference dimensions changed')
            profile=m['profile'];base=profile.removesuffix('-active')
            expected=[dict(time_s=t,value=value) for t,value in SCHEDULES[base]]
            events=m['events']
            if [dict(time_s=e['time_s'],value=e['value']) for e in events]!=expected:
                raise ValueError('Undeclared state intervention')
            if any(e['action']!='timer-diagnostic-state-step' for e in events):raise ValueError('Unexpected physical event')
            for name in ('BAS_OEL_OUTPUT','BAS_VHZ_OUTPUT','VOEL_MACHINE'):
                if any(row[header.index(name)]!=0 for row in rows):raise ValueError('Controlled diagnostic changed a limiter output')
            metadata.append(m)
        pair=compare(a/'native.csv',b/'native.csv');total+=pair['rows']*2
        if not set(pair['numerical_differences'])<=set(('BAS_T1_B','BAS_T2_B','BAS_T3_B')):
            raise ValueError('Cold physical-state or timer-deadline trajectory changed')
        pair.update(runs=[a.name,b.name],profile=metadata[0]['profile'],
                    files=[dict(csv_sha256=digest(folder/'native.csv'),manifest_sha256=digest(folder/'manifest.json')) for folder in (a,b)])
        if metadata[0]['components']['MAXXL']['actual_icons'][0]!=0:
            pair['candidate_phase_audits']=[phase_audit(a,nearest) for nearest in (False,True)]
        pairs.append(pair)
    return dict(status='controlled-diagnostic-not-native-runtime-acceptance',raw_rows=total,pairs=pairs,
                automatic_native_import_enabled=False,clock_fitting_performed=False)


if __name__=='__main__':
    import sys
    print(json.dumps(audit(sys.argv[1] if len(sys.argv)>1 else 'target'),indent=2))
