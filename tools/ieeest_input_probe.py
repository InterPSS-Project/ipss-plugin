"""Exact native electrical/accelerating-power selectors on the core-machine SMIB."""
from pathlib import Path
import sys
from genrou_native_probe import configuration as machine_configuration,run_case
from gensae_field_probe import EXC_STATES

MODES={'electrical-flat':3,'electrical-fault':3,'accelerating-flat':4,'accelerating-fault':4}
EXC_CON=(.02,.8,-.8,.2,.8,.1,.4,80.,.04,99.,-99.,7.3,-7.3,0.,.1,1.,0.,1.)
PSS_CON=(.061,.0017,.020,0.,0.,0.,.30,.03,.30,.03,5.,5.,5.,.05,-.05,0.,0.)
PSS_STATES=('PSS_FILTER1','PSS_FILTER2','PSS_FILTER3','PSS_FILTER4','PSS_LL1','PSS_LL2','PSS_LAST')

def configuration(mode,raw):
    if mode not in MODES:
        raise ValueError(mode)
    cfg=machine_configuration('flat' if mode.endswith('flat') else 'fault',raw)
    cfg['dynamic']+="1 'ESST1A' '1' 1 1 "+' '.join(map(str,EXC_CON))+' /\n'
    cfg['dynamic']+="1 'IEEEST' '1' "+str(MODES[mode])+" 0 "+' '.join(map(str,PSS_CON))+' /\n'
    cfg['components']=[dict(kind='EXC',model='ESST1A',icons=[1,1],constants=list(EXC_CON),states=EXC_STATES),
                       dict(kind='STAB',model='IEEEST',icons=[MODES[mode],0],constants=list(PSS_CON),states=PSS_STATES,
                            variable_count=2)]
    cfg['record_live_states']=True
    return cfg

def main():
    tag,step,mode=sys.argv[1:]
    source=Path.cwd()/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    run_case(tag,step,mode,configuration(mode,source.read_text()),directory_prefix='ieeest-input-',
             probe_source=__file__,record_phasors=True,
             extra_machine_channels=((8,'XAD_IFD'),(9,'ECOMP'),(10,'VOTHSG'),(11,'VREF')),
             support_files=('gensae_field_probe.py','gensal_native_probe.py'))

if __name__=='__main__':main()
