"""Declared zero-time and disabled-feedback discriminators, without state forcing."""
from pathlib import Path
import sys
from gensae_field_probe import configuration as field_configuration, machine_channels
from gensal_native_probe import state_names
from genrou_native_probe import run_case

MODES = {
    'zero-transducer': (0,),
    'zero-regulator': (8,),
    'first-bypass': (3, 4),
    'second-bypass': (5, 6),
    'zero-feedback': (14,),
    'all-bypass': (0, 3, 4, 5, 6, 8, 14),
}


def configuration(mode, raw):
    if mode not in MODES:
        raise ValueError(mode)
    cfg = field_configuration('esst1a-unlimited', raw)
    constants = cfg['components'][0]['constants']
    for index in MODES[mode]:
        constants[index] = 0.
    cfg['dynamic'] = '\n'.join(cfg['dynamic'].splitlines()[:2]) + '\n'
    cfg['dynamic'] += "1 'ESST1A' '1' 1 1 " + ' '.join(map(str, constants)) + ' /\n'
    cfg['apply_fault'] = False
    cfg['vref_increment'] = .02
    cfg['record_live_states'] = True
    return cfg


def main():
    tag, step, mode = sys.argv[1:]
    source = Path.cwd() / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    cfg = configuration(mode, source.read_text())
    run_case(tag, step, mode, cfg, directory_prefix='esst1a-bypass-',
             probe_source=__file__, state_names=state_names('exponential-fault'),
             record_phasors=True, extra_machine_channels=machine_channels(cfg),
             support_files=('gensal_native_probe.py', 'gensae_field_probe.py'))


if __name__ == '__main__':
    main()
