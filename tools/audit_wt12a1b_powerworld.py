"""Audit repeatable published-model diagnostics without claiming trajectory parity."""
import csv
import hashlib
import json
import math
from pathlib import Path
import shlex
from wt12a1b_native_probe import configuration


def canonical(path):
    return path.read_bytes().replace(b'\r\n', b'\n').replace(b'\r', b'\n')


def digest(path):
    return hashlib.sha256(canonical(path)).hexdigest()


def table(path):
    lines=path.read_text().splitlines()
    start=next(i for i,line in enumerate(lines) if line.startswith('Results,'))+1
    rows=[list(map(float,row)) for row in csv.reader(lines[start:]) if row and row[0] != 'END']
    if any(len(row)!=9 or not all(math.isfinite(v) for v in row) for row in rows):
        raise ValueError('Invalid eight-channel diagnostic')
    return rows


def audit(root):
    matrices={}; profiles=[]
    for mode in ('fault','direct','sustained'):
        dynamic,constants,events=configuration('fault' if mode=='sustained' else mode)
        if mode=='sustained': events=[(.05,'fault'),(.4,'clear')]
        source=next(line for line in dynamic.splitlines() if "'WT12A1B'" in line)
        wrapper="1 'USRMDL' '1' 'WT12A1U_B' 105 0 1 14 3 2 0 "+' '.join(map(str,constants))+' /'
        for label,step in [('coarse',.0005),('fine',.00025)]:
            hashes=[]
            for repeat in ('a','b'):
                run=root/'target'/f'wt12a1b-powerworld-{mode}-{label}-verified-{repeat}'
                m=json.loads((run/'manifest.json').read_text())
                if m['status']!='complete' or m['auto_correct'] or m['powerworld_model_overrides'] or m['simulation']!={'start_s':0,'stop_s':1,'step_s':step}:
                    raise ValueError('Changed correction/simulation contract')
                if m['powerworld_executable']['sha256']!='06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c':
                    raise ValueError('Changed simulator version')
                expected=[dict(time_s=t,object='Bus 1',action='FAULT 3PB IMP 0 0.1' if action=='fault' else 'CLEARFAULT') for t,action in events]
                if m['contingency']['events']!=expected:
                    raise ValueError('Changed fault duration/impedance units')
                for name,meta in m['artifacts'].items():
                    if digest(run/name)!=meta['sha256'] or len(canonical(run/name))!=meta['bytes']:
                        raise ValueError('Changed generated artifact')
                for meta in m['inputs'].values():
                    if digest(root/meta['path'])!=meta['sha256']:
                        raise ValueError('Changed benchmark input')
                if (run/'source.dyr').read_text()!=dynamic.replace(source,wrapper):
                    raise ValueError('Changed exact explicit-ID wrapper')
                for archived,original in [('probe.py','tools/wt12a1b_powerworld_probe.py'),('wt12a1b_native_probe.py','tools/wt12a1b_native_probe.py'),('hplntd_fault_probe.py','tools/hplntd_fault_probe.py'),('runner.py','ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py')]:
                    if canonical(run/archived)!=canonical(root/original):
                        raise ValueError('Changed generating source')
                before=run/'power_flow_solved-Stabilizer_WT1P_B.aux'; after=run/'initialized-Stabilizer_WT1P_B.aux'
                if canonical(before)!=canonical(after):
                    raise ValueError('Parameters changed during initialization')
                values=shlex.split(after.read_text().splitlines()[2])
                if values[:2]!=['1','1'] or list(map(float,values[2:]))!=constants:
                    raise ValueError('Imported parameters differ from entered values')
                rows=table(run/'powerworld.csv'); times=[row[0] for row in rows]
                if len(rows)!=round(1/step)+3 or times[0]!=0 or times[-1]!=1 or any(b<a for a,b in zip(times,times[1:])):
                    raise ValueError('Incomplete raw history')
                if sum(a==b for a,b in zip(times,times[1:]))!=2:
                    raise ValueError('Fault-side duplicate samples not preserved')
                hashes.append(hashlib.sha256((run/'powerworld.csv').read_bytes()).hexdigest())
            if hashes[0]!=hashes[1]:
                raise ValueError('Cold raw outputs differ')
            matrices[mode,label]=rows
            profiles.append(dict(profile=mode,step_s=step,samples=len(rows),classification='behavioral-diagnostic-not-parity'))
    for label in ('coarse','fine'):
        a,b=matrices['fault',label],matrices['direct',label]
        if any(x[i]!=y[i] for x,y in zip(a,b) for i in range(9) if i!=6):
            raise ValueError('Changed TR-only behavioral discriminator')
        if max(abs(x[6]-y[6]) for x,y in zip(a,b))<.1:
            raise ValueError('Inactive voltage-filter discriminator')
    return dict(status='cold-repeatability-and-parameter-roundtrip-audited',runs=12,profiles=profiles)


if __name__=='__main__':
    print(json.dumps(audit(Path.cwd()),indent=2))
