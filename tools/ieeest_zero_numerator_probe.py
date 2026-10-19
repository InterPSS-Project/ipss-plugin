"""Native T5=0 terminal-lag discriminators; no parameter or initial-state fitting."""
from pathlib import Path
import sys
from ieeest_input_probe import configuration as input_configuration,MODES
from genrou_native_probe import run_case

def configuration(mode,raw):
    cfg=input_configuration(mode,raw)
    cfg['components'][1]['constants'][10]=0.
    cfg['dynamic']='\n'.join(cfg['dynamic'].splitlines()[:-1])+'\n'
    cfg['dynamic']+="1 'IEEEST' '1' "+str(MODES[mode])+" 0 "+' '.join(map(str,cfg['components'][1]['constants']))+' /\n'
    return cfg

def main():
    tag,step,mode=sys.argv[1:]
    source=Path.cwd()/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    run_case(tag,step,mode,configuration(mode,source.read_text()),directory_prefix='ieeest-input-',
             probe_source=__file__,record_phasors=True,
             extra_machine_channels=((8,'XAD_IFD'),(9,'ECOMP'),(10,'VOTHSG'),(11,'VREF')),
             support_files=('ieeest_input_probe.py','gensae_field_probe.py','gensal_native_probe.py'))

if __name__=='__main__':main()
