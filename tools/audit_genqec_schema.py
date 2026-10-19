"""Audit exact standard/wrapped inputs and independent selector equivalence."""
import csv
import base64
import gzip
import hashlib
import json
import math
from pathlib import Path
import struct
from genqec_schema_probe import PROFILES, configuration


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def rows(path):
    with path.open(newline='') as stream:
        reader=csv.DictReader(stream);fields=reader.fieldnames
        result=[{k:float(v) for k,v in row.items()} for row in reader]
    if not result or len(fields)!=17 or not all(math.isfinite(v) for row in result for v in row.values()):raise ValueError('Incomplete/nonfinite generator trajectory')
    return result


def audit(root):
    profiles=[];data={};identity=None
    for profile in PROFILES:
        dynamic,selector,constants=configuration(profile)
        for label,step in [('coarse',.0005),('fine',.00025)]:
            cold=[]
            for repeat in ('a','b'):
                run=root/'target'/f'genqec-schema-{profile}-{label}-{repeat}'
                m=json.loads((run/'manifest.json').read_text())
                if (m['status'],m['model'],m['profile'],m['selector'],m['actual_selector'],m['step_s'],m['end_s'])!=('native-converged-profile','GENQEC',profile,selector,selector,step,.3):raise ValueError('Changed profile identity')
                expected={'network.raw','dynamic.dyr','probe.py','hplntd_fault_probe.py','native.log','native.out','native.csv'}
                if set(m['files'])!=expected:raise ValueError('Incomplete raw manifest')
                for name,sha in m['files'].items():
                    if digest(run/name)!=sha:raise ValueError('Changed raw file: '+name)
                for archived,original in [('probe.py','genqec_schema_probe.py'),('hplntd_fault_probe.py','hplntd_fault_probe.py')]:
                    if digest(run/archived)!=digest(root/'tools'/original):raise ValueError('Changed generating source')
                if (run/'dynamic.dyr').read_text()!=dynamic or m['declared_constants']!=constants or m['actual_constants']!=[struct.unpack('f',struct.pack('f',v))[0] for v in constants]:raise ValueError('Changed entered/allocated constant vector')
                if digest(run/'network.raw')!=digest(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'):raise ValueError('Changed public testbed')
                if tuple(m['layout']['N'+k] for k in ('CON','ICON','STATE','VAR'))!=(16,1,6,1):raise ValueError('Changed exact native allocation')
                current=tuple(m[k] for k in ('source_document_sha256','library_sha256','python_sha256','wrapper_sha256'))
                if current[:2]!=('40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d','c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6'):raise ValueError('Changed source/runtime implementation')
                if identity is None:identity=current
                if current!=identity:raise ValueError('Mixed native implementations')
                if m['fault']!={'bus':1,'impedance_ohm':[0,20],'impedance_system_pu':[0,.2]} or [(v['time_s'],v['action']) for v in m['events']]!=[(.05,'fault'),(.1,'clear')]:raise ValueError('Changed fault/base contract')
                if any(abs(v['actual_time_s']-v['time_s'])>step for v in m['events']):raise ValueError('Excessive clock drift')
                log=(run/'native.log').read_text(errors='replace')
                if 'Initial conditions check O.K.' not in log or 'Network not converged' in log:raise ValueError('Rejected native initialization/solution')
                raw=rows(run/'native.csv');times=[row['time_s'] for row in raw]
                fields=['time_s','EP_Q','EP_D','PSIP_D','PSIP_Q','SPEED_DEV','ANGLE','INTERNAL_VAR','V_BUS1','V_BUS2','MACH_ANGLE','P_PU','Q_PU','EFD','MACH_SPEED','REF_ANGLE','REF_SPEED']
                if list(raw[0])!=fields or len(raw)!=m['sample_count'] or m['channel_count']!=16:raise ValueError('Changed semantic channel/row contract')
                if any(b<a for a,b in zip(times,times[1:])) or times[0]>=0 or not any(a==b for a,b in zip(times,times[1:])) or abs(times[-1]-.3)>step:raise ValueError('History/event rows or endpoint missing')
                resource=root/'ipss.test.plugin.core/src/test/resources/genqec-schema-native'
                for suffix,name,zipped in [('csv','native.csv',True),('dyr','dynamic.dyr',False)]:
                    body=base64.b64decode((resource/f'{profile}-{label}.{suffix}.b64').read_bytes())
                    if zipped:body=gzip.decompress(body)
                    if body!=(run/name).read_bytes():raise ValueError('Embedded reference does not preserve exact raw bytes')
                if base64.b64decode((resource/'network.raw.b64').read_bytes())!=(run/'network.raw').read_bytes():raise ValueError('Changed embedded network')
                cold.append(m['files']['native.csv'])
            if cold[0]!=cold[1]:raise ValueError('Cold generator trace differs')
            data[profile,label]=raw
            profiles.append(dict(profile=profile,step_s=step,samples=len(raw),csv_sha256=cold[0]))
    wrapper_diagnostics={}
    for label in ('coarse','fine'):
        original=data['standard-1',label]
        for profile in ('standard-2','standard-negative'):
            if data[profile,label]!=original:raise ValueError('Nonzero selector does not match native scaled-quadratic behavior')
        exponential=data['standard-0',label]
        if max(abs(a['INTERNAL_VAR']-b['INTERNAL_VAR']) for a,b in zip(original,exponential))<1e-4:raise ValueError('Inactive saturation discriminator')
        wrapped=data['wrapper-1',label]
        wrapper_diagnostics[label]={key:max(abs(a[key]-b[key]) for a,b in zip(original,wrapped)) for key in original[0]}
    return dict(status='exact-schema-and-nonzero-selector-equivalence-audited',cold_runs=20,profiles=profiles,wrapper_versus_standard_residual_diagnostics=wrapper_diagnostics)


if __name__=='__main__':print(json.dumps(audit(Path.cwd()),indent=2))
