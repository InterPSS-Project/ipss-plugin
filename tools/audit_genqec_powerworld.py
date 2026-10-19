"""Audit cold repeatable exact-input diagnostics, not cross-tool conformance."""
import csv
import hashlib
import json
import math
from pathlib import Path
from genqec_schema_probe import configuration


def canonical(path):return path.read_bytes().replace(b'\r\n',b'\n').replace(b'\r',b'\n')
def digest(path):return hashlib.sha256(canonical(path)).hexdigest()


def table(path):
    lines=path.read_text().splitlines();start=next(i for i,line in enumerate(lines) if line.startswith('Results,'))+1
    result=[list(map(float,row)) for row in csv.reader(lines[start:]) if row and row[0]!='END']
    if not result or any(len(row)!=12 or not all(math.isfinite(v) for v in row) for row in result):raise ValueError('Incomplete eleven-channel trajectory')
    return result


def audit(root):
    result=[]
    for profile in ('standard-0','standard-1'):
        dynamic,_,_=configuration(profile)
        for label,step in [('coarse',.0005),('fine',.00025)]:
            hashes=[]
            for repeat in ('a','b'):
                run=root/'target'/f'genqec-schema-powerworld-{profile}-{label}-final-{repeat}';m=json.loads((run/'manifest.json').read_text())
                if m['status']!='complete' or m['auto_correct'] or m['powerworld_model_overrides'] or m['simulation']!={'start_s':0,'stop_s':.3,'step_s':step}:raise ValueError('Changed simulation/correction contract')
                if m['powerworld_executable']['sha256']!='06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c':raise ValueError('Changed simulator version')
                if (run/'source.dyr').read_text()!=dynamic:raise ValueError('Changed exact native standard input')
                for metadata in m['inputs'].values():
                    if digest(root/metadata['path'])!=metadata['sha256']:raise ValueError('Changed input hash')
                for name,metadata in m['artifacts'].items():
                    if digest(run/name)!=metadata['sha256'] or len(canonical(run/name))!=metadata['bytes']:raise ValueError('Changed output hash/length')
                for archived,source in [('probe.py','tools/genqec_schema_powerworld_probe.py'),('genqec_schema_probe.py','tools/genqec_schema_probe.py'),('hplntd_fault_probe.py','tools/hplntd_fault_probe.py'),('runner.py','ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py')]:
                    if canonical(run/archived)!=canonical(root/source):raise ValueError('Changed generating source')
                expected=[dict(time_s=.05,object='Bus 1',action='FAULT 3PB IMP 0 0.2'),dict(time_s=.1,object='Bus 1',action='CLEARFAULT')]
                if m['contingency']['events']!=expected or len(m['result_selectors'])!=11:raise ValueError('Changed events/state scope')
                raw=table(run/'powerworld.csv');times=[r[0] for r in raw]
                if len(raw)!=round(.3/step)+3 or times[0]!=0 or times[-1]!=.3 or any(b<a for a,b in zip(times,times[1:])) or sum(a==b for a,b in zip(times,times[1:]))!=2:raise ValueError('Missing event-side rows or endpoints')
                hashes.append(hashlib.sha256((run/'powerworld.csv').read_bytes()).hexdigest())
            if hashes[0]!=hashes[1]:raise ValueError('Cold reference output differs')
            result.append(dict(profile=profile,step_s=step,samples=len(raw),classification='repeatable-trajectory-diagnostic-not-conformance'))
    return dict(status='eight-cold-exact-input-PowerWorld-diagnostics-audited',runs=8,profiles=result,post_initialization_parameter_roundtrip='not-proved-export-attempts-retained-separately')


if __name__=='__main__':print(json.dumps(audit(Path.cwd()),indent=2))
