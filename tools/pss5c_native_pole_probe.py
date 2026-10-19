"""Cold SMIB state-space response diagnostics; not trajectory acceptance.

The documented state-change API is used only for independent small state-space
perturbations. These states are never injected into an InterPSS comparison.
"""
import json
import os
from pathlib import Path
import shutil
import sys
from genrou_native_probe import checked, f32, sha
from pss5c_native_probe import configuration

FREQUENCIES={'baseline':None,'unity':1.,'half':.5,'double':2.}
TRANSDUCERS={'dli-half':(0,.5),'al-double':(1,2.),'bl-double':(2,2.),
             'explicit-low-first':(1,0.),'high-no-lag':(4,0.),'high-no-quadratic':(5,0.)}


def main():
    import psse3607
    import psspy
    tag,entered_step,profile,entered_amplitude,scope=sys.argv[1:]
    step,amplitude=float(entered_step),float(entered_amplitude)
    assert tag and all(c.isalnum() or c in '-_' for c in tag)
    assert step in (.0001,.00005) and amplitude in (1e-6,5e-7)
    assert profile in FREQUENCIES or profile in TRANSDUCERS
    assert scope in ('pilot','all','sensors')
    root=Path.cwd();out=root/'target'/('pss5c-poles-'+tag);out.mkdir(exist_ok=False)
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    cfg=configuration('flat',raw);constants=cfg['components'][1]['constants']
    if profile in TRANSDUCERS:
        offset,factor=TRANSDUCERS[profile];constants[offset]*=factor
    elif FREQUENCIES[profile] is not None:
        for offset in (10,14,18,22):constants[offset]=FREQUENCIES[profile]
    lines=[line for line in cfg['dynamic'].splitlines() if "'PSS5C'" not in line]
    lines.append("1 'PSS5C' '1' "+' '.join(map(str,constants))+' /')
    (out/'network.raw').write_text(cfg['raw']);(out/'dynamic.dyr').write_text('\n'.join(lines)+'\n')
    shutil.copy2(__file__,out/'probe.py')
    for name in ('pss5c_native_probe.py','genrou_native_probe.py','gensae_field_probe.py','gensal_native_probe.py','hplntd_fault_probe.py'):
        shutil.copy2(root/'tools'/name,out/name)
    os.chdir(out)
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL'
    document=library.parent.parent/'DOCS/ModelLibrary.pdf'
    offsets=(tuple(range(5)) if scope=='sensors' else
             ((5,11) if scope=='pilot' else tuple(range(5,13))))
    cases=[('control',None,0)]+[(f'state-{offset+1}-{sign:+d}',offset,sign) for offset in offsets for sign in (1,-1)]
    results=[]
    for label,offset,sign in cases:
        checked('initialize',psspy.psseinit(50))
        for fn in (psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output):
            checked('log',fn(2,label+'.log',[0,0]))
        checked('library',psspy.addmodellibrary(str(library)))
        checked('network',psspy.read(0,'network.raw'))
        checked('flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
        checked('generators',psspy.cong(0))
        for stage in (1,2,3):checked('loads',psspy.conl(0,1,stage,[0,0],[0.]*4))
        checked('order',psspy.ordr(0));checked('factor',psspy.fact());checked('network',psspy.tysl(0))
        checked('channels reset',psspy.delete_all_plot_channels())
        checked('dynamic',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''))
        layout={key:checked(key,psspy.mdlind(1,'1','STAB',key))
                for key in ('CON','ICON','STATE','VAR','NCON','NICON','NSTATE','NVAR')}
        assert [layout['N'+key] for key in ('ICON','CON','STATE','VAR')]==[1,30,13,6]
        actual=[checked('parameter',psspy.dsrval('CON',layout['CON']+i)) for i in range(30)]
        assert actual==list(map(f32,constants))
        for index in range(13):checked('state channel',psspy.state_channel([-1,layout['STATE']+index],f'PSS_STATE_{index+1}'))
        for index in range(6):checked('variable channel',psspy.var_channel([-1,layout['VAR']+index],f'PSS_VAR_{index+1}'))
        checked('speed channel',psspy.machine_array_channel([-1,7,1],'1','SPEED_DEV'))
        checked('output channel',psspy.machine_array_channel([-1,10,1],'1','VOTHSG'))
        di,df=psspy.getdefaultint(),psspy.getdefaultreal()
        checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,step]+[df]*5))
        checked('start',psspy.strt_2([0,0],label+'.out'))
        checked('warm history',psspy.run(0,0.,1000000,1,0))
        actual_step=checked('step readback',psspy.dsrval('DELT'))
        def observe(phase):
            return dict(phase=phase,time_s=checked('clock',psspy.dsrval('TIME')),
                states=[checked('state',psspy.dsrval('STATE',layout['STATE']+i)) for i in range(13)],
                variables=[checked('variable',psspy.dsrval('VAR',layout['VAR']+i)) for i in range(6)])
        before=observe('before');assert max(map(abs,before['states']))<amplitude/100
        if offset is not None:
            value=before['states'][offset]+sign*amplitude
            checked('state-space perturbation',psspy.change_state(layout['STATE']+offset,[value,0.,value]))
        observations=[before,observe('injected')]
        for index in range(1,5):
            checked('response',psspy.run(0,index*actual_step,1000000,1,0));observations.append(observe('step-'+str(index)))
        final=[checked('parameter recheck',psspy.dsrval('CON',layout['CON']+i)) for i in range(30)]
        assert actual==final
        log=Path(label+'.log').read_text(errors='replace')
        assert 'Initial conditions check O.K.' in log and 'Network not converged' not in log and 'NaN' not in log
        results.append(dict(label=label,state_offset=offset,sign=sign,step_s=actual_step,
                            constants=actual,layout=layout,observations=observations))
        print(json.dumps(dict(case=label,status='complete',steps=4)),flush=True)
    Path('responses.json').write_text(json.dumps(results,indent=2)+'\n')
    files={p.name:sha(p) for p in out.iterdir() if p.is_file()}
    manifest=dict(status='native-state-space-diagnostic',profile=profile,scope=scope,step_s=step,
                  amplitude=amplitude,cases=len(results),library_sha256=sha(library),
                  source_document_sha256=sha(document),python_sha256=sha(sys.executable),wrapper_sha256=sha(psspy.__file__),
                  engine_module=psse3607.__name__,files=files,trajectory_acceptance=False)
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')


if __name__=='__main__':main()
