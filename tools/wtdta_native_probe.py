"""Cold native drive-train reference matrix, keeping legacy angular-frequency data separate."""
import csv,json,math,os,shutil,sys
from pathlib import Path
from tgov1_limit_probe import checked,sha,STEPS
from wttqa_native_probe import configuration as torque_configuration,dynamic as torque_dynamic

MODES=('flat','fault','curve','curve-flat','single','single-curve','frequency','grid50','undamped')


def configuration(mode,grid,repeat):
    if mode not in MODES or grid not in STEPS or repeat not in ('a','b'):raise ValueError('Unknown native drive-train profile')
    return dict(name=mode+'-'+grid,mode=mode,repeat=repeat,step_s=STEPS[grid],
                constants=[5.,0. if mode=='undamped' else .1,0. if mode.startswith('single') else .8,3. if mode=='frequency' else 1.5,.2],
                nominal_hz=50 if mode=='grid50' else 60,curve=mode in ('curve','curve-flat','single-curve','undamped'),
                fault=mode not in ('flat','curve-flat'),apply_s=.05,clear_s=.1,end_s=.5,fault_ohms=529.)


def inputs(root,settings):
    base=torque_configuration('curve' if settings['curve'] else 'speed','coarse','a');text=torque_dynamic(root,base)
    original="1 'WTDTA1' '1' 5 .1 .8 1.5 .2 /";assert text.count(original)==1
    text=text.replace(original,"1 'WTDTA1' '1' "+' '.join(map(str,settings['constants']))+' /')
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.raw').read_text()
    lines=raw.splitlines();header=lines[0].split(',');assert header[5].startswith(' 60.00')
    header[5]=header[5].replace('60.00',str(settings['nominal_hz'])+'.00',1);lines[0]=','.join(header)
    return '\n'.join(lines)+'\n',text


def main():
    settings=configuration(*sys.argv[1:])
    import psse3607
    import psspy,dyntools
    root=Path.cwd();out=root/'target'/('wtdta-native-'+settings['name']+'-'+settings['repeat']);out.mkdir(exist_ok=False)
    for name in ['wtdta_native_probe.py','wttqa_native_probe.py','tgov1_limit_probe.py']:shutil.copy2(root/'tools'/name,out/name)
    raw,dyr=inputs(root,settings);(out/'network.raw').write_text(raw);(out/'dynamic.dyr').write_text(dyr);os.chdir(out)
    checked('initialize',psspy.psseinit(50))
    for fn in (psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output):checked('log',fn(2,'native.log',[0,0]))
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL';document=library.parent.parent/'DOCS/ModelLibrary.pdf'
    checked('library',psspy.addmodellibrary(str(library)));checked('network',psspy.read(0,'network.raw'))
    di,df=psspy.getdefaultint(),psspy.getdefaultreal();flags=[di]*7;flags[5]=1
    checked('wind mode',psspy.machine_chng_4(1,'1',flags,[df]*17,''));checked('flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
    checked('generators',psspy.cong(0))
    for stage in (1,2,3):checked('loads',psspy.conl(0,1,stage,[0,0],[0.]*4))
    checked('order',psspy.ordr(0));checked('factor',psspy.fact());checked('solution',psspy.tysl(0));checked('dynamic',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''))
    layout={}
    for kind in ('CON','STATE','VAR'):
        index=checked('index',psspy.windmind(1,'1','WMECH',kind));count=checked('count',psspy.windmind(1,'1','WMECH','N'+kind));layout[kind]=index;layout['N'+kind]=count
        if kind in ('STATE','VAR'):
            fn=psspy.state_channel if kind=='STATE' else psspy.var_channel
            for i in range(count):checked('channel',fn([-1,index+i],'MECH_'+kind+str(i)))
    assert [layout['NCON'],layout['NSTATE'],layout['NVAR']]==[5,4,3]
    def constants():return [checked('constant',psspy.dsrval('CON',layout['CON']+i)) for i in range(5)]
    before=constants()
    for code,name in ((2,'P_PU'),(3,'Q_PU'),(7,'SPEED_DEV')):checked('machine',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2,3):checked('voltage',psspy.voltage_and_angle_channel([-1,-1,-1,bus],['V'+str(bus),'A'+str(bus)+'_DEG']))
    checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,settings['step_s']]+[df]*5));checked('start',psspy.strt_2([0,0],'native.out'));after=constants()
    assert before==after and all(abs(a-b)<1e-7 for a,b in zip(after,settings['constants'],strict=True))
    checked('prefault',psspy.run(0,settings['apply_s'],1000000,1,0));events=[]
    if settings['fault']:
        events.append(dict(action='fault',time_s=checked('clock',psspy.dsrval('TIME'))));checked('fault',psspy.dist_bus_fault(2,3,230.,[0.,settings['fault_ohms']]))
    checked('fault interval',psspy.run(0,settings['clear_s'],1000000,1,0))
    if settings['fault']:
        events.append(dict(action='clear',time_s=checked('clock',psspy.dsrval('TIME'))));checked('clear',psspy.dist_clear_fault(1))
    checked('postfault',psspy.run(0,settings['end_s'],1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int));assert len(keys)==16
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,time in enumerate(data['time']):writer.writerow([format(time,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    log=Path('native.log').read_text(errors='replace');quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','wtdta_native_probe.py','wttqa_native_probe.py','tgov1_limit_probe.py','native.log','native.out','native.csv']
    manifest=dict(status='complete' if quality else 'rejected-quality',model='WTDTA1',settings=settings,layout=layout,events=events,
                  entered_constants=before,initialized_constants=after,sample_count=len(data['time']),channel_count=len(keys),
                  library_sha256=sha(library),source_document_sha256=sha(document),engine_module=psse3607.__name__,files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(dict(profile=settings['name'],status=manifest['status'])));assert quality


if __name__=='__main__':main()
