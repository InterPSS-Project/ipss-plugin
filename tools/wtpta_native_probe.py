"""Native pitch-controller states and all physical reference inputs on the public SMIB."""
import csv,json,math,os,shutil,struct,sys
from pathlib import Path
from tgov1_limit_probe import checked,sha,STEPS
from wttqa_native_probe import configuration as torque_configuration,dynamic

MODES=('flat','fault','order-upper','order-lower','upper','lower','rate','moving','zero','zero-rate')

def configuration(mode,grid,repeat):
    if mode not in MODES or grid not in STEPS or repeat not in ('a','b'):raise ValueError('Unknown pitch profile')
    constants=[29.,177.,23.72,2.93,.25,.3,27.,0.,10.,-10.]
    theta0=0. if mode=='lower' else 2.
    if mode in ('upper','order-upper'):constants[6]=2.005
    if mode in ('rate','zero-rate'):constants[8:10]=[.01,-.01]
    if mode in ('zero','zero-rate'):constants[5]=0.
    return dict(name=mode+'-'+grid,mode=mode,repeat=repeat,step_s=STEPS[grid],constants=constants,theta0=theta0,
                curve=mode=='moving',fault=mode not in ('flat','order-upper','order-lower'),end_s=.5,
                order_events=[dict(time_s=.05,value=.34 if mode=='order-upper' else .14),dict(time_s=.1,value=.24)] if mode.startswith('order-') else [])

def inputs(root,settings):
    text=dynamic(root,torque_configuration('curve' if settings['curve'] else 'speed','coarse','a'))
    original="1 'WTPTA1' '1' 0 0 0 0 0 .3 27 0 10 -10 /";assert text.count(original)==1
    text=text.replace(original,"1 'WTPTA1' '1' "+' '.join(map(str,settings['constants']))+' /')
    text=text.replace("1 'WTARA1' '1' .007 0 /","1 'WTARA1' '1' .007 "+str(settings['theta0'])+' /')
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.raw').read_text()
    return raw,text

def main():
    settings=configuration(*sys.argv[1:])
    import psse3607
    import psspy,dyntools
    root=Path.cwd();out=root/'target'/('wtpta-order-native-'+settings['name']+'-'+settings['repeat']);out.mkdir(exist_ok=False)
    for name in ['wtpta_native_probe.py','wttqa_native_probe.py','tgov1_limit_probe.py']:shutil.copy2(root/'tools'/name,out/name)
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
    for category,prefix in [('WAERO','AERO'),('WPITCH','PITCH'),('WMECH','MECH'),('WTRQ','TRQ'),('WELEC','ELEC')]:
        layout[prefix]={}
        for kind in ('CON','STATE','VAR'):
            result=psspy.windmind(1,'1',category,'N'+kind)
            # The API reports unused storage with code 6, not a zero-success count.
            count=0 if category=='WAERO' and kind=='STATE' and result==(6,0) else checked('count',result)
            layout[prefix]['N'+kind]=count
            if not count:continue
            index=checked('index',psspy.windmind(1,'1',category,kind));layout[prefix][kind]=index
            if kind in ('STATE','VAR') and prefix not in ('TRQ','ELEC'):
                fn=psspy.state_channel if kind=='STATE' else psspy.var_channel
                for i in range(count):checked('channel',fn([-1,index+i],prefix+'_'+kind+str(i)))
    pitch=layout['PITCH'];assert [pitch['NCON'],pitch['NSTATE'],pitch['NVAR']]==[10,3,1]
    checked('PORD',psspy.state_channel([-1,layout['ELEC']['STATE']+5],'PORD'))
    checked('WREF',psspy.state_channel([-1,layout['TRQ']['STATE']+1],'WREF'))
    def constants():return [checked('constant',psspy.dsrval('CON',pitch['CON']+i)) for i in range(10)]
    before=constants()
    for code,name in ((2,'P_PU'),(3,'Q_PU'),(7,'SPEED_DEV'),(6,'SHAFT_POWER'),(17,'TURBINE_SPEED_DEV'),(18,'PITCH_DEG'),(19,'AERO_TORQUE')):
        checked('machine',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2,3):checked('voltage',psspy.voltage_channel([-1,-1,-1,bus],'V'+str(bus)))
    checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,settings['step_s']]+[df]*5));checked('start',psspy.strt_2([0,0],'native.out'));after=constants()
    expected=[struct.unpack('f',struct.pack('f',v))[0] for v in settings['constants']]
    Path('readback.json').write_text(json.dumps(dict(entered=before,initialized=after,declared_binary32=expected),indent=2)+'\n')
    assert before==after==expected
    events=[]
    checked('prefault',psspy.run(0,.05,1000000,1,0))
    if settings['fault']:checked('fault',psspy.dist_bus_fault(2,3,230.,[0.,529.]))
    if settings['order_events']:
        value=settings['order_events'][0]['value'];checked('upstream order driver',psspy.change_state(layout['ELEC']['STATE']+5,[value,0.,value]))
        events.append(dict(action='order',value=value,actual_time_s=checked('clock',psspy.dsrval('TIME'))))
    checked('fault interval',psspy.run(0,.1,1000000,1,0))
    if settings['fault']:checked('clear',psspy.dist_clear_fault(1))
    if settings['order_events']:
        checked('upstream order restore',psspy.change_state(layout['ELEC']['STATE']+5,[.24,0.,.24]));events.append(dict(action='order',value=.24,actual_time_s=checked('clock',psspy.dsrval('TIME'))))
    checked('post-input',psspy.run(0,settings['end_s'],1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int));assert len(keys)==24
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,time in enumerate(data['time']):writer.writerow([format(time,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    log=Path('native.log').read_text(errors='replace');quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','wtpta_native_probe.py','wttqa_native_probe.py','tgov1_limit_probe.py','readback.json','native.log','native.out','native.csv']
    manifest=dict(status='complete' if quality else 'rejected-quality',model='WTPTA1',settings=settings,layout=layout,events=events,
                  entered_constants=before,initialized_constants=after,sample_count=len(data['time']),channel_count=len(keys),
                  library_sha256=sha(library),source_document_sha256=sha(document),engine_module=psse3607.__name__,files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(dict(profile=settings['name'],status=manifest['status'])));assert quality

if __name__=='__main__':main()
