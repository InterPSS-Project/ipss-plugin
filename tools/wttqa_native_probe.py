"""Exact torque-controller selector profiles on the public three-bus wind testbed."""
import csv,json,math,os,shutil,sys
from pathlib import Path
from tgov1_limit_probe import checked,sha,STEPS


def configuration(mode,grid,repeat):
    if mode not in ('flat','speed','power','curve','dip') or grid not in STEPS or repeat not in ('a','b'):
        raise ValueError('Expected flat/speed/power/curve/dip, coarse/fine and a/b')
    constants=[2.,1.,.02,.1,2.,0.,0.,0.,0.,0.,0.,0.,0.,0.,100.]
    if mode=='curve':constants[6:14]=[.1,.8,.2,.9,.4,1.,.8,1.1]
    return dict(name=mode+'-'+grid,mode=mode,step_s=STEPS[grid],repeat=repeat,tflag=1 if mode=='power' else 0,
                constants=constants,apply_s=.05,clear_s=.10,end_s=.5,fault_ohms=100. if mode=='dip' else 529.)


def dynamic(root,settings):
    records=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/type3_wind_bus1062.dyr').read_text().splitlines()
    electrical=records[1].split();assert electrical[7]=='1';electrical[7]='0'
    return '\n'.join([records[0],' '.join(electrical),"1 'WTDTA1' '1' 5 .1 .8 1.5 .2 /",
        "1 'WTARA1' '1' .007 0 /","1 'WTPTA1' '1' 0 0 0 0 0 .3 27 0 10 -10 /",
        "1 'WTTQA1' '1' "+str(settings['tflag'])+' '+' '.join(map(str,settings['constants']))+' /',records[-1]])+'\n'


def main():
    settings=configuration(*sys.argv[1:])
    import psse3607
    import psspy,dyntools
    root=Path.cwd();out=root/settings.get('artifact_root','target')/('wttqa-native-'+settings['name']+'-'+settings['repeat']);out.mkdir(parents=True,exist_ok=False)
    shutil.copy2(__file__,out/'probe.py');shutil.copy2(root/'tools/tgov1_limit_probe.py',out/'tgov1_limit_probe.py')
    scripts=['probe.py','tgov1_limit_probe.py']+list(settings.get('extra_scripts',[]))
    for script in settings.get('extra_scripts',[]):shutil.copy2(root/'tools'/script,out/script)
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.raw').read_text()
    if 'initial_power_pu' in settings:
        lines=raw.splitlines()
        for i,line in enumerate(lines):
            if line.startswith(("1,'1',","3,'1',")):
                fields=line.split(',');fields[2]=str(100*settings['initial_power_pu']*(1 if fields[0]=='1' else -1));lines[i]=','.join(fields)
        raw='\n'.join(lines)+'\n'
    (out/'network.raw').write_text(raw)
    (out/'dynamic.dyr').write_text(dynamic(root,settings));os.chdir(out)
    checked('initialize',psspy.psseinit(50))
    for fn in (psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output):checked('log',fn(2,'native.log',[0,0]))
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL';document=library.parent.parent/'DOCS/ModelLibrary.pdf'
    checked('library',psspy.addmodellibrary(str(library)));checked('network',psspy.read(0,'network.raw'))
    di,df=psspy.getdefaultint(),psspy.getdefaultreal();flags=[di]*7;flags[5]=1
    checked('wind mode',psspy.machine_chng_4(1,'1',flags,[df]*17,''));checked('flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
    checked('generators',psspy.cong(0))
    for stage in (1,2,3):checked('loads',psspy.conl(0,1,stage,[0,0],[0.]*4))
    checked('order',psspy.ordr(0));checked('factor',psspy.fact());checked('solution',psspy.tysl(0))
    checked('dynamic',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''));layout={}
    for category,prefix in [('WTRQ','TRQ'),('WPITCH','PITCH'),('WMECH','MECH')]:
        layout[prefix]={}
        for kind in ('CON','STATE','VAR'):
            index=checked('index',psspy.windmind(1,'1',category,kind));count=checked('count',psspy.windmind(1,'1',category,'N'+kind))
            layout[prefix][kind]=index;layout[prefix]['N'+kind]=count
            if kind in ('STATE','VAR'):
                fn=psspy.state_channel if kind=='STATE' else psspy.var_channel
                for i in range(count):checked('channel',fn([-1,index+i],prefix+'_'+kind+str(i)))
    torque=layout['TRQ'];assert [torque['NCON'],torque['NSTATE'],torque['NVAR']]==[15,3,3]
    torque['ICON']=checked('icon',psspy.windmind(1,'1','WTRQ','ICON'));assert checked('icon count',psspy.windmind(1,'1','WTRQ','NICON'))==1
    before=[checked('constant',psspy.dsrval('CON',torque['CON']+i)) for i in range(15)]
    for code,name in ((2,'P_PU'),(3,'Q_PU'),(7,'SPEED_DEV')):checked('machine',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2,3):checked('voltage',psspy.voltage_channel([-1,-1,-1,bus],'V'+str(bus)))
    checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,settings['step_s']]+[df]*5));checked('start',psspy.strt_2([0,0],'native.out'))
    after=[checked('initialized constant',psspy.dsrval('CON',torque['CON']+i)) for i in range(15)]
    selector=checked('selector',psspy.dsival('ICON',torque['ICON']));assert selector==settings['tflag']
    assert before==after and all(abs(a-b)<1e-7 for a,b in zip(after,settings['constants'],strict=True))
    checked('prefault',psspy.run(0,settings['apply_s'],1000000,1,0));events=[]
    if settings['mode']!='flat':
        events.append(dict(action='fault',actual_time_s=checked('clock',psspy.dsrval('TIME'))));checked('fault',psspy.dist_bus_fault(2,3,230.,[0.,settings['fault_ohms']]))
    checked('fault interval',psspy.run(0,settings['clear_s'],1000000,1,0))
    if settings['mode']!='flat':
        events.append(dict(action='clear',actual_time_s=checked('clock',psspy.dsrval('TIME'))));checked('clear',psspy.dist_clear_fault(1))
    checked('postfault',psspy.run(0,settings['end_s'],1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int))
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,time in enumerate(data['time']):writer.writerow([format(time,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    log=Path('native.log').read_text(errors='replace');quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','native.log','native.out','native.csv']+scripts
    manifest=dict(status='complete' if quality else 'rejected-quality',model='WTTQA1',settings=settings,layout=layout,
                  entered_constants=before,initialized_constants=after,initialized_selector=selector,events=events,
                  sample_count=len(data['time']),channel_count=len(keys),engine_module=psse3607.__name__,
                  library_sha256=sha(library),source_document_sha256=sha(document),files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(dict(profile=settings['name'],status=manifest['status'])));assert quality


if __name__=='__main__':main()
