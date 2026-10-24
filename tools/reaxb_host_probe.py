"""Native auxiliary-control host/interface references on a small converter case."""
import csv,json,math,os,shutil,struct,sys
from pathlib import Path
from tgov1_limit_probe import checked,sha,STEPS


def configuration(mode,grid,repeat,mva=100,iterations=None):
    if mode not in ('legacy3','legacy4','standard') or grid not in STEPS or repeat not in ('a','b'):
        raise ValueError('Unknown auxiliary host profile')
    mva=float(mva)
    if mva not in (50,100,200):raise ValueError('Unreviewed converter rating')
    iterations=None if iterations is None else int(iterations)
    if iterations is not None and iterations!=200:raise ValueError('Unreviewed iteration limit')
    return dict(name=(('mva'+str(int(mva))+'-') if mva!=100 else '')+mode+'-'+grid+('-iter200' if iterations else ''),mode=mode,step_s=STEPS[grid],repeat=repeat,machine_mva=mva,network_iterations=iterations,
        plant_icons=[2,1,2,'1',0,1,1],
        plant_constants=[.066667,0,25,0,.02,.7,0,0,-.01,.001,-.001,0,0,1,-1,.01,.06,.25,0,0,999,-999,999,-999,.1,50,0,0],
        auxiliary_constants=[.066667,1,1,9999,-9999,9999,-9999])


def inputs(root,settings):
    from reeca_native_probe import configuration as electrical,inputs as electrical_inputs
    raw,dyr=electrical_inputs(root,electrical('q','coarse','a'))
    if settings['machine_mva']!=100:
        lines=raw.splitlines()
        for i,line in enumerate(lines):
            if line.startswith("1,'1',"):
                fields=line.split(',');fields[8]=str(settings['machine_mva']);lines[i]=','.join(fields)
        raw='\n'.join(lines)+'\n'
    icons=list(settings['plant_icons']);icons[3]="'"+icons[3]+"'"
    plant=' '.join(map(str,icons+settings['plant_constants']))
    auxiliary='2 '+' '.join(map(str,settings['auxiliary_constants']))
    if settings['mode']=='standard':
        dyr+="2 'PLNTB1' "+plant+" /\n1 'REAXB1' '1' "+auxiliary+' /\n'
    else:
        model='REAX3BU1' if settings['mode']=='legacy3' else 'REAX4BU1'
        dyr+="2 'USRBUS' 'PLNTBU1' 504 0 7 28 7 15 "+plant+" /\n"
        dyr+="1 'USRMDL' '1' '"+model+"' 107 0 1 7 2 4 "+auxiliary+' /\n'
    return raw,dyr


def main():
    settings=configuration(*sys.argv[1:])
    import psse3607,psspy,dyntools
    root=Path.cwd();out=root/'ipss.test.plugin.core/testData/private/reaxb-host'/(settings['name']+'-'+settings['repeat'])
    out.mkdir(parents=True,exist_ok=False)
    scripts=['reaxb_host_probe.py','reeca_native_probe.py','tgov1_limit_probe.py']
    for script in scripts:shutil.copy2(root/'tools'/script,out/script)
    raw,dyr=inputs(root,settings);(out/'network.raw').write_text(raw);(out/'dynamic.dyr').write_text(dyr);os.chdir(out)
    checked('initialize',psspy.psseinit(50))
    for fn in (psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output):checked('log',fn(2,'native.log',[0,0]))
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL'
    checked('library',psspy.addmodellibrary(str(library)));checked('network',psspy.read(0,'network.raw'))
    di,df=psspy.getdefaultint(),psspy.getdefaultreal();flags=[di]*7;flags[5]=1
    checked('wind mode',psspy.machine_chng_4(1,'1',flags,[df]*17,''))
    checked('flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
    checked('generators',psspy.cong(0))
    for stage in (1,2,3):checked('loads',psspy.conl(0,1,stage,[0,0],[0.]*4))
    checked('order',psspy.ordr(0));checked('factor',psspy.fact());checked('solution',psspy.tysl(0))
    checked('dynamic',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''));layout={}
    plant_name=None
    for candidate in ('PLNTBU1','PLNTB1'):
        result=psspy.cctmind_buso(2,candidate,'CON')
        if result[0]==0:plant_name=candidate;break
    if plant_name is None:raise RuntimeError('Native plant did not allocate; inspect the retained log')
    for prefix,query in [('PLANT',lambda a:psspy.cctmind_buso(2,plant_name,a)),('AUX',lambda a:psspy.windmind(1,'1','WAUX',a)),('ELEC',lambda a:psspy.windmind(1,'1','WELEC',a))]:
        layout[prefix]={}
        for kind in ('ICON','CON','STATE','VAR'):
            count=checked('count',query('N'+kind));layout[prefix]['N'+kind]=count
            if count:
                index=checked('index',query(kind));layout[prefix][kind]=index
                if kind in ('STATE','VAR'):
                    fn=psspy.state_channel if kind=='STATE' else psspy.var_channel
                    for i in range(count):checked('channel',fn([-1,index+i],prefix+'_'+kind+str(i)))
    def read_constants():return {p:[checked('constant',psspy.dsrval('CON',a['CON']+i)) for i in range(a['NCON'])] for p,a in layout.items()}
    before=read_constants()
    for code,name in ((2,'P_PU'),(3,'Q_PU'),(7,'SPEED_DEV')):checked('machine',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2,3):checked('voltage',psspy.voltage_channel([-1,-1,-1,bus],'V'+str(bus)))
    checked('step',psspy.dynamics_solution_param_2([settings['network_iterations'] or di]+[di]*7,[df,df,settings['step_s']]+[df]*5))
    checked('start',psspy.strt_2([0,0],'native.out'));after=read_constants();assert before==after
    events=[];checked('prefault',psspy.run(0,.05,1000000,1,0))
    events.append(dict(action='fault',actual_time_s=checked('clock',psspy.dsrval('TIME'))))
    checked('fault',psspy.dist_bus_fault(2,3,230.,[0.,100.]))
    checked('fault interval',psspy.run(0,.1,1000000,1,0));events.append(dict(action='clear',actual_time_s=checked('clock',psspy.dsrval('TIME'))))
    checked('clear',psspy.dist_clear_fault(1));checked('postfault',psspy.run(0,.5,1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int))
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,time in enumerate(data['time']):writer.writerow([format(time,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    log=Path('native.log').read_text(errors='replace');quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','native.log','native.out','native.csv']+scripts
    manifest=dict(status='complete' if quality else 'rejected-quality',settings=settings,layout=layout,plant_query_name=plant_name,
        entered_constants=before,initialized_constants=after,events=events,sample_count=len(data['time']),channel_count=len(keys),
        engine_module=psse3607.__name__,library_sha256=sha(library),source_document_sha256=sha(library.parent.parent/'DOCS/ModelLibrary.pdf'),files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(dict(profile=settings['name'],status=manifest['status'],layout=layout)));assert quality


if __name__=='__main__':main()
