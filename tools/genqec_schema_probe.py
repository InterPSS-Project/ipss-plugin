"""Exact standard/wrapped generator schema and saturation-selector references."""
import csv
import json
import math
import os
from pathlib import Path
import shutil
import sys
from hplntd_fault_probe import checked, sha

PROFILES=('standard-0','standard-1','standard-2','standard-negative','wrapper-1')


def configuration(profile):
    if profile not in PROFILES: raise ValueError('Unknown schema profile')
    selector={'standard-0':0,'standard-1':1,'standard-2':2,'standard-negative':-1,'wrapper-1':1}[profile]
    constants=[6.4,.032,.68,.043,4.4,.07,2.05,1.74,.34,.49,.089,.12,.05,.05,.24,.035]
    head="1 'USRMDL' '1' 'GENQECU' 1 1 1 16 6 1 " if profile.startswith('wrapper') else "1 'GENQEC' '1' "
    dynamic=head+str(selector)+' '+' '.join(map(str,constants))+" /\n2 'GENCLS' '1' 99999 0 /\n"
    return dynamic,selector,constants


def main():
    import psse3607
    import psspy
    import dyntools
    root=Path.cwd();tag,entered_step,profile=sys.argv[1:];step=float(entered_step)
    assert step in (.0005,.00025) and tag and all(c.isalnum() or c in '-_' for c in tag)
    dynamic,selector,constants=configuration(profile)
    out=root/'target'/('genqec-schema-'+tag);out.mkdir(exist_ok=False)
    shutil.copy2(__file__,out/'probe.py');shutil.copy2(root/'tools/hplntd_fault_probe.py',out/'hplntd_fault_probe.py')
    shutil.copy2(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw',out/'network.raw')
    (out/'dynamic.dyr').write_text(dynamic);os.chdir(out)
    checked('initialize',psspy.psseinit(50))
    for fn in (psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output):checked('log',fn(2,'native.log',[0,0]))
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL';document=library.parent.parent/'DOCS/ModelLibrary.pdf'
    checked('library',psspy.addmodellibrary(str(library)));checked('network',psspy.read(0,'network.raw'))
    checked('flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
    checked('convert generators',psspy.cong(0))
    for stage in (1,2,3):checked('convert loads',psspy.conl(0,1,stage,[0,0],[0.]*4))
    checked('order',psspy.ordr(0));checked('factor',psspy.fact());checked('network solution',psspy.tysl(0))
    checked('dynamic input',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''))
    layout={}
    for kind in ('CON','ICON','STATE','VAR'):
        start=checked('index '+kind,psspy.mdlind(1,'1','GEN',kind));count=checked('count '+kind,psspy.mdlind(1,'1','GEN','N'+kind))
        layout[kind]=start;layout['N'+kind]=count
    assert tuple(layout['N'+k] for k in ('CON','ICON','STATE','VAR'))==(16,1,6,1),layout
    actual=[checked('constant',psspy.dsrval('CON',layout['CON']+i)) for i in range(16)]
    actual_selector=checked('selector',psspy.dsival('ICON',layout['ICON']))
    for i,name in enumerate(('EP_Q','EP_D','PSIP_D','PSIP_Q','SPEED_DEV','ANGLE')):checked('state channel',psspy.state_channel([-1,layout['STATE']+i],name))
    checked('internal variable',psspy.var_channel([-1,layout['VAR']],'INTERNAL_VAR'))
    for bus in (1,2):checked('voltage channel',psspy.voltage_channel([-1,-1,-1,bus],'V_BUS'+str(bus)))
    for code,name in ((1,'MACH_ANGLE'),(2,'P_PU'),(3,'Q_PU'),(5,'EFD'),(7,'MACH_SPEED')):checked('machine channel',psspy.machine_array_channel([-1,code,1],'1',name))
    for code,name in ((1,'REF_ANGLE'),(7,'REF_SPEED')):checked('reference channel',psspy.machine_array_channel([-1,code,2],'1',name))
    di,df=psspy.getdefaultint(),psspy.getdefaultreal();checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,step]+[df]*5))
    checked('start',psspy.strt_2([0,0],'native.out'));checked('prefault',psspy.run(0,.05,1000000,1,0))
    before=checked('fault clock',psspy.dsrval('TIME'));checked('fault',psspy.dist_bus_fault(1,3,100,[0,20]))
    checked('fault interval',psspy.run(0,.1,1000000,1,0));clear=checked('clear clock',psspy.dsrval('TIME'));checked('clear',psspy.dist_clear_fault(1))
    checked('recovery',psspy.run(0,.3,1000000,1,0))
    _,labels,channels=dyntools.CHNF('native.out').get_data();indices=sorted(k for k in labels if isinstance(k,int));assert all(math.isfinite(v) for k in ['time']+indices for v in channels[k])
    with Path('native.csv').open('w',newline='',encoding='utf-8') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in indices])
        for i,time in enumerate(channels['time']):writer.writerow([format(time,'.12g')]+[format(channels[k][i],'.12g') for k in indices])
    log=Path('native.log').read_text(errors='replace');quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','probe.py','hplntd_fault_probe.py','native.log','native.out','native.csv']
    m=dict(status='native-converged-profile' if quality else 'rejected-native-quality',model='GENQEC',profile=profile,selector=selector,actual_selector=actual_selector,declared_constants=constants,actual_constants=actual,layout=layout,step_s=step,end_s=.3,events=[dict(time_s=.05,actual_time_s=before,action='fault'),dict(time_s=.1,actual_time_s=clear,action='clear')],fault=dict(bus=1,impedance_ohm=[0,20],impedance_system_pu=[0,.2]),sample_count=len(channels['time']),channel_count=len(indices),library_sha256=sha(library),source_document_sha256=sha(document),python_sha256=sha(sys.executable),wrapper_sha256=sha(psspy.__file__),engine_module=psse3607.__name__,files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(m,indent=2)+'\n');print(json.dumps(dict(status=m['status'],profile=profile,layout=layout,samples=m['sample_count'])));assert quality


if __name__=='__main__':main()
