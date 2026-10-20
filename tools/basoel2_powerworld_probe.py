"""Cold exact-model PowerWorld references; raw channels and readbacks are retained."""
import importlib.util
import json
import re
from pathlib import Path
import shutil
import subprocess
import sys
from basoel2_native_probe import configuration


def model(body,name):
    match=re.search(r'(?ms)^'+re.escape(name)+r'\s*\((.*?)\)\s*\{(.*?)\}',body)
    if not match:raise ValueError('Missing model readback: '+name)
    fields=[s.strip() for s in match.group(1).replace('\n','').split(',')]
    values=[s.strip('"') for s in re.findall(r'"[^"]*"|\S+',match.group(2))]
    if len(fields)!=len(values):raise ValueError('Invalid readback cardinality')
    row=dict(zip(fields,values))
    if row['ID']!='1' or row['Status']!='Active' or int(row['BusNum'])!=1:raise ValueError('Invalid model owner/status')
    return row


def validate_readback(body,cfg):
    limiter=model(body,'OverExcitationLimiter_BASOEL2');exc=model(body,'Exciter_AC1C');machine=model(body,'MachineModel_GENROU')
    fields=('OELFlag','VHzFlag','OELIn','TRoel','Kp','Ki','OEL1','OET1','OEL2','OET2','OEL3','OET3','TRVHz','KVHz','VHzmin')
    expected=list(cfg['components'][1]['icons'])+cfg['components'][1]['constants']
    if [float(limiter[k]) for k in fields]!=expected:raise ValueError('Limiter parameters changed')
    fields=('Tr','Tb','Tc','Ka','Ta','VaMax','VaMin','Te','Kf','Tf','Kc','Kd','Ke','E1','SE1','E2','SE2','Eefmax','Eefmin','Vfemax','Vemin')
    if [float(exc[k]) for k in fields]!=cfg['components'][0]['constants']:
        raise ValueError('Exciter constants changed')
    if [int(exc[k]) for k in ('OEL','UEL')]!=list(cfg['components'][0]['icons']):raise ValueError('Exciter connections changed')
    c=cfg['constants'];expected=dict(H=c[4],D=c[5],Ra=cfg['resistance'],Xd=c[6],Xq=c[7],Xdp=c[8],Xqp=c[9],Xdpp=c[10],Xl=c[11],
        Tdop=c[0],Tqop=c[2],Tdopp=c[1],Tqopp=c[3],S1=c[12],S12=c[13],RComp=0.,XComp=0.)
    if any(float(machine[k])!=v for k,v in expected.items()):raise ValueError('Machine parameters changed')
    options={key:re.search(r'"'+key+r'"\s+"([^"]*)"',body) for key in ('MOD_LoadModelDefault','MOD_IntegrationMethod')}
    if any(value is None for value in options.values()):raise ValueError('Missing solver/load options')
    options={key:value.group(1) for key,value in options.items()}
    if options!={'MOD_LoadModelDefault':'Impedance','MOD_IntegrationMethod':'RK2'}:raise ValueError('Solver/load options changed')
    return dict(limiter=limiter,exciter=exc,machine=machine,options=options)


def specification(profile,step,relative):
    if profile not in ('efd-fault','ifd-fault','vfe-fault','ifd-zero-filter','disabled-fault'):
        raise ValueError('Unknown BASOEL2 fault profile')
    if step not in (.0005,.00025):raise ValueError('Unsupported integration step')
    gen=lambda field:"GEN 1 '1'|"+field
    return dict(name='basoel2-'+profile,raw='ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw',
        raw_format='PTI33',dyr=relative,bootstrap_powerworld=True,bootstrap_wait_s=8,auto_correct=False,
        simulation=dict(start_s=0,stop_s=.5,step_s=step),contingency=dict(name='Basoel2',
            memo='Exact physical inputs and published PowerWorld policy; no fitted values or corrections',
            events=[dict(time_s=.05,object='Bus 1',action='FAULT 3PB IMP 0 0.2'),
                    dict(time_s=.1,object='Bus 1',action='CLEARFAULT')]),
        results=['Bus 1 | TSVpu','Bus 2 | TSVpu']+[gen(f) for f in ('TSGenP','TSGenQ','TSSpeed','TSRotorAngle')]
            +["GEN 2 '1'|TSSpeed","GEN 2 '1'|TSRotorAngle"]+[gen(f) for f in ('TSEfd','TSIfd','TSVOEL')]
            +[gen('TSExciterState:'+str(i)) for i in range(1,6)]
            +[gen('TSOELState:'+str(i)) for i in range(1,4)]
            +[gen('TSOELOther:'+str(i)) for i in range(1,13)])


def main():
    tag,entered,profile=sys.argv[1:];root=Path.cwd();step=float(entered)
    if not tag or not all(c.isalnum() or c in '-_' for c in tag):raise ValueError('Invalid output tag')
    out=root/'target'/('basoel2-powerworld-'+tag);out.mkdir(exist_ok=False)
    for name in ('basoel2_native_probe.py','genrou_native_probe.py','hplntd_fault_probe.py'):
        shutil.copy2(root/'tools'/name,out/name)
    shutil.copy2(__file__,out/'probe.py')
    shutil.copy2(root/'ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py',out/'runner.py')
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    cfg=configuration(profile,raw);(out/'source.dyr').write_text(cfg['dynamic'])
    spec=specification(profile,step,(out/'source.dyr').relative_to(root).as_posix())
    (out/'spec.json').write_text(json.dumps(spec,indent=2)+'\n')
    module=importlib.util.spec_from_file_location('basoel2_independent_runner',out/'runner.py')
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
    readbacks={stage:validate_readback((out/(stage+'-models.aux')).read_text(),cfg) for stage in ('power_flow_solved','initialized')}
    (out/'model-readback.json').write_text(json.dumps(dict(status='exact-model-parameter-readback',records=readbacks),indent=2)+'\n')
    print(manifest)


if __name__=='__main__':main()
