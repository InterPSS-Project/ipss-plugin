"""Cold small-testbed profiles for native TGOV1 machine-base and damping semantics."""
import csv
import json
import math
import os
from pathlib import Path
import shutil
import sys
from tgov1_limit_probe import checked, sha, STEPS


def profile(rating, damping, grid, repeat, network='default'):
    if rating not in ('50', '200') or damping not in ('zero', 'damped') or grid not in STEPS or repeat not in ('a', 'b') or network not in ('default','tight'):
        raise ValueError('Expected 50/200, zero/damped, coarse/fine, a/b and optional default/tight')
    settings=dict(name=f'{rating}-{damping}-{grid}', machine_mva=int(rating),
                constants=[.05,.04,1.2,.2,.1,.3,0. if damping=='zero' else .7],
                step_s=STEPS[grid], increment=.005, apply_s=.05, release_s=.20, end_s=.5, repeat=repeat)
    if network=='tight':settings.update(name=settings['name']+'-tight',network_tolerance=1e-8,network_max_iterations=200)
    return settings


def inputs(root, settings):
    source=root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB'
    lines=(source/'SMIB_v33.raw').read_text().splitlines()
    index=next(i for i,line in enumerate(lines) if line.startswith("1,'1',50.001,"))
    fields=lines[index].split(',');assert fields[8]=='100.000'
    fields[8]=str(settings['machine_mva']);lines[index]=','.join(fields)
    dynamic=(source/'SMIB_v33_genrou_tgov1.dyr').read_text()
    old="1 'TGOV1' 1 0.05 0.4 1.2 0.0 2.0 8.0 0.0 /";assert dynamic.count(old)==1
    dynamic=dynamic.replace(old,"1 'TGOV1' 1 "+' '.join(map(str,settings['constants']))+' /')
    return '\n'.join(lines)+'\n',dynamic


def main():
    settings=profile(*sys.argv[1:])
    import psse3607
    import psspy
    import dyntools
    root=Path.cwd();out=root/'target'/('tgov1-base-'+settings['name']+'-'+settings['repeat']);out.mkdir(exist_ok=False)
    raw,dyr=inputs(root,settings);(out/'network.raw').write_text(raw);(out/'dynamic.dyr').write_text(dyr)
    shutil.copy2(__file__,out/'probe.py');shutil.copy2(root/'tools/tgov1_limit_probe.py',out/'probe_dependency.py');os.chdir(out)
    checked('initialize',psspy.psseinit(50))
    for fn in (psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output):checked('log',fn(2,'native.log',[0,0]))
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL';document=library.parent.parent/'DOCS/ModelLibrary.pdf'
    checked('library',psspy.addmodellibrary(str(library)));checked('network',psspy.read(0,'network.raw'))
    entered_mva=checked('machine base',psspy.macdat(1,'1','MBASE'));assert entered_mva==settings['machine_mva']
    checked('flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
    checked('generators',psspy.cong(0))
    for stage in (1,2,3):checked('loads',psspy.conl(0,1,stage,[0,0],[0.]*4))
    checked('order',psspy.ordr(0));checked('factor',psspy.fact());checked('solution',psspy.tysl(0))
    checked('dynamic input',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''))
    layout={}
    for kind in ('CON','STATE','VAR'):
        layout[kind]=checked('index '+kind,psspy.mdlind(1,'1','GOV',kind))
        layout['N'+kind]=checked('count '+kind,psspy.mdlind(1,'1','GOV','N'+kind))
    assert [layout['N'+kind] for kind in ('CON','STATE','VAR')]==[7,2,1]
    def constants():return [checked('constant',psspy.dsrval('CON',layout['CON']+i)) for i in range(7)]
    before=constants()
    for i in range(2):checked('state',psspy.state_channel([-1,layout['STATE']+i],f'GOV_STATE_{i+1}'))
    checked('reference',psspy.var_channel([-1,layout['VAR']],'GOV_REFERENCE'))
    for code,name in ((1,'MACH_ANGLE'),(2,'P_PU'),(3,'Q_PU'),(6,'PMECH'),(7,'SPEED')):
        checked('machine',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2):checked('voltage',psspy.voltage_channel([-1,-1,-1,bus],f'V_BUS{bus}'))
    di,df=psspy.getdefaultint(),psspy.getdefaultreal()
    checked('step',psspy.dynamics_solution_param_2([settings.get('network_max_iterations',di)]+[di]*7,[df,settings.get('network_tolerance',df),settings['step_s']]+[df]*5))
    checked('start',psspy.strt_2([0,0],'native.out'));initialized=constants()
    assert all(abs(a-b)<1e-7*max(1,abs(b)) for a,b in zip(initialized,settings['constants'],strict=True))
    assert checked('initialized machine base',psspy.macdat(1,'1','MBASE'))==entered_mva
    checked('flat',psspy.run(0,settings['apply_s'],1000000,1,0));events=[]
    for name,increment,until in [('apply',settings['increment'],settings['release_s']),('release',-settings['increment'],settings['end_s'])]:
        time=checked('clock',psspy.dsrval('TIME'));checked('increment',psspy.increment_gref(1,'1',increment))
        events.append(dict(action=name,actual_time_s=time,increment=increment));checked(name,psspy.run(0,until,1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int))
    assert len(keys)==10 and all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,time in enumerate(data['time']):writer.writerow([format(time,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    log=Path('native.log').read_text(errors='replace');quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','probe.py','probe_dependency.py','native.log','native.out','native.csv']
    manifest=dict(status='complete' if quality else 'rejected-quality',model='TGOV1',settings=settings,layout=layout,
                  machine_mva=entered_mva,entered_constants=before,initialized_constants=initialized,events=events,
                  sample_count=len(data['time']),channel_count=len(keys),engine_module=psse3607.__name__,
                  library_sha256=sha(library),source_document_sha256=sha(document),python_sha256=sha(sys.executable),
                  wrapper_sha256=sha(psspy.__file__),files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(dict(profile=settings['name'],status=manifest['status'])))
    assert quality


if __name__=='__main__':main()
