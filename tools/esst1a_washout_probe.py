"""Source-defined constant-regulator washout discriminators on a public SMIB."""
from pathlib import Path
import sys
from gensae_field_probe import configuration as field_configuration, machine_channels
from gensal_native_probe import state_names
from genrou_native_probe import run_case

MODES = {'tau-half': .5, 'tau-one': 1., 'tau-two': 2.}


def configuration(mode, raw):
    if mode not in MODES:
        raise ValueError(mode)
    cfg = field_configuration('esst1a-unlimited', raw)
    exc = cfg['components'][0]
    con = exc['constants']
    con[9], con[15] = 2., MODES[mode]
    cfg['dynamic'] = '\n'.join(cfg['dynamic'].splitlines()[:2]) + '\n'
    cfg['dynamic'] += "1 'ESST1A' '1' 1 1 " + ' '.join(map(str, con)) + ' /\n'
    cfg['apply_fault'] = False
    cfg['end_s'] = 3.
    cfg['vref_increment'] = .2
    cfg['record_live_states'] = True
    return cfg


def main():
    tag, step, mode = sys.argv[1:]
    source = Path.cwd() / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    cfg = configuration(mode, source.read_text())
    run_case(tag, step, mode, cfg, directory_prefix='esst1a-washout-',
             probe_source=__file__, state_names=state_names('exponential-fault'),
             record_phasors=True, extra_machine_channels=machine_channels(cfg),
             support_files=('gensal_native_probe.py', 'gensae_field_probe.py'))


if __name__ == '__main__':
    main()
