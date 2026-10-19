"""Algebraic aerodynamic reference with explicit external pitch-driver playback."""
import csv,json,math,os,shutil,sys
from pathlib import Path
from tgov1_limit_probe import checked,sha,STEPS
from wttqa_native_probe import configuration as torque_configuration,dynamic

MODES=('flat','fault','pitch','nonzero','gain','negative','nonunit','zero-gain')

def configuration(mode,grid,repeat):
    if mode not in MODES or grid not in STEPS or repeat not in ('a','b'):raise ValueError('Unknown aerodynamic profile')
    theta0=5. if mode in ('nonzero','nonunit') else 0.
    values=[theta0+2,theta0-2,theta0] if theta0 else [3.,1.,0.]
    if mode=='negative':values=[8.,9.,0.]
    return dict(name=mode+'-'+grid,mode=mode,repeat=repeat,step_s=STEPS[grid],
                constants=[0. if mode=='zero-gain' else .014 if mode=='gain' else .007,theta0],
                curve=mode=='nonunit',fault=mode=='fault',end_s=.5,
                pitch_events=[] if mode in ('flat','fault') else [dict(time_s=t,pitch_deg=v) for t,v in zip((.15,.25,.35),values,strict=True)])

def inputs(root,settings):
    text=dynamic(root,torque_configuration('curve' if settings['curve'] else 'speed','coarse','a'))
    original="1 'WTARA1' '1' .007 0 /";assert text.count(original)==1
    text=text.replace(original,"1 'WTARA1' '1' "+' '.join(map(str,settings['constants']))+' /')
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.raw').read_text()
    return raw,text

def main():
    settings=configuration(*sys.argv[1:])
    import psse3607
    import psspy,dyntools
    root=Path.cwd();out=root/'target'/('wtara-boundary-native-'+settings['name']+'-'+settings['repeat']);out.mkdir(exist_ok=False)
    for name in ['wtara_native_probe.py','wttqa_native_probe.py','tgov1_limit_probe.py']:shutil.copy2(root/'tools'/name,out/name)
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
    for category,prefix in [('WAERO','AERO'),('WPITCH','PITCH'),('WMECH','MECH')]:
        layout[prefix]={}
        for kind in ('CON','STATE','VAR'):
            result=psspy.windmind(1,'1',category,'N'+kind)
            # The API reports unused storage with code 6, not a zero-success count.
            count=0 if category=='WAERO' and kind=='STATE' and result==(6,0) else checked('count',result)
            layout[prefix]['N'+kind]=count
            if not count:continue
            index=checked('index',psspy.windmind(1,'1',category,kind));layout[prefix][kind]=index
            if kind in ('STATE','VAR'):
                fn=psspy.state_channel if kind=='STATE' else psspy.var_channel
                for i in range(count):checked('channel',fn([-1,index+i],prefix+'_'+kind+str(i)))
    aero=layout['AERO'];assert [aero['NCON'],aero['NSTATE'],aero['NVAR']]==[2,0,1]
    def constants():return [checked('constant',psspy.dsrval('CON',aero['CON']+i)) for i in range(2)]
    before=constants()
    for code,name in ((2,'P_PU'),(3,'Q_PU'),(7,'SPEED_DEV'),(6,'SHAFT_POWER'),(17,'TURBINE_SPEED_DEV'),(18,'PITCH_DEG'),(19,'AERO_TORQUE')):
        checked('machine',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2,3):checked('voltage',psspy.voltage_channel([-1,-1,-1,bus],'V'+str(bus)))
    checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,settings['step_s']]+[df]*5));checked('start',psspy.strt_2([0,0],'native.out'));after=constants()
    assert before==after and all(abs(a-b)<1e-7 for a,b in zip(after,settings['constants'],strict=True))
    events=[]
    checked('prefault',psspy.run(0,.05,1000000,1,0))
    if settings['fault']:checked('fault',psspy.dist_bus_fault(2,3,230.,[0.,529.]))
    checked('fault interval',psspy.run(0,.1,1000000,1,0))
    if settings['fault']:checked('clear',psspy.dist_clear_fault(1))
    for event in settings['pitch_events']:
        checked('advance to input change',psspy.run(0,event['time_s'],1000000,1,0))
        # Drive only the upstream zero-gain pitch fixture. WTARA1 has no states.
        # Reset driver derivatives/history explicitly, never aerodynamic/mechanical states.
        for offset in (0,1):checked('pitch input',psspy.change_state(layout['PITCH']['STATE']+offset,[event['pitch_deg'],0.,event['pitch_deg']]))
        events.append(dict(**event,actual_time_s=checked('clock',psspy.dsrval('TIME'))))
    checked('post-input',psspy.run(0,settings['end_s'],1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int));assert len(keys)==22
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,time in enumerate(data['time']):writer.writerow([format(time,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    log=Path('native.log').read_text(errors='replace');quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','wtara_native_probe.py','wttqa_native_probe.py','tgov1_limit_probe.py','native.log','native.out','native.csv']
    manifest=dict(status='complete' if quality else 'rejected-quality',model='WTARA1',settings=settings,layout=layout,events=events,
                  entered_constants=before,initialized_constants=after,sample_count=len(data['time']),channel_count=len(keys),
                  library_sha256=sha(library),source_document_sha256=sha(document),engine_module=psse3607.__name__,files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(dict(profile=settings['name'],status=manifest['status'])));assert quality

if __name__=='__main__':main()
