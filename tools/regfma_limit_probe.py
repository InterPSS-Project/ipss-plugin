"""Repeatable grid-forming lower-integral and coupled power-limit SMIB probes."""
import csv
import json
import math
import os
from pathlib import Path
import shutil
import struct
import sys
from hplntd_fault_probe import checked, sha

PROFILES=('lower-shunt','pq-voltage-pi')

def configuration(profile):
    if profile not in PROFILES:raise ValueError(profile)
    p=[.01,.012,.014,1.5,1.5,.5,.9,-.9,.8,-.8,.02,.03,.01,.1,.1,20,.25,5.86]
    if profile=='lower-shunt':p[4:6]=[1.1,1.075]
    else:p[6:10]=[.405,.395,.3,.2]
    return dict(constants=p,icons=[1,1],shunt_ohm=[0.,-500. if profile=='lower-shunt' else 100.],
                bus=2,start_s=.05,clear_s=.1,end_s=.3)

def run(tag,step,profile):
    import psse3607
    import psspy
    import dyntools
    cfg=configuration(profile)
    assert step in (.0005,.00025)
    assert tag and all(c.isalnum() or c in '-_' for c in tag)
    root=Path.cwd();out=root/'target'/('regfma-limit-'+tag);out.mkdir(exist_ok=False)
    shutil.copy2(__file__,out/'probe.py')
    shutil.copy2(root/'tools/hplntd_fault_probe.py',out/'hplntd_fault_probe.py')
    shutil.copy2(root/'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regfma1_native.raw',out/'network.raw')
    (out/'dynamic.dyr').write_text("1 'REGFMA1' '1' 1 1 "+' '.join(map(str,cfg['constants']))+" /\n3 'GENCLS' '1' 9999999 0 /\n")
    os.chdir(out)
    checked('initialize',psspy.psseinit(50))
    for fn in (psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output):
        checked('log',fn(2,'native.log',[0,0]))
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL'
    document=library.parent.parent/'DOCS/ModelLibrary.pdf'
    checked('library',psspy.addmodellibrary(str(library)))
    checked('network',psspy.read(0,'network.raw'))
    checked('flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
    solved={key:checked(key,psspy.macdat(1,'1',key)) for key in ('P','Q','MBASE')}
    checked('convert generators',psspy.cong(0))
    for stage in (1,2,3):checked('convert loads',psspy.conl(0,1,stage,[0,0],[0.]*4))
    checked('order',psspy.ordr(0));checked('factor',psspy.fact());checked('network solution',psspy.tysl(0))
    checked('dynamic input',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''))
    allocation={key:checked(key,psspy.windmind(1,'1','WGEN',key)) for key in ('ICON','NICON','CON','NCON','STATE','NSTATE','VAR','NVAR')}
    assert [allocation['N'+k] for k in ('ICON','CON','STATE','VAR')]==[2,18,9,8]
    def constants():return [checked('constant',psspy.dsrval('CON',allocation['CON']+i)) for i in range(18)]
    def icons():return [checked('selector',psspy.dsival('ICON',allocation['ICON']+i)) for i in range(2)]
    expected=[struct.unpack('<f',struct.pack('<f',v))[0] for v in cfg['constants']]
    assert constants()==expected and icons()==cfg['icons']
    for kind,count in (('STATE',9),('VAR',8)):
        for i in range(count):checked('channel',getattr(psspy,kind.lower()+'_channel')([-1,allocation[kind]+i],kind+'_'+str(i)))
    for bus in (1,2,3):checked('voltage',psspy.voltage_channel([-1,-1,-1,bus],'V_BUS'+str(bus)))
    for code,name in ((2,'P'),(3,'Q'),(4,'ETERM'),(7,'SPEED')):checked('machine channel',psspy.machine_array_channel([-1,code,1],'1',name))
    di,df=psspy.getdefaultint(),psspy.getdefaultreal()
    checked('settings',psspy.dynamics_solution_param_2([200]+[di]*7,[df,df,step]+[df]*5))
    checked('start',psspy.strt_2([0,0],'native.out'))
    assert constants()==expected and icons()==cfg['icons']
    live=[];events=[]
    def capture(stage):
        live.append(dict(stage=stage,time_s=checked('clock',psspy.dsrval('TIME')),
            states=[checked('state',psspy.dsrval('STATE',allocation['STATE']+i)) for i in range(9)],
            variables=[checked('var',psspy.dsrval('VAR',allocation['VAR']+i)) for i in range(8)]))
    capture('initialized')
    for until in (cfg['start_s'],cfg['clear_s'],cfg['end_s']):
        checked('interval',psspy.run(0,until,1000000,1,0));capture('interval-end')
        if until==cfg['start_s']:
            checked('shunt on',psspy.dist_bus_fault(cfg['bus'],3,100,cfg['shunt_ohm']))
            events.append(dict(action='shunt-on',nominal_s=until,actual_s=checked('clock',psspy.dsrval('TIME'))))
            capture('post-shunt-on')
        elif until==cfg['clear_s']:
            checked('shunt off',psspy.dist_clear_fault(1))
            events.append(dict(action='shunt-off',nominal_s=until,actual_s=checked('clock',psspy.dsrval('TIME'))))
            capture('post-shunt-off')
    assert constants()==expected and icons()==cfg['icons']
    _,labels,channels=dyntools.CHNF('native.out').get_data();indices=sorted(i for i in labels if isinstance(i,int))
    assert len(indices)==24 and all(math.isfinite(v) for key in ['time']+indices for v in channels[key])
    with Path('native.csv').open('w',newline='') as f:
        writer=csv.writer(f,lineterminator='\n');writer.writerow(['time_s']+[labels[i] for i in indices])
        for row,t in enumerate(channels['time']):writer.writerow([format(t,'.12g')]+[format(channels[i][row],'.12g') for i in indices])
    Path('live-states.json').write_text(json.dumps(live,indent=2)+'\n')
    log=Path('native.log').read_text(errors='replace')
    quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=('network.raw','dynamic.dyr','probe.py','hplntd_fault_probe.py','native.log','native.out','native.csv','live-states.json')
    manifest=dict(status='native-converged-profile' if quality else 'rejected-native-quality',model='REGFMA1',profile=profile,
        configuration=cfg,actual_constants=expected,final_constants=constants(),actual_icons=icons(),allocation=allocation,
        solved=solved,step_s=step,events=events,sample_count=len(channels['time']),channel_count=24,
        library_sha256=sha(library),source_document_sha256=sha(document),python_sha256=sha(sys.executable),
        wrapper_sha256=sha(psspy.__file__),engine_module=psse3607.__name__,files={name:sha(name) for name in files},
        trajectory_acceptance=False)
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    assert quality
    return dict(status=manifest['status'],samples=manifest['sample_count'],trajectory_acceptance=False)

if __name__=='__main__':print(json.dumps(run(sys.argv[1],float(sys.argv[2]),sys.argv[3])))
