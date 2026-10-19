"""Repeatable TGOV1 governor-reference increments in an independent small testbed."""
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
from tgov1_limit_probe import profile

RESULTS = [
    'Bus 1 | TSVpu', 'Bus 2 | TSVpu', "GEN 1 '1'|TSGenP", "GEN 1 '1'|TSGenQ",
    "GEN 1 '1'|TSSpeed", "GEN 1 '1'|TSRotorAngle",
    "GEN 1 '1'|TSGovernorState:1", "GEN 1 '1'|TSGovernorState:2",
    "GEN 2 '1'|TSSpeed", "GEN 2 '1'|TSRotorAngle",
    *[f"GEN 1 '1'|TSMachineState:{i}" for i in range(3,7)],
]


def specification(direction, resolution, repeat):
    settings=profile(direction,resolution,repeat)
    return dict(name='tgov1-limits-'+settings['name'],raw='network.raw',dyr='dynamic.dyr',
                raw_format='PTI33',bootstrap_powerworld=True,bootstrap_wait_s=8,auto_correct=False,
                simulation=dict(start_s=0,stop_s=settings['end_s'],step_s=settings['step_s']),
                contingency=dict(name='GovLimits',memo='Governor speed-load reference increment and inward release',
                                 events=[dict(time_s=time,object="Gen 1 '1'",action=f'CHANGEBY Governor_Setpoint {increment:g} pu')
                                         for time,increment in [(settings['apply_s'],settings['increment']),
                                                                (settings['release_s'],-settings['increment'])]]),
                results=list(RESULTS))


def canonical_sha(body):
    return hashlib.sha256(body.replace(b'\r\n',b'\n').replace(b'\r',b'\n')).hexdigest()


def main():
    direction,resolution,repeat=sys.argv[1:];spec=specification(direction,resolution,repeat)
    root=Path.cwd();out=root/'target'/('tgov1-powerworld-limits-'+direction+'-'+resolution+'-'+repeat)
    out.mkdir(exist_ok=False);workspace=out/'workspace';workspace.mkdir()
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_bytes()
    dyr=base64.b64decode((root/'ipss.test.plugin.core/src/test/resources/tgov1-limit-native/dynamic.dyr.b64').read_bytes())
    assert canonical_sha(raw)=='986c6b3c8201ff0c4581a60e48af3438a14c692c12e4f8b966f4f2c710daa02b'
    assert canonical_sha(dyr)=='5ee9b74bbaae06876bda2b032935cf221b34256435874718f7cac395d8be029f'
    (workspace/'network.raw').write_bytes(raw);(workspace/'dynamic.dyr').write_bytes(dyr)
    (workspace/'spec.json').write_text(json.dumps(spec,indent=2)+'\n')
    shutil.copy2(__file__,out/'probe.py');shutil.copy2(root/'ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py',out/'runner.py')
    module=importlib.util.spec_from_file_location('tgov1_independent_runner',out/'runner.py')
    runner=importlib.util.module_from_spec(module);module.loader.exec_module(runner)
    original=subprocess.Popen
    def hidden(*args,**kwargs):
        startup=subprocess.STARTUPINFO();startup.dwFlags|=subprocess.STARTF_USESHOWWINDOW;startup.wShowWindow=subprocess.SW_HIDE
        kwargs.setdefault('startupinfo',startup);return original(*args,**kwargs)
    runner.subprocess.Popen=hidden
    print(runner.run(workspace/'spec.json',workspace,out,'C:/Program Files/PowerWorld/Simulator Education-Evalution 24/pwrworld.exe',90,False))


if __name__=='__main__':main()
