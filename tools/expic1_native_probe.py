"""Cold seven-state excitation references with parameter readback and native event rows."""
import csv,json,math,os,shutil,struct,sys
from pathlib import Path
from tgov1_limit_probe import checked,sha,STEPS


def configuration(mode,grid,repeat):
    if mode not in ('flat','step-up','step-down','fault','dynamic','upper','lower',
                    'integral-upper','integral-lower','feedback','saturation','constant-source','constant-source-wide','bridge-current') or grid not in STEPS or repeat not in ('a','b'):
        raise ValueError('Unknown excitation profile')
    c=[0,3.5,1,1,-.87,0,0,0,1,-.87,0,1,0,7.5184,0,0,0,1,0,1.2,0,6.4774,0,.08]
    if mode=='dynamic':c=[.02,3.5,.2,1.5,-1,.05,.01,.03,1.2,-.9,.03,.3,.1,8,0,1,.5,3,.05,4,.1,5,.2,.08]
    if mode=='feedback':c[10]=.03;c[11]=.3
    if mode=='saturation':
        c=[.02,3.5,.2,1.5,-1,.05,.01,.03,1.2,-.9,.03,.3,.1,8,0,1,.5,.8,.05,1.2,.1,5,.2,.08]
    if mode in ('constant-source','constant-source-wide'):c[21]=c[22]=0
    if mode=='constant-source-wide':c[3]=c[8]=2
    if mode=='bridge-current':c[22]=.4
    return dict(name=mode+'-'+grid,mode=mode,repeat=repeat,step_s=STEPS[grid],constants=c,
                reference_step=3 if mode=='integral-upper' else -3 if mode=='integral-lower' else .3 if mode=='upper' else -.3 if mode=='lower' else -.02 if mode=='step-down' else .02,
                end_s=.6)


def inputs(root,settings):
    source=root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB'
    raw=(source/'SMIB_v33.raw').read_text().replace('INTERPSS / ANDES GENROU CONFORMANCE','EXCITATION SYSTEM SMIB')
    dyr=(source/'SMIB_v33_genrou_tgov1.dyr').read_text()
    dyr='\n'.join(line for line in dyr.splitlines() if 'TGOV1' not in line)+'\n'
    dyr+="1 '"+settings.get('model','EXPIC1')+"' '1' "+' '.join(map(str,settings['constants']))+' /\n'
    return raw,dyr


def main():
    settings=configuration(*sys.argv[1:])
    import psse3607,psspy,dyntools
    root=Path.cwd();out=root/settings.get('artifact_root','ipss.test.plugin.core/testData/private/expic1-smib-v2')/(settings['name']+'-'+settings['repeat'])
    out.mkdir(parents=True,exist_ok=False)
    raw,dyr=inputs(root,settings);(out/'network.raw').write_text(raw);(out/'dynamic.dyr').write_text(dyr)
    scripts=['expic1_native_probe.py','tgov1_limit_probe.py']+list(settings.get('extra_scripts',[]))
    for name in scripts:shutil.copy2(root/'tools'/name,out/name)
    os.chdir(out)
    checked('initialize',psspy.psseinit(50))
    for fn in (psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output):checked('log',fn(2,'native.log',[0,0]))
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL'
    checked('library',psspy.addmodellibrary(str(library)));checked('network',psspy.read(0,'network.raw'))
    checked('flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
    checked('generators',psspy.cong(0))
    for stage in (1,2,3):checked('load',psspy.conl(0,1,stage,[0,0],[0.]*4))
    checked('order',psspy.ordr(0));checked('factor',psspy.fact());checked('solution',psspy.tysl(0))
    checked('dynamic',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''))
    layout={}
    for kind in ('CON','STATE','VAR'):
        result=psspy.mdlind(1,'1','EXC','N'+kind)
        count=0 if kind=='VAR' and result[0]==6 else checked('count',result);layout['N'+kind]=count
        if count:
            index=checked('index',psspy.mdlind(1,'1','EXC',kind));layout[kind]=index
            if kind in ('STATE','VAR'):
                fn=psspy.state_channel if kind=='STATE' else psspy.var_channel
                for i in range(count):checked('channel',fn([-1,index+i],'EXC_'+kind+str(i)))
    assert layout['NCON']==len(settings['constants']) and layout['NSTATE']==settings.get('state_count',7)
    def constants():return [checked('constant',psspy.dsrval('CON',layout['CON']+i)) for i in range(len(settings['constants']))]
    before=constants()
    for code,name in ((1,'ANGLE_DEG'),(2,'P_PU'),(3,'Q_PU'),(5,'EFD'),(7,'SPEED_DEV'),(8,'XADIFD'),(9,'ECOMP')):
        checked('machine channel',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2):checked('voltage channel',psspy.voltage_and_angle_channel([-1,-1,-1,bus],['V'+str(bus),'A'+str(bus)+'_DEG']))
    di,df=psspy.getdefaultint(),psspy.getdefaultreal()
    checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,settings['step_s']]+[df]*5))
    checked('start',psspy.strt_2([0,0],'native.out'));after=constants()
    expected=[struct.unpack('f',struct.pack('f',x))[0] for x in settings['constants']]
    assert before==after==expected
    events=[]
    checked('prefault',psspy.run(0,.05,1000000,1,0))
    if settings['mode'] not in ('flat','fault'):
        events.append(dict(action='reference-step',actual_time_s=checked('clock',psspy.dsrval('TIME'))))
        checked('reference',psspy.increment_vref(1,'1',settings['reference_step']))
    checked('response',psspy.run(0,.20,1000000,1,0))
    if settings['mode'] not in ('flat','fault'):
        events.append(dict(action='reference-restore',actual_time_s=checked('clock',psspy.dsrval('TIME'))))
        checked('reference restore',psspy.increment_vref(1,'1',-settings['reference_step']))
    checked('pre-fault',psspy.run(0,.30,1000000,1,0))
    if settings['mode'] in ('fault','dynamic'):
        events.append(dict(action='fault',actual_time_s=checked('clock',psspy.dsrval('TIME'))))
        checked('fault',psspy.dist_bus_fault(1,3,100.,[0.,50.]))
    checked('fault interval',psspy.run(0,.35,1000000,1,0))
    if settings['mode'] in ('fault','dynamic'):
        events.append(dict(action='clear',actual_time_s=checked('clock',psspy.dsrval('TIME'))))
        checked('clear',psspy.dist_clear_fault(1))
    checked('postfault',psspy.run(0,settings['end_s'],1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int))
    assert all(math.isfinite(x) for k in ['time']+keys for x in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,t in enumerate(data['time']):writer.writerow([format(t,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    Path('readback.json').write_text(json.dumps(dict(entered=before,initialized=after),indent=2)+'\n')
    log=Path('native.log').read_text(errors='replace');quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','native.out','native.csv','native.log','readback.json']+scripts
    manifest=dict(status='complete' if quality else 'rejected-quality',model=settings.get('model','EXPIC1'),settings=settings,layout=layout,events=events,
                  sample_count=len(data['time']),channel_count=len(keys),engine_module=psse3607.__name__,
                  library_sha256=sha(library),source_document_sha256=sha(library.parent.parent/'DOCS/ModelLibrary.pdf'),files={f:sha(f) for f in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(dict(profile=settings['name'],status=manifest['status'])));assert quality

if __name__=='__main__':main()
