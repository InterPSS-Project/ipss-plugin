"""Audit documented salient-branch cold references and exact raw preservation."""
import base64,csv,gzip,hashlib,json,math,struct
from pathlib import Path
from genqec_salient_probe import PROFILES,configuration
from audit_genqec_schema import digest
from audit_genqec_powerworld import canonical,table


def audit(root):
    profiles=[];identity=None;resource=root/'ipss.test.plugin.core/src/test/resources/genqec-salient-native'
    for profile in PROFILES:
        dynamic,selector,constants=configuration(profile)
        for scale,step in [('coarse',.0005),('fine',.00025)]:
            hashes=[]
            for repeat in ('a','b'):
                run=root/'target'/f'genqec-schema-salient-{profile}-{scale}-phasor-{repeat}';m=json.loads((run/'manifest.json').read_text())
                if (m['status'],m['profile'],m['selector'],m['actual_selector'],m['step_s'],m['end_s'],m['branch'])!=('native-converged-profile',profile,selector,selector,step,.3,'Tqop=0,Xqp=Xq'):raise ValueError('Changed salient contract')
                if m['model']!=('GENQEJ' if profile.startswith('genqej') else 'GENQEC'):raise ValueError('Wrong generator identity')
                if set(m['files'])!={'network.raw','dynamic.dyr','probe.py','hplntd_fault_probe.py','native.log','native.out','native.csv','genqec_schema_probe.py'}:raise ValueError('Incomplete native manifest')
                for name,sha in m['files'].items():
                    if digest(run/name)!=sha:raise ValueError('Changed raw artifact: '+name)
                for archived,source in [('probe.py','genqec_salient_probe.py'),('genqec_schema_probe.py','genqec_schema_probe.py'),('hplntd_fault_probe.py','hplntd_fault_probe.py')]:
                    if digest(run/archived)!=digest(root/'tools'/source):raise ValueError('Changed generating source')
                if (run/'dynamic.dyr').read_text()!=dynamic or m['declared_constants']!=constants or m['actual_constants']!=[struct.unpack('f',struct.pack('f',v))[0] for v in constants] or constants[2]!=0 or constants[9]!=constants[7]:raise ValueError('Changed exact winding/parameter vector')
                current=tuple(m[k] for k in ['source_document_sha256','library_sha256','python_sha256','wrapper_sha256'])
                if current[:2]!=('40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d','c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6'):raise ValueError('Changed source/runtime')
                if identity is None:identity=current
                if identity!=current:raise ValueError('Mixed native runtime')
                if tuple(m['layout']['N'+k] for k in ['CON','ICON','STATE','VAR'])!=(16,1,6,1):raise ValueError('Changed native allocation')
                log=(run/'native.log').read_text(errors='replace')
                if 'Initial conditions check O.K.' not in log or 'Network not converged' in log:raise ValueError('Rejected native quality')
                with (run/'native.csv').open(newline='') as stream:
                    reader=csv.DictReader(stream);fields=reader.fieldnames;raw=[{k:float(v) for k,v in row.items()} for row in reader]
                if len(fields)!=19 or m['channel_count']!=18 or len(raw)!=m['sample_count'] or not all(math.isfinite(v) for row in raw for v in row.values()):raise ValueError('Incomplete phasor/state histories')
                if 'A_BUS1' not in fields or 'A_BUS2' not in fields or any(row['EP_D']!=0 for row in raw):raise ValueError('Missing phasor or nonzero removed state')
                times=[r['time_s'] for r in raw]
                if times[0]>=0 or abs(times[-1]-.3)>step or any(b<a for a,b in zip(times,times[1:])) or sum(a==b for a,b in zip(times,times[1:]))!=2:raise ValueError('History/event samples missing')
                if [(e['time_s'],e['action']) for e in m['events']]!=[(.05,'fault'),(.1,'clear')] or any(abs(e['time_s']-e['actual_time_s'])>step for e in m['events']):raise ValueError('Changed event timing')
                if m['fault']!={'bus':1,'impedance_ohm':[0,20],'impedance_system_pu':[0,.2]}:raise ValueError('Changed fault/base')
                for suffix,name,zipped in [('csv','native.csv',True),('dyr','dynamic.dyr',False)]:
                    body=base64.b64decode((resource/f'{profile}-{scale}.{suffix}.b64').read_bytes());body=gzip.decompress(body) if zipped else body
                    if body!=(run/name).read_bytes():raise ValueError('Changed embedded raw reference')
                if base64.b64decode((resource/'network.raw.b64').read_bytes())!=(run/'network.raw').read_bytes():raise ValueError('Changed embedded network')
                hashes.append(m['files']['native.csv'])
            if hashes[0]!=hashes[1]:raise ValueError('Cold salient outputs differ')
            profiles.append(dict(profile=profile,step_s=step,samples=len(raw),channel_count=18))
    return dict(status='sixteen-cold-salient-branch-references-audited',runs=16,profiles=profiles)


def audit_powerworld(root):
    result=[]
    for profile in ('genqec-0','genqec-1'):
        dynamic,_,_=configuration(profile)
        for scale,step in [('coarse',.0005),('fine',.00025)]:
            hashes=[]
            for repeat in ('a','b'):
                run=root/'target'/f'genqec-schema-powerworld-salient-{profile}-{scale}-{repeat}';m=json.loads((run/'manifest.json').read_text())
                if m['status']!='complete' or m['salient_profile']!=profile or m['branch']!='Tqop=0,Xqp=Xq' or m['auto_correct'] or m['powerworld_model_overrides'] or m['simulation']!={'start_s':0,'stop_s':.3,'step_s':step}:raise ValueError('Changed commercial diagnostic contract')
                if m['powerworld_executable']['sha256']!='06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c':raise ValueError('Changed commercial runtime')
                if (run/'source.dyr').read_text()!=dynamic:raise ValueError('Changed exact commercial input')
                for meta in m['inputs'].values():
                    if hashlib.sha256(canonical(root/meta['path'])).hexdigest()!=meta['sha256']:raise ValueError('Changed commercial input hash')
                for name,meta in m['artifacts'].items():
                    if hashlib.sha256(canonical(run/name)).hexdigest()!=meta['sha256'] or len(canonical(run/name))!=meta['bytes']:raise ValueError('Changed commercial output')
                if set(m['generating_files'])!={'probe.py','genqec_schema_powerworld_probe.py','genqec_salient_probe.py','genqec_schema_probe.py','hplntd_fault_probe.py','runner.py'}:raise ValueError('Incomplete generating source manifest')
                for name,sha in m['generating_files'].items():
                    original='ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py' if name=='runner.py' else 'tools/'+('genqec_salient_powerworld_probe.py' if name=='probe.py' else name)
                    if digest(run/name)!=sha or digest(root/original)!=sha:raise ValueError('Changed commercial generating source')
                raw=table(run/'powerworld.csv')
                if len(raw)!=round(.3/step)+3 or any(row[10]!=0 for row in raw):raise ValueError('Incomplete commercial output or active removed state')
                expected=[dict(time_s=.05,object='Bus 1',action='FAULT 3PB IMP 0 0.2'),dict(time_s=.1,object='Bus 1',action='CLEARFAULT')]
                if m['contingency']['events']!=expected:raise ValueError('Changed commercial fault')
                times=[row[0] for row in raw]
                if times[0]!=0 or times[-1]!=.3 or any(b<a for a,b in zip(times,times[1:])) or sum(a==b for a,b in zip(times,times[1:]))!=2:raise ValueError('Commercial event rows or endpoint missing')
                hashes.append(digest(run/'powerworld.csv'))
            if hashes[0]!=hashes[1]:raise ValueError('Commercial cold repeats differ')
            result.append(dict(profile=profile,step_s=step,samples=len(raw),classification='repeatable-diagnostic-not-coupled-acceptance'))
    return dict(status='eight-cold-salient-commercial-diagnostics-audited',runs=8,profiles=result)


if __name__=='__main__':print(json.dumps(dict(native=audit(Path.cwd()),powerworld=audit_powerworld(Path.cwd())),indent=2))
