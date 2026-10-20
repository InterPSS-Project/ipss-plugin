"""Cold OEL4C contract discriminators; exact inputs and all raw channels are retained."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
from basoel2_native_probe import configuration as base_configuration
from basoel2_powerworld_probe import model
from oel3c_powerworld_probe import validate_options

PROFILES={
    'flat':(1.,.01,.15,.25,-.25),
    'delayed-fault':(.35,.01,.15,.25,-.25),
    'zero-delay-fault':(.35,0.,.15,.25,-.25),
    'integral-fault':(.35,.01,0.,.25,-.25),
    'lower-limit-fault':(.35,.01,3.,4.,-.02),
    'qmax-fault':(0.,.01,.15,.25,-.25),
}

def configuration(profile,raw):
    if profile not in PROFILES:raise ValueError('Unknown OEL4C profile')
    cfg=base_configuration('flat' if profile=='flat' else 'ifd-fault',raw)
    cfg['dynamic']='\n'.join(line for line in cfg['dynamic'].splitlines() if "'BASOEL2'" not in line)+'\n'
    cfg['dynamic']+="1 'OEL4C' '1' "+' '.join(map(str,PROFILES[profile]))+' /\n'
    return cfg

def specification(profile,step,relative):
    if profile not in PROFILES or step not in (.0005,.00025):raise ValueError('Unknown profile or integration step')
    gen=lambda field:"GEN 1 '1'|"+field
    # The shared runner requires a contingency; the flat profile schedules it strictly after the 0.5-s horizon.
    events=[dict(time_s=.6,object='Bus 1',action='FAULT 3PB IMP 0 0.2')] if profile=='flat' else [dict(time_s=.05,object='Bus 1',action='FAULT 3PB IMP 0 0.2'),
        dict(time_s=.1,object='Bus 1',action='CLEARFAULT')]
    return dict(name='oel4c-'+profile,raw='ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw',
        raw_format='PTI33',dyr=relative,bootstrap_powerworld=True,bootstrap_wait_s=8,auto_correct=False,
        simulation=dict(start_s=0,stop_s=.5,step_s=step),contingency=dict(name='Oel4c',
            memo='Exact documented delay and bounded PI discriminators; no fitting or corrections',events=events),
        results=['Bus 1 | TSVpu','Bus 2 | TSVpu']+[gen(f) for f in ('TSGenP','TSGenQ','TSSpeed','TSRotorAngle')]
            +["GEN 2 '1'|TSSpeed","GEN 2 '1'|TSRotorAngle"]+[gen(f) for f in ('TSEfd','TSIfd','TSVOEL')]
            +[gen('TSExciterState:'+str(i)) for i in range(1,6)]+[gen('TSOELState:1')]
            +[gen('TSOELOther:'+str(i)) for i in range(1,5)])

def validate_readback(body,cfg,profile):
    limiter=model(body,'OverExcitationLimiter_OEL4C');exc=model(body,'Exciter_AC1C');machine=model(body,'MachineModel_GENROU')
    if [float(limiter[k]) for k in ('Qref','TDelay','Kp','Ki','Vmin')]!=list(PROFILES[profile]):
        raise ValueError('OEL4C constants changed')
    fields=('Tr','Tb','Tc','Ka','Ta','VaMax','VaMin','Te','Kf','Tf','Kc','Kd','Ke','E1','SE1','E2','SE2','Eefmax','Eefmin','Vfemax','Vemin')
    if [float(exc[k]) for k in fields]!=cfg['components'][0]['constants'] or [int(exc[k]) for k in ('OEL','UEL')]!=[1,0]:
        raise ValueError('Exciter parameters changed')
    c=cfg['constants'];expected=dict(H=c[4],D=c[5],Ra=cfg['resistance'],Xd=c[6],Xq=c[7],Xdp=c[8],Xqp=c[9],Xdpp=c[10],Xl=c[11],
        Tdop=c[0],Tqop=c[2],Tdopp=c[1],Tqopp=c[3],S1=c[12],S12=c[13],RComp=0.,XComp=0.)
    if any(float(machine[k])!=v for k,v in expected.items()):raise ValueError('Machine parameters changed')
    return dict(limiter=limiter,exciter=exc,machine=machine,options=validate_options(body))

def main():
    tag,entered,profile=sys.argv[1:];root=Path.cwd();step=float(entered)
    if not tag or not all(c.isalnum() or c in '-_' for c in tag):raise ValueError('Invalid output tag')
    out=root/'target'/('oel4c-powerworld-'+tag);out.mkdir(exist_ok=False)
    for name in ('basoel2_native_probe.py','basoel2_powerworld_probe.py','genrou_native_probe.py','hplntd_fault_probe.py',
        'oel3c_powerworld_probe.py','oel3c_routing_probe.py','oel3c_native_probe.py'):
        shutil.copy2(root/'tools'/name,out/name)
    shutil.copy2(__file__,out/'probe.py')
    shutil.copy2(root/'ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py',out/'runner.py')
    cfg=configuration(profile,(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text())
    (out/'source.dyr').write_text(cfg['dynamic'])
    spec=specification(profile,step,(out/'source.dyr').relative_to(root).as_posix())
    (out/'spec.json').write_text(json.dumps(spec,indent=2)+'\n')
    module=importlib.util.spec_from_file_location('oel4c_independent_runner',out/'runner.py')
    runner=importlib.util.module_from_spec(module);module.loader.exec_module(runner)
    original=subprocess.Popen
    def hidden(*args,**kwargs):
        startup=subprocess.STARTUPINFO();startup.dwFlags|=subprocess.STARTF_USESHOWWINDOW;startup.wShowWindow=subprocess.SW_HIDE
        kwargs.setdefault('startupinfo',startup);return original(*args,**kwargs)
    runner.subprocess.Popen=hidden;render=runner.render_aux
    def audited(settings,directory):
        text=render(settings,directory)
        for marker in ('POWER_FLOW_SOLVED','INITIALIZED'):
            anchor=f'WriteTextToFile("progress.txt", "{marker};");';assert text.count(anchor)==1
            text=text.replace(anchor,anchor+f'\nTSWriteOptions("{marker.lower()}-models.aux", [YES, YES, YES, NO, YES, NO, YES], PRIMARY);\nLogSave("{marker.lower()}.log", NO);')
        return text
    runner.render_aux=audited
    manifest=runner.run(out/'spec.json',root,out,'C:/Program Files/PowerWorld/Simulator Education-Evalution 24/pwrworld.exe',90,False)
    records={stage:validate_readback((out/(stage+'-models.aux')).read_text(),cfg,profile)
        for stage in ('power_flow_solved','initialized')}
    (out/'model-readback.json').write_text(json.dumps(dict(status='exact-model-parameter-readback',records=records),indent=2)+'\n')
    print(manifest)

if __name__=='__main__':main()
