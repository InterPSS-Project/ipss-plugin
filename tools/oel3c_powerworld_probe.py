"""Repeatable independent routing fault profile with exact controller round-trip exports."""
import importlib.util
import json
import re
from pathlib import Path
import shutil
import subprocess
import sys
from oel3c_routing_probe import configuration

def translate(cfg):
    lines=cfg['dynamic'].splitlines();source=cfg['components'][1];flag,exponent=source['icons'];c=source['constants']
    selector={1:1,2:0,3:2}[flag]
    # The target reader follows its published parameter order, unlike the native layout.
    values=[selector,*c[:3],exponent,*c[3:]]
    index=next(i for i,line in enumerate(lines) if "'OEL3C'" in line)
    lines[index]="1 'OEL3C' '1' "+' '.join(map(str,values))+' /'
    return '\n'.join(lines)+'\n'

def read_aux_model(body,name):
    match=re.search(r'(?ms)^'+re.escape(name)+r'\s*\((.*?)\)\s*\{(.*?)\}',body)
    if not match:raise ValueError('Missing model readback: '+name)
    fields=[x.strip() for x in match.group(1).replace('\n','').split(',')]
    values=re.findall(r'"[^"]*"|\S+',match.group(2))
    if len(values)!=len(fields):raise ValueError('Unexpected model readback cardinality')
    return dict(zip(fields,[x.strip('"') for x in values]))

def validate_readback(body,cfg):
    row=read_aux_model(body,'OverExcitationLimiter_OEL3C');oel=cfg['components'][1];flag,exponent=oel['icons'];c=oel['constants']
    expected=dict(BusNum=1,OELInput={1:1,2:0,3:2}[flag],ITFpu=c[0],Kscale=c[1],TF=c[2],K1=exponent,
        Koel=c[3],Toel=c[4],KPoel=c[5],VOELmax1=c[6],VOELmin1=c[7],VOELmax2=c[8],VOELmin2=c[9])
    if row['ID']!='1' or row['Status']!='Active':raise ValueError('Invalid limiter owner/status')
    for key,value in expected.items():
        if float(row[key])!=value:raise ValueError('Changed limiter readback: '+key)
    exc=read_aux_model(body,'Exciter_AC1C');values=cfg['components'][0]['constants']
    if exc['ID']!='1' or exc['Status']!='Active' or int(exc['BusNum'])!=1:raise ValueError('Invalid exciter owner/status')
    fields=['Tr','Tb','Tc','Ka','Ta','VaMax','VaMin','Te','Kf','Tf','Kc','Kd','Ke','E1','SE1','E2','SE2','Eefmax','Eefmin','Vfemax','Vemin']
    if [float(exc[k]) for k in fields]!=values or [int(exc[k]) for k in ('OEL','UEL')]!=list(cfg['components'][0]['icons']):
        raise ValueError('Changed exciter readback')
    machine=read_aux_model(body,'MachineModel_GENROU');c=cfg['constants']
    expected=dict(H=c[4],D=c[5],Ra=cfg['resistance'],Xd=c[6],Xq=c[7],Xdp=c[8],Xqp=c[9],Xdpp=c[10],Xl=c[11],
        Tdop=c[0],Tqop=c[2],Tdopp=c[1],Tqopp=c[3],S1=c[12],S12=c[13])
    if machine['ID']!='1' or machine['Status']!='Active' or int(machine['BusNum'])!=1:raise ValueError('Invalid machine owner/status')
    if any(float(machine[k])!=v for k,v in expected.items()):raise ValueError('Changed generator readback')
    return dict(limiter=row,exciter=exc,machine=machine)

def specification(profile,step,relative,translated):
    if profile not in ('efd-fault','ifd-fault','vfe-fault') or step not in (.0005,.00025):raise ValueError('Unknown profile or step')
    return dict(name='oel3c-routing-'+profile,raw='ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw',
        raw_format='PTI33',dyr=relative,powerworld_dyr=translated,bootstrap_powerworld=True,bootstrap_wait_s=8,auto_correct=False,
        simulation=dict(start_s=0,stop_s=.5,step_s=step),contingency=dict(name='OelRouting',
            memo='Exact physical-input routing profile; no fitting, parameter override or automatic correction',
            events=[dict(time_s=.05,object='Bus 1',action='FAULT 3PB IMP 0 0.2'),dict(time_s=.1,object='Bus 1',action='CLEARFAULT')]),
        results=['Bus 1 | TSVpu','Bus 2 | TSVpu',"GEN 1 '1'|TSGenP","GEN 1 '1'|TSGenQ",
            "GEN 1 '1'|TSSpeed","GEN 1 '1'|TSRotorAngle","GEN 2 '1'|TSSpeed"]+
            ["GEN 1 '1'|TSExciterState:"+str(i) for i in range(1,6)])

def validate_options(body):
    result={key:re.search(r'"'+key+r'"\s+"([^"]*)"',body) for key in ('MOD_LoadModelDefault','MOD_IntegrationMethod')}
    if any(value is None for value in result.values()):raise ValueError('Missing solver/load options')
    result={key:value.group(1) for key,value in result.items()}
    if result!={'MOD_LoadModelDefault':'Impedance','MOD_IntegrationMethod':'RK2'}:raise ValueError('Unexpected solver/load options')
    return result

def main():
    tag,entered_step,profile=sys.argv[1:];step=float(entered_step);root=Path.cwd()
    if not tag or not all(c.isalnum() or c in '-_' for c in tag):raise ValueError('Invalid output tag')
    out=root/'target'/('oel3c-powerworld-'+tag);out.mkdir(exist_ok=False)
    for source,destination in [('tools/oel3c_powerworld_probe.py','probe.py'),('tools/oel3c_routing_probe.py','oel3c_routing_probe.py'),
        ('tools/oel3c_native_probe.py','oel3c_native_probe.py'),('tools/genrou_native_probe.py','genrou_native_probe.py'),
        ('tools/hplntd_fault_probe.py','hplntd_fault_probe.py'),
        ('ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py','runner.py')]:shutil.copy2(root/source,out/destination)
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    cfg=configuration(profile,raw);(out/'source.dyr').write_text(cfg['dynamic']);(out/'translated.dyr').write_text(translate(cfg))
    spec=specification(profile,step,(out/'source.dyr').relative_to(root).as_posix(),(out/'translated.dyr').relative_to(root).as_posix())
    (out/'spec.json').write_text(json.dumps(spec,indent=2)+'\n')
    module=importlib.util.spec_from_file_location('oel3c_independent_runner',out/'runner.py');runner=importlib.util.module_from_spec(module);module.loader.exec_module(runner)
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
    rows={marker:validate_readback((out/(marker+'-models.aux')).read_text(),cfg) for marker in ('power_flow_solved','initialized')}
    options={marker:validate_options((out/(marker+'-models.aux')).read_text()) for marker in rows}
    (out/'model-readback.json').write_text(json.dumps(dict(status='exact-physical-parameter-readback',records=rows,options=options),indent=2)+'\n')
    print(manifest)

if __name__=='__main__':main()
