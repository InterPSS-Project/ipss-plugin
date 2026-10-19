"""Exact published active-stall profile; no parameter overrides or correction."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
from wt12a1b_native_probe import configuration


def main():
    root=Path.cwd();tag,entered_step,profile=sys.argv[1:];step=float(entered_step)
    assert step in (.0005,.00025) and tag and all(c.isalnum() or c in '-_' for c in tag)
    out=root/'target'/('wt12a1b-powerworld-'+tag);out.mkdir(exist_ok=False)
    for source,destination in [('tools/wt12a1b_powerworld_probe.py','probe.py'),('tools/wt12a1b_native_probe.py','wt12a1b_native_probe.py'),('tools/hplntd_fault_probe.py','hplntd_fault_probe.py'),('ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py','runner.py')]:shutil.copy2(root/source,out/destination)
    dynamic,constants,events=configuration('fault' if profile=='sustained' else profile)
    if profile=='sustained':events=[(.05,'fault'),(.4,'clear')]
    source=next(line for line in dynamic.splitlines() if "'WT12A1B'" in line)
    wrapper="1 'USRMDL' '1' 'WT12A1U_B' 105 0 1 14 3 2 0 "+' '.join(map(str,constants))+' /'
    (out/'source.dyr').write_text(dynamic.replace(source,wrapper))
    relative=(out/'source.dyr').relative_to(root).as_posix()
    fields=['BusNum','ID','Tr','Rmax','Rmin','T','Pmin','Pset','Vt1','T1','Vt2','T2','Vt3','T3','Vt4','T4']
    spec=dict(name='published-wt1p-b-'+profile,raw='ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33_wt1g1.raw',raw_format='PTI33',dyr=relative,powerworld_dyr=relative,
              bootstrap_powerworld=True,bootstrap_wait_s=8,auto_correct=False,simulation=dict(start_s=0,stop_s=1,step_s=step),
              contingency=dict(name='ActiveStall',memo='Exact active-stall constants; native wrapper translation only; no fitting',events=[dict(time_s=t,object='Bus 1',action='FAULT 3PB IMP 0 0.1' if a=='fault' else 'CLEARFAULT') for t,a in events]),
              results=['Bus 1 | TSVpu','Bus 2 | TSVpu',"GEN 1 '1'|TSGenP","GEN 1 '1'|TSGenQ","GEN 1 '1'|TSSpeed"]+["GEN 1 '1'|TSStabilizerState:"+str(i) for i in range(1,4)])
    (out/'spec.json').write_text(json.dumps(spec,indent=2)+'\n')
    module=importlib.util.spec_from_file_location('wt1p_b_runner',out/'runner.py');runner=importlib.util.module_from_spec(module);module.loader.exec_module(runner)
    original=subprocess.Popen
    def hidden(*args,**kwargs):
        startup=subprocess.STARTUPINFO();startup.dwFlags|=subprocess.STARTF_USESHOWWINDOW;startup.wShowWindow=subprocess.SW_HIDE
        kwargs.setdefault('startupinfo',startup);return original(*args,**kwargs)
    runner.subprocess.Popen=hidden;render=runner.render_aux
    def audited(spec,directory):
        text=render(spec,directory)
        for marker in ['POWER_FLOW_SOLVED','INITIALIZED']:
            anchor=f'WriteTextToFile("progress.txt", "{marker};");';assert text.count(anchor)==1
            text=text.replace(anchor,anchor+'\n'+f'SaveData("{marker.lower()}-Stabilizer_WT1P_B.aux", AUX, Stabilizer_WT1P_B, [{", ".join(fields)}], []);\nLogSave("{marker.lower()}.log", NO);')
        return text
    runner.render_aux=audited
    print(runner.run(out/'spec.json',root,out,'C:/Program Files/PowerWorld/Simulator Education-Evalution 24/pwrworld.exe',90,False))


if __name__=='__main__':main()
