"""Cold SMIB discriminators for fixed/down-only controller limits and native response flags."""
import csv, json, math, os, shutil, sys
from pathlib import Path
from reeca_native_probe import configuration as electrical_configuration, inputs as electrical_inputs
from repca_native_probe import configuration as plant_configuration
from tgov1_limit_probe import checked, sha, STEPS


def configuration(mode, grid, repeat):
    policy, direction = mode.rsplit('-', 1)
    if policy not in ('normal', 'fixed', 'down', 'baseload', 'baseload2', 'uncontrolled') or direction not in ('up', 'down'):
        raise ValueError('Expected normal/fixed/down/baseload and up/down')
    if grid not in STEPS or repeat not in ('a', 'b'):
        raise ValueError('Expected coarse/fine and a/b')
    return dict(mode=mode, policy=policy, increment=.1 if direction=='up' else -.1,
                step_s=STEPS[grid], grid=grid, repeat=repeat, end_s=.5)


def inputs(root, settings):
    e=electrical_configuration('q','coarse','a')
    p=plant_configuration('flat-q','coarse','a')
    # Equal absolute P limits encode fixed response in the reference engine;
    # native response flags are tested independently with unchanged model limits.
    if settings['policy'] in ('fixed','down'):
        e['constants'][25]=p['constants'][22]=.24
    if settings['policy']=='fixed':
        e['constants'][26]=p['constants'][23]=.24
    raw,dyr=electrical_inputs(root,e)
    raw=raw.replace('INTERPSS / ANDES BUS-1062-UNIT-2 PROFILE','RENEWABLE ACTIVE-POWER RESPONSE SMIB')
    if settings['policy']=='uncontrolled':
        return raw,'\n'.join(line for line in dyr.splitlines() if 'REECA1' not in line)+'\n'
    selectors=list(p['icons']);selectors[3]="'"+str(selectors[3])+"'"
    dyr+="1 'REPCA1' '1' "+' '.join(map(str,selectors+p['constants']))+' /\n'
    return raw,dyr


def main():
    settings=configuration(*sys.argv[1:])
    import psse3607, psspy, dyntools
    root=Path.cwd()
    out=root/'ipss.test.plugin.core/testData/private/bsg-smib-v2'/(settings['mode']+'-'+settings['grid']+'-'+settings['repeat'])
    out.mkdir(parents=True,exist_ok=False)
    raw,dyr=inputs(root,settings)
    (out/'network.raw').write_text(raw);(out/'dynamic.dyr').write_text(dyr)
    for file in ('bsg_native_probe.py','reeca_native_probe.py','repca_native_probe.py','tgov1_limit_probe.py'):
        shutil.copy2(root/'tools'/file,out/file)
    os.chdir(out)
    checked('initialize',psspy.psseinit(50))
    for fn in (psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output):
        checked('log',fn(2,'native.log',[0,0]))
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL'
    checked('library',psspy.addmodellibrary(str(library)))
    checked('network',psspy.read(0,'network.raw'))
    di,df=psspy.getdefaultint(),psspy.getdefaultreal()
    flags=[di]*7;flags[5]=1;flags[6]=2 if settings['policy']=='baseload2' else 1 if settings['policy']=='baseload' else 0
    checked('flags',psspy.machine_chng_4(1,'1',flags,[df]*17,''))
    checked('flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
    checked('generators',psspy.cong(0))
    for stage in (1,2,3):checked('loads',psspy.conl(0,1,stage,[0,0],[0.]*4))
    for label,fn in [('order',lambda:psspy.ordr(0)),('factor',psspy.fact),('solution',lambda:psspy.tysl(0))]:checked(label,fn())
    checked('dynamic',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''))
    layout={}
    categories=[('WGEN','CONVERTER')] if settings['policy']=='uncontrolled' else [('WAUX','PLANT'),('WELEC','ELEC'),('WGEN','CONVERTER')]
    for category,prefix in categories:
        layout[prefix]={}
        for kind in ('CON','ICON','STATE','VAR'):
            count=checked('count',psspy.windmind(1,'1',category,'N'+kind));layout[prefix]['N'+kind]=count
            if not count:continue
            index=checked('index',psspy.windmind(1,'1',category,kind));layout[prefix][kind]=index
            if kind in ('STATE','VAR'):
                fn=psspy.state_channel if kind=='STATE' else psspy.var_channel
                for i in range(count):checked('channel',fn([-1,index+i],prefix+'_'+kind+str(i)))
    def readback():
        return {prefix:[checked('constant',psspy.dsrval('CON',data['CON']+i)) for i in range(data['NCON'])] for prefix,data in layout.items()}
    before=readback()
    for code,name in ((2,'P_PU'),(3,'Q_PU'),(22,'PREF_INPUT')):checked('machine channel',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2,3):
        checked('voltage channel',psspy.voltage_and_angle_channel([-1,-1,-1,bus],['V'+str(bus),'A'+str(bus)+'_DEG']))
        checked('frequency channel',psspy.bus_frequency_channel([-1,bus],'F'+str(bus)+'_DEV'))
    checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,settings['step_s']]+[df]*5))
    checked('start',psspy.strt_2([0,0],'native.out'));after=readback()
    assert before==after,'Initialization changed entered constants'
    checked('prefault',psspy.run(0,.05,1000000,1,0))
    if settings['policy']!='uncontrolled':
        plant_reference=checked('plant reference',psspy.dsrval('VAR',layout['PLANT']['VAR']+3))
        checked('reference change',psspy.change_wnmod_var(1,'1','REPCA1',4,plant_reference+settings['increment']))
    checked('pre-fault response',psspy.run(0,.20,1000000,1,0))
    checked('fault',psspy.dist_bus_fault(2,3,230.,[0.,100.]))
    checked('fault response',psspy.run(0,.25,1000000,1,0))
    checked('clear',psspy.dist_clear_fault(1))
    checked('postfault',psspy.run(0,settings['end_s'],1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int))
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,t in enumerate(data['time']):writer.writerow([format(t,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    log=Path('native.log').read_text(errors='replace')
    quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    readback_data=dict(entered=before,initialized=after,baseload_flag=checked('flag readback',psspy.macint(1,'1','BASFLG')))
    Path('readback.json').write_text(json.dumps(readback_data,indent=2)+'\n')
    files=['network.raw','dynamic.dyr','native.csv','native.out','native.log','readback.json','bsg_native_probe.py','reeca_native_probe.py','repca_native_probe.py','tgov1_limit_probe.py']
    manifest=dict(status='complete' if quality else 'rejected-quality',settings=settings,layout=layout,
                  mapping='Explicit REECA1/REPCA1 absolute active-power limits; BSG is not read by this engine',
                  sample_count=len(data['time']),channel_count=len(keys),engine_module=psse3607.__name__,
                  library_sha256=sha(library),source_document_sha256=sha(library.parent.parent/'DOCS/ModelLibrary.pdf'),
                  files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(dict(mode=settings['mode'],status=manifest['status'],samples=manifest['sample_count'])))
    assert quality

if __name__=='__main__':main()
