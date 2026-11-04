"""Exact electrical-controller selector, dip/hold and current-table SMIB references."""
import csv,json,math,os,shutil,struct,sys
from pathlib import Path
from tgov1_limit_probe import checked,sha,STEPS

MODES=('discharge-limit','charge-limit','voltage-flat','pf-voltage-flat','remote-flat','remote-fault','plant-flat','plant-fault','plant-qbound-fault')

def configuration(mode,grid,repeat):
    if mode not in MODES or grid not in STEPS or repeat not in ('a','b'):raise ValueError('Unknown profile')
    record=Path('ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regca_reecc1_bus1062.dyr').read_text().splitlines()[1].split()[3:-1]
    icons=list(map(int,record[:5]));constants=list(map(float,record[5:]))
    if mode in ('voltage-flat','pf-voltage-flat') or mode.startswith('remote-'): icons[1:4]=[1 if mode.startswith('pf-') else 0,0,1]
    if mode.startswith('remote-'): icons[0]=2
    if mode in ('charge-limit','discharge-limit'): constants[41:45]=[.2,.55,.6,.5]
    result=dict(name=mode+'-'+grid,mode=mode,repeat=repeat,step_s=STEPS[grid],icons=icons,constants=constants,
                converter=[1,.02,10.,.9,.5,1.22,1.2,.8,.4,-1.3,.02,.7,100.,-100.,.8],
                fault=mode.endswith('-fault'),apply_s=.05,clear_s=.10,end_s=.3,fault_ohms=100.)
    if mode.startswith('plant-'):
        from repca_native_probe import configuration as plant_configuration
        plant=plant_configuration('q',grid,repeat)
        result['plant']={'icons':plant['icons'],'constants':plant['constants']}
        if mode=='plant-qbound-fault':result['plant']['constants'][14]=.04
        icons[1]=0
    return result

def inputs(root,settings):
    base=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.dyr').read_text().splitlines()
    record="1 'REECC1' '1' "+' '.join(map(str,settings['icons']+settings['constants']))+' /'
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.raw').read_text()
    converter="1 'REGCA1' '1' "+' '.join(map(str,settings['converter']))+' /'
    if settings['mode']=='charge-limit': raw=raw.replace("1,'1',24.000","1,'1',-24.000")
    records=[converter,record]
    if 'plant' in settings:
        selectors=list(settings['plant']['icons']);selectors[3]="'"+str(selectors[3])+"'"
        records.append("1 'REPCA1' '1' "+' '.join(map(str,selectors+settings['plant']['constants']))+' /')
    records.append(base[-1])
    return raw,'\n'.join(records)+'\n'

def main():
    settings=configuration(*sys.argv[1:])
    import psse3607
    import psspy,dyntools
    root=Path.cwd();prefix='reeca-remote-diagnostic-' if settings['mode']=='remote' else 'reecc-limit-native-';out=root/'target'/(prefix+settings['name']+'-'+settings['repeat']);out.mkdir(exist_ok=False)
    scripts=['reecc_native_probe.py','tgov1_limit_probe.py']+(['repca_native_probe.py'] if 'plant' in settings else [])
    for name in scripts:shutil.copy2(root/'tools'/name,out/name)
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
    for category,prefix in [('WELEC','ELEC'),('WGEN','CONVERTER')]+([('WAUX','PLANT')] if 'plant' in settings else []):
        layout[prefix]={}
        for kind in ('CON','ICON','STATE','VAR'):
            count=checked('count',psspy.windmind(1,'1',category,'N'+kind));layout[prefix]['N'+kind]=count
            if not count:continue
            index=checked('index',psspy.windmind(1,'1',category,kind));layout[prefix][kind]=index
            if kind in ('STATE','VAR'):
                fn=psspy.state_channel if kind=='STATE' else psspy.var_channel
                for i in range(count):checked('channel',fn([-1,index+i],prefix+'_'+kind+str(i)))
    e=layout['ELEC'];assert [e['NICON'],e['NCON'],e['NSTATE'],e['NVAR']]==[5,45,7,6]
    def constants():return [checked('constant',psspy.dsrval('CON',e['CON']+i)) for i in range(45)]
    def selectors():return [checked('selector',psspy.dsival('ICON',e['ICON']+i)) for i in range(5)]
    before=constants();flags_before=selectors()
    def plant_readback():
        if 'plant' not in settings:return None
        index=layout['PLANT'];assert [index['NICON'],index['NCON'],index['NSTATE'],index['NVAR']]==[7,27,7,9]
        return {'constants':[checked('plant constant',psspy.dsrval('CON',index['CON']+i)) for i in range(27)],
                'icons':[checked('plant circuit',psspy.dscval('ICON',index['ICON']+i)).strip() if i==3 else checked('plant selector',psspy.dsival('ICON',index['ICON']+i)) for i in range(7)]}
    plant_before=plant_readback()
    g=layout['CONVERTER'];converter_before=[checked('converter constant',psspy.dsrval('CON',g['CON']+i)) for i in range(14)]
    for code,name in ((2,'P_PU'),(3,'Q_PU'),(7,'SPEED_DEV'),(22,'PREF_INPUT'),(23,'QEXT_INPUT')):
        checked('machine',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2,3):checked('voltage',psspy.voltage_and_angle_channel([-1,-1,-1,bus],['V'+str(bus),'A'+str(bus)+'_DEG']))
    for bus in (1,2,3):checked('frequency',psspy.bus_frequency_channel([-1,bus],'F'+str(bus)+'_DEV'))
    checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,settings['step_s']]+[df]*5));checked('start',psspy.strt_2([0,0],'native.out'))
    plant_after=plant_readback()
    assert plant_before==plant_after
    if plant_after is not None:
        assert plant_after['constants']==[struct.unpack('f',struct.pack('f',v))[0] for v in settings['plant']['constants']]
        assert plant_after['icons']==settings['plant']['icons']
    after=constants();flags_after=selectors();expected=[struct.unpack('f',struct.pack('f',v))[0] for v in settings['constants']]
    converter_after=[checked('converter constant',psspy.dsrval('CON',g['CON']+i)) for i in range(14)]
    Path('readback.json').write_text(json.dumps(dict(entered=before,initialized=after,declared_binary32=expected,entered_icons=flags_before,initialized_icons=flags_after),indent=2)+'\n')
    assert before==after==expected and flags_before==settings['icons'] and flags_after[1:]==flags_before[1:]
    selectors_exact=flags_after==flags_before
    if not settings['mode'].startswith('remote-'):assert selectors_exact
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
    files=['network.raw','dynamic.dyr','readback.json','native.log','native.out','native.csv']+scripts
    status='rejected-quality' if not quality else 'complete' if selectors_exact else 'remote-selector-normalization-diagnostic'
    manifest=dict(status=status,model='REECC1',settings=settings,layout=layout,events=events,selector_readback_unchanged=selectors_exact,
                  entered_constants=before,initialized_constants=after,entered_icons=flags_before,initialized_icons=flags_after,
                  entered_converter_constants=converter_before,initialized_converter_constants=converter_after,plant_entered=plant_before,plant_initialized=plant_after,
                  sample_count=len(data['time']),channel_count=len(keys),library_sha256=sha(library),source_document_sha256=sha(document),engine_module=psse3607.__name__,files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(dict(profile=settings['name'],status=manifest['status'])));assert quality and selectors_exact

if __name__=='__main__':main()
