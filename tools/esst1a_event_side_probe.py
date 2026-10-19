"""Read native allocated states immediately after the reference-change API."""
from pathlib import Path
import sys
from esst1a_bypass_probe import configuration as bypass_configuration
from gensae_field_probe import machine_channels
from gensal_native_probe import state_names
from genrou_native_probe import run_case

MODES=('zero-regulator','all-bypass')

def configuration(mode,raw):
    if mode not in MODES:
        raise ValueError(mode)
    cfg=bypass_configuration(mode,raw)
    cfg['record_live_event_states']=True
    return cfg

def main():
    tag,step,mode=sys.argv[1:]
    source=Path.cwd()/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    cfg=configuration(mode,source.read_text())
    run_case(tag,step,mode,cfg,directory_prefix='esst1a-bypass-',probe_source=__file__,
             state_names=state_names('exponential-fault'),record_phasors=True,
             extra_machine_channels=machine_channels(cfg),
             support_files=('gensal_native_probe.py','gensae_field_probe.py','esst1a_bypass_probe.py'))

if __name__=='__main__':
    main()
