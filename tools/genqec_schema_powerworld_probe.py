"""Round-trip the documented standard generator input without correction."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
from genqec_schema_probe import configuration


def main():
    root=Path.cwd();tag,entered_step,profile=sys.argv[1:];step=float(entered_step)
    assert profile in ('standard-0','standard-1') and step in (.0005,.00025) and tag and all(c.isalnum() or c in '-_' for c in tag)
    out=root/'target'/('genqec-schema-powerworld-'+tag);out.mkdir(exist_ok=False)
    for source,destination in [('tools/genqec_schema_powerworld_probe.py','probe.py'),('tools/genqec_schema_probe.py','genqec_schema_probe.py'),('tools/hplntd_fault_probe.py','hplntd_fault_probe.py'),('ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py','runner.py')]:shutil.copy2(root/source,out/destination)
    dynamic,_,_=configuration(profile);(out/'source.dyr').write_text(dynamic)
    relative=(out/'source.dyr').relative_to(root).as_posix()
    spec=dict(name='native-standard-genqec-'+profile,raw='ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw',raw_format='PTI33',dyr=relative,powerworld_dyr=relative,bootstrap_powerworld=True,bootstrap_wait_s=8,auto_correct=False,simulation=dict(start_s=0,stop_s=.3,step_s=step),contingency=dict(name='GeneratorSchema',memo='Exact native standard record; no fitting or overrides',events=[dict(time_s=.05,object='Bus 1',action='FAULT 3PB IMP 0 0.2'),dict(time_s=.1,object='Bus 1',action='CLEARFAULT')]),results=['Bus 1 | TSVpu','Bus 2 | TSVpu',"GEN 1 '1'|TSGenP","GEN 1 '1'|TSGenQ","GEN 1 '1'|TSSpeed"]+["GEN 1 '1'|TSMachineState:"+str(i) for i in range(1,7)])
    (out/'spec.json').write_text(json.dumps(spec,indent=2)+'\n')
    module=importlib.util.spec_from_file_location('generator_schema_runner',out/'runner.py');runner=importlib.util.module_from_spec(module);module.loader.exec_module(runner)
    original=subprocess.Popen
    def hidden(*args,**kwargs):
        startup=subprocess.STARTUPINFO();startup.dwFlags|=subprocess.STARTF_USESHOWWINDOW;startup.wShowWindow=subprocess.SW_HIDE;kwargs.setdefault('startupinfo',startup);return original(*args,**kwargs)
    runner.subprocess.Popen=hidden
    print(runner.run(out/'spec.json',root,out,'C:/Program Files/PowerWorld/Simulator Education-Evalution 24/pwrworld.exe',90,False))


if __name__=='__main__':main()
