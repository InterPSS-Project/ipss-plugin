"""Exact native thirty-constant PSS5C SMIB profiles; no typed-layout conversion."""
from pathlib import Path
import json
import sys
from genrou_native_probe import configuration as machine_configuration, run_case, sha
from gensae_field_probe import EXC_STATES

EXC_CON=(.02,.8,-.8,.2,.8,.1,.4,80.,.04,99.,-99.,7.3,-7.3,0.,.1,1.,0.,1.)
PSS_CON=(.001759,.00012739,.017823,66.,1.,1.,81.,80.,66.,
         .5,.01,.01,-.01,3.,.07,.075,-.075,20.,.6,.6,-.6,80.,9.,.6,-.6,
         5.736,6.883,8.259,.15,-.15)
MODES=('flat','fault','transducer-fault','gain-fault','very-low-fault','unit-frequency-fault','unit-frequency-no-dli-fault',
       'fine-observation-flat','fine-observation-fault','fine-observation-transducer-fault')


def configuration(mode,raw):
    assert mode in MODES
    cfg=machine_configuration('flat' if mode in ('flat','fine-observation-flat') else 'fault',raw)
    constants=list(PSS_CON)
    if mode in ('transducer-fault','fine-observation-transducer-fault'):
        constants[:3]=[.003,.0002,.025]
        constants[4:8]=[.8,1.2,70.,65.]
    if mode=='gain-fault':constants[3],constants[8]=33.,99.
    if mode=='very-low-fault':
        for index in (13,17,21):constants[index]=0.
    if mode.startswith(('unit-frequency','fine-observation')):
        for index in (10,14,18,22):constants[index]=1.
        if mode=='unit-frequency-no-dli-fault':constants[0]=0.
    cfg['dynamic']+="1 'ESST1A' '1' 1 1 "+' '.join(map(str,EXC_CON))+' /\n'
    cfg['dynamic']+="1 'PSS5C' '1' "+' '.join(map(str,constants))+' /\n'
    cfg['components']=[dict(kind='EXC',model='ESST1A',icons=[1,1],constants=list(EXC_CON),states=EXC_STATES),
        dict(kind='STAB',model='PSS5C',icons=[0],constants=constants,
             states=tuple('PSS_STATE_'+str(i+1) for i in range(13)),
             variables=('PSS_LOW_SIGNAL','PSS_HIGH_SIGNAL','PSS_VVL','PSS_VL','PSS_VI','PSS_VH'))]
    cfg['record_live_states']=True
    if mode.startswith('fine-observation'):
        cfg['pss5c_observation_diagnostic']=True
        cfg['record_live_variables']=True
        cfg['record_live_fault_states']=True
    return cfg


def main():
    tag,step,mode=sys.argv[1:]
    assert tag and all(c.isalnum() or c in '-_' for c in tag)
    source=Path.cwd()/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    run=Path.cwd()/'target'/('pss5c-native-'+tag)
    if run.exists():
        raise FileExistsError('Preserve existing reference evidence; choose a fresh run tag')
    try:
        run_case(tag,step,mode,configuration(mode,source.read_text()),directory_prefix='pss5c-native-',
                 probe_source=__file__,record_phasors=True,extra_machine_channels=((10,'VOTHSG'),),
                 support_files=('gensae_field_probe.py','gensal_native_probe.py'))
    except FileExistsError:
        raise # A concurrent creator also owns its existing evidence.
    except Exception as failure:
        if (run/'native.log').is_file():
            files={p.name:sha(p) for p in run.iterdir() if p.is_file() and p.suffix in ('.raw','.dyr','.log','.out','.py')}
            (run/'rejected-run.json').write_text(json.dumps(dict(status='rejected-native-probe',profile=mode,
                exception_type=type(failure).__name__,trajectory_acceptance=False,files=files),indent=2)+'\n')
        raise


if __name__=='__main__':main()
