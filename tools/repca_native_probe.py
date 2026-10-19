"""Native plant measurement, compensation, freeze and active-limit SMIB references."""
import csv,json,math,os,shutil,struct,sys
from pathlib import Path
from tgov1_limit_probe import checked,sha,STEPS

MODES=('flat-q','q','flat-v','v','branch-q','remote-v','ldc-zero','ldc','droop','reverse-q','negative-from','freeze','q-limit','p-limit','frequency-off')

def configuration(mode,grid,repeat):
    if mode not in MODES or grid not in STEPS or repeat not in ('a','b'):raise ValueError('Unknown plant-control profile')
    icons=[0,0,0,0,0,0,1]
    constants=[.02,.5,1.,.03,.1,.7,0.,0.,0.,.2,-.2,0.,0.,1.,-1.,.5,1.,.02,-.0006,.0006,.2,-.2,1.,0.,.1,20.,20.]
    if mode in ('flat-v','v','remote-v','ldc-zero','ldc','droop','freeze'):icons[5]=1
    if mode in ('branch-q','ldc-zero','ldc','droop','remote-v'):icons[1:4]=[1,2,1]
    if mode in ('remote-v','ldc-zero','ldc','droop'):icons[0]=2
    if mode in ('ldc-zero','ldc'):icons[4]=1
    if mode=='ldc':constants[6:8]=[.02,.08]
    if mode=='droop':constants[8]=.4
    if mode=='reverse-q':icons[1:4]=[2,1,1]
    if mode=='negative-from':icons[1:4]=[-1,2,1]
    if mode=='freeze':constants[5]=.9
    if mode=='q-limit':constants[13:15]=[.05,-.05]
    if mode=='p-limit':constants[22:24]=[.245,.235]
    if mode=='frequency-off':icons[6]=0
    icons[3]=str(icons[3])
    return dict(name=mode+'-'+grid,mode=mode,repeat=repeat,step_s=STEPS[grid],icons=icons,constants=constants,
                converter=[1,.02,10.,.9,.5,1.22,1.2,.8,.4,-1.3,.02,.7,100.,-100.,.8],
                fault=mode not in ('flat-q','flat-v'),apply_s=.05,clear_s=.10,end_s=.5,fault_ohms=100.)

def inputs(root,settings):
    from reeca_native_probe import configuration as electrical,inputs as electrical_inputs
    raw,dyr=electrical_inputs(root,electrical('q','coarse','a'))
    selectors=list(settings['icons']);selectors[3]="'"+str(selectors[3])+"'"
    record="1 'REPCA1' '1' "+' '.join(map(str,selectors+settings['constants']))+' /'
    return raw,dyr+record+'\n'

def main():
    settings=configuration(*sys.argv[1:])
    import psse3607
    import psspy,dyntools
    root=Path.cwd();prefix='repca-boundary-native-';out=root/'target'/(prefix+settings['name']+'-'+settings['repeat']);out.mkdir(exist_ok=False)
    for name in ['repca_native_probe.py','reeca_native_probe.py','tgov1_limit_probe.py']:shutil.copy2(root/'tools'/name,out/name)
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
    for category,prefix in [('WAUX','PLANT'),('WELEC','ELEC'),('WGEN','CONVERTER')]:
        layout[prefix]={}
        for kind in ('CON','ICON','STATE','VAR'):
            count=checked('count',psspy.windmind(1,'1',category,'N'+kind));layout[prefix]['N'+kind]=count
            if not count:continue
            index=checked('index',psspy.windmind(1,'1',category,kind));layout[prefix][kind]=index
            if kind in ('STATE','VAR'):
                fn=psspy.state_channel if kind=='STATE' else psspy.var_channel
                for i in range(count):checked('channel',fn([-1,index+i],prefix+'_'+kind+str(i)))
    e=layout['PLANT'];assert [e['NICON'],e['NCON'],e['NSTATE'],e['NVAR']]==[7,27,7,9]
    def constants():return [checked('constant',psspy.dsrval('CON',e['CON']+i)) for i in range(27)]
    def selectors():return [checked('circuit selector',psspy.dscval('ICON',e['ICON']+i)).strip() if i==3 else checked('selector',psspy.dsival('ICON',e['ICON']+i)) for i in range(7)]
    before=constants();flags_before=selectors()
    g=layout['CONVERTER'];converter_before=[checked('converter constant',psspy.dsrval('CON',g['CON']+i)) for i in range(14)]
    for code,name in ((2,'P_PU'),(3,'Q_PU'),(7,'SPEED_DEV'),(22,'PREF_INPUT'),(23,'QEXT_INPUT')):
        checked('machine',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2,3):checked('voltage',psspy.voltage_and_angle_channel([-1,-1,-1,bus],['V'+str(bus),'A'+str(bus)+'_DEG']))
    for bus in (1,2,3):checked('frequency',psspy.bus_frequency_channel([-1,bus],'F'+str(bus)+'_DEV'))
    for a,b in [(1,2),(2,1)]:checked('branch flow',psspy.branch_p_and_q_channel([-1,-1,-1,a,b],'1',['P'+str(a)+str(b)+'_MW','Q'+str(a)+str(b)+'_MVAR']))
    checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,settings['step_s']]+[df]*5));checked('start',psspy.strt_2([0,0],'native.out'))
    after=constants();flags_after=selectors();expected=[struct.unpack('f',struct.pack('f',v))[0] for v in settings['constants']]
    converter_after=[checked('converter constant',psspy.dsrval('CON',g['CON']+i)) for i in range(14)]
    Path('readback.json').write_text(json.dumps(dict(entered=before,initialized=after,declared_binary32=expected,entered_icons=flags_before,initialized_icons=flags_after),indent=2)+'\n')
    assert before==after==expected and flags_before==settings['icons'] and flags_after[4:]==flags_before[4:]
    selectors_exact=flags_after==flags_before
    assert selectors_exact
    assert converter_before==converter_after==[struct.unpack('f',struct.pack('f',v))[0] for v in settings['converter'][1:]]
    checked('prefault',psspy.run(0,settings['apply_s'],1000000,1,0));events=[]
    if settings['fault']:
        events.append(dict(action='fault',actual_time_s=checked('clock',psspy.dsrval('TIME'))));checked('fault',psspy.dist_bus_fault(2,3,230.,[0.,settings['fault_ohms']]))
    checked('fault interval',psspy.run(0,settings['clear_s'],1000000,1,0))
    if settings['fault']:
        events.append(dict(action='clear',actual_time_s=checked('clock',psspy.dsrval('TIME'))));checked('clear',psspy.dist_clear_fault(1))
    checked('postfault',psspy.run(0,settings['end_s'],1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int))
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,time in enumerate(data['time']):writer.writerow([format(time,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    log=Path('native.log').read_text(errors='replace');quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','repca_native_probe.py','reeca_native_probe.py','tgov1_limit_probe.py','readback.json','native.log','native.out','native.csv']
    status='rejected-quality' if not quality else 'complete' if selectors_exact else 'remote-selector-normalization-diagnostic'
    manifest=dict(status=status,model='REPCA1',settings=settings,layout=layout,events=events,selector_readback_unchanged=selectors_exact,
                  entered_constants=before,initialized_constants=after,entered_icons=flags_before,initialized_icons=flags_after,
                  entered_converter_constants=converter_before,initialized_converter_constants=converter_after,
                  sample_count=len(data['time']),channel_count=len(keys),library_sha256=sha(library),source_document_sha256=sha(document),engine_module=psse3607.__name__,files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(dict(profile=settings['name'],status=manifest['status'])));assert quality and selectors_exact

if __name__=='__main__':main()
