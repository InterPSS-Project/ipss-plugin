"""Audit valid large-Kw references, product-limit activity and cold provenance."""
import base64,csv,gzip,hashlib,json,math,struct
from pathlib import Path
from genqec_kw_probe import VALUES,configuration
from audit_genqec_schema import digest
from audit_genqec_powerworld import canonical,table


def product(row,kw):
    angle=row['ANGLE']-math.radians(row['A_BUS1'])
    return kw*(row['P_PU']*math.sin(angle)+row['Q_PU']*math.cos(angle))/row['V_BUS1']


def audit(root):
    resource=root/'ipss.test.plugin.core/src/test/resources/genqec-kw-native';profiles=[];data={};identity=None
    for profile,kw in VALUES.items():
        dynamic,selector,constants=configuration(profile)
        for scale,step in [('coarse',.0005),('fine',.00025)]:
            hashes=[]
            for repeat in ('a','b'):
                run=root/'target'/f'genqec-schema-{profile}-{scale}-{repeat}';m=json.loads((run/'manifest.json').read_text())
                if (m['status'],m['profile'],m['model'],m['selector'],m['actual_selector'],m['step_s'],m['end_s'],m['branch'])!=('native-converged-profile',profile,'GENQEC',selector,selector,step,.3,'Kw>=0; clamp(Kw*Id,-0.4,0.4)'):raise ValueError('Changed field-compensation identity')
                if set(m['files'])!={'network.raw','dynamic.dyr','probe.py','hplntd_fault_probe.py','native.log','native.out','native.csv','genqec_schema_probe.py'}:raise ValueError('Incomplete native provenance')
                for name,sha in m['files'].items():
                    if digest(run/name)!=sha:raise ValueError('Changed native file')
                for archived,source in [('probe.py','genqec_kw_probe.py'),('genqec_schema_probe.py','genqec_schema_probe.py'),('hplntd_fault_probe.py','hplntd_fault_probe.py')]:
                    if digest(run/archived)!=digest(root/'tools'/source):raise ValueError('Changed native generator')
                if (run/'dynamic.dyr').read_text()!=dynamic or m['declared_constants']!=constants or constants[15]!=kw or m['actual_constants']!=[struct.unpack('f',struct.pack('f',v))[0] for v in constants]:raise ValueError('Changed exact entered/allocated Kw or other constant')
                if tuple(m['layout']['N'+k] for k in ['CON','ICON','STATE','VAR'])!=(16,1,6,1):raise ValueError('Changed native allocation')
                current=tuple(m[k] for k in ['source_document_sha256','library_sha256','python_sha256','wrapper_sha256'])
                if current[:2]!=('40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d','c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6'):raise ValueError('Changed source/runtime')
                if identity is None:identity=current
                if current!=identity:raise ValueError('Mixed native implementations')
                log=(run/'native.log').read_text(errors='replace')
                if 'Initial conditions check O.K.' not in log or 'Network not converged' in log:raise ValueError('Rejected native quality')
                with (run/'native.csv').open(newline='') as stream:reader=csv.DictReader(stream);fields=reader.fieldnames;raw=[{k:float(v) for k,v in row.items()} for row in reader]
                if len(fields)!=19 or m['channel_count']!=18 or len(raw)!=m['sample_count'] or not all(math.isfinite(v) for row in raw for v in row.values()):raise ValueError('Incomplete phasor/state history')
                times=[r['time_s'] for r in raw]
                if times[0]>=0 or abs(times[-1]-.3)>step or any(b<a for a,b in zip(times,times[1:])) or sum(a==b for a,b in zip(times,times[1:]))!=2:raise ValueError('Missing raw history/event rows')
                if m['fault']!={'bus':1,'impedance_ohm':[0,20],'impedance_system_pu':[0,.2]} or [(e['time_s'],e['action']) for e in m['events']]!=[(.05,'fault'),(.1,'clear')] or any(abs(e['time_s']-e['actual_time_s'])>step for e in m['events']):raise ValueError('Changed fault/base/timing')
                for suffix,name,zipped in [('csv','native.csv',True),('dyr','dynamic.dyr',False)]:
                    body=base64.b64decode((resource/f'{profile}-{scale}.{suffix}.b64').read_bytes());body=gzip.decompress(body) if zipped else body
                    if body!=(run/name).read_bytes():raise ValueError('Embedded bytes differ from immutable native output')
                if base64.b64decode((resource/'network.raw.b64').read_bytes())!=(run/'network.raw').read_bytes():raise ValueError('Changed embedded network')
                hashes.append(m['files']['native.csv'])
            if hashes[0]!=hashes[1]:raise ValueError('Native cold outputs differ')
            products=[product(row,kw) for row in raw];data[profile,scale]=raw
            if profile=='kw-one' and not(products[0]<.4<max(products) and min(products)<-.4 and products[-1]<.4):raise ValueError('Signed entry/release discriminator inactive')
            if profile=='kw-high' and not products[0]>.4:raise ValueError('Initial upper limit not activated')
            profiles.append(dict(profile=profile,step_s=step,samples=len(raw),Kw=kw,product_initial=products[0],product_min=min(products),product_max=max(products),product_final=products[-1]))
    for scale in ['coarse','fine']:
        baseline=data['kw-zero',scale][0]['EFD']
        for profile,kw in VALUES.items():
            row=data[profile,scale][0];numerator=row['EFD']*(1-max(-.4,min(.4,product(row,kw))))
            if abs(numerator-baseline)>1e-6:raise ValueError('Native initial field violates clipped-product denominator')
    return dict(status='twelve-cold-Kw-profiles-and-field-denominator-audited',runs=12,profiles=profiles)


def audit_powerworld(root):
    profiles=[]
    for profile in VALUES:
        dynamic,_,_=configuration(profile)
        for scale,step in [('coarse',.0005),('fine',.00025)]:
            hashes=[]
            for repeat in ['a','b']:
                run=root/'target'/f'genqec-schema-powerworld-{profile}-{scale}-{repeat}';m=json.loads((run/'manifest.json').read_text())
                if m['status']!='complete' or m['kw_profile']!=profile or m['branch']!='Kw>=0; clamp(Kw*Id,-0.4,0.4)' or m['auto_correct'] or m['powerworld_model_overrides'] or m['simulation']!={'start_s':0,'stop_s':.3,'step_s':step}:raise ValueError('Changed commercial simulation')
                if m['powerworld_executable']['sha256']!='06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c':raise ValueError('Changed commercial runtime')
                if (run/'source.dyr').read_text()!=dynamic:raise ValueError('Changed commercial input')
                for meta in m['inputs'].values():
                    if hashlib.sha256(canonical(root/meta['path'])).hexdigest()!=meta['sha256']:raise ValueError('Changed commercial input hash')
                for name,meta in m['artifacts'].items():
                    if hashlib.sha256(canonical(run/name)).hexdigest()!=meta['sha256'] or len(canonical(run/name))!=meta['bytes']:raise ValueError('Changed commercial artifact')
                if set(m['generating_files'])!={'probe.py','genqec_schema_powerworld_probe.py','genqec_kw_probe.py','genqec_schema_probe.py','hplntd_fault_probe.py','runner.py'}:raise ValueError('Incomplete generating source manifest')
                for name,sha in m['generating_files'].items():
                    original='ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py' if name=='runner.py' else 'tools/'+('genqec_kw_powerworld_probe.py' if name=='probe.py' else name)
                    if digest(run/name)!=sha or digest(root/original)!=sha:raise ValueError('Changed commercial source')
                expected=[dict(time_s=.05,object='Bus 1',action='FAULT 3PB IMP 0 0.2'),dict(time_s=.1,object='Bus 1',action='CLEARFAULT')]
                if m['contingency']['events']!=expected:raise ValueError('Changed commercial fault')
                raw=table(run/'powerworld.csv');times=[r[0] for r in raw]
                if len(raw)!=round(.3/step)+3 or times[0]!=0 or times[-1]!=.3 or any(b<a for a,b in zip(times,times[1:])) or sum(a==b for a,b in zip(times,times[1:]))!=2:raise ValueError('Missing commercial sample/event row')
                hashes.append(digest(run/'powerworld.csv'))
            if hashes[0]!=hashes[1]:raise ValueError('Commercial cold outputs differ')
            profiles.append(dict(profile=profile,step_s=step,samples=len(raw),classification='repeatable-diagnostic-not-coupled-acceptance'))
    return dict(status='twelve-cold-commercial-Kw-diagnostics-audited',runs=12,profiles=profiles)


if __name__=='__main__':print(json.dumps(dict(native=audit(Path.cwd()),powerworld=audit_powerworld(Path.cwd())),indent=2))
