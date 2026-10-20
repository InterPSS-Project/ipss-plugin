"""Observe terminal-bus frequency without changing the limiter's physical inputs."""
from pathlib import Path
import sys
from basoel2_native_probe import configuration as base_configuration
from genrou_native_probe import run_case


def configuration(profile,raw):
    cfg=base_configuration(profile,raw)
    cfg['frequency_buses']=(1,)
    return cfg


if __name__=='__main__':
    tag,step,profile=sys.argv[1:]
    raw=(Path.cwd()/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    run_case('basoel2-terminal-'+tag,step,profile,configuration(profile,raw),probe_source=__file__,
             extra_machine_channels=((8,'XAD_IFD'),(11,'VREF'),(13,'VOEL_MACHINE')),
             support_files=('basoel2_native_probe.py',))
