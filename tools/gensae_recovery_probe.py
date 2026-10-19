"""Three-second composed generator/exciter/governor SMIB discriminators."""
from pathlib import Path
import sys
from gensae_field_probe import configuration as field_configuration, machine_channels
from gensal_native_probe import configuration as machine_configuration, state_names
from genrou_native_probe import run_case

MODES = ('flat', 'fault', 'ceiling', 'compensated', 'mva50', 'mva200',
         'zero-damping', 'valve-limits', 'valve-lower')
GOV_STATES = ('GOV_VALVE', 'GOV_STORAGE')


def configuration(mode, raw):
    if mode not in MODES:
        raise ValueError('Unknown composed recovery profile: ' + mode)
    base = {'flat': 'esst1a-flat', 'ceiling': 'esst1a-ceiling',
            'compensated': 'esst1a-compensated'}.get(mode, 'esst1a-fault')
    cfg = field_configuration(base, raw)
    if mode in ('mva50', 'mva200'):
        machine = machine_configuration('exponential-' + mode, raw)
        cfg['raw'], cfg['mva'] = machine['raw'], machine['mva']
    constants = [.05, .04, 1.2, .2, .1, .3, .7]
    if mode == 'zero-damping':
        constants[6] = 0.
    if mode == 'valve-limits':
        constants[2:4] = [.51, .49]
    if mode == 'valve-lower':
        constants[3] = .499
    cfg['dynamic'] += "1 'TGOV1' '1' " + ' '.join(map(str, constants)) + ' /\n'
    cfg['components'].append(dict(kind='GOV', model='TGOV1', icons=[],
                                  constants=constants, states=GOV_STATES,
                                  variables=('GOV_REFERENCE',)))
    cfg['end_s'] = 3.
    return cfg


def main():
    tag, step, mode = sys.argv[1:]
    source = Path.cwd() / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    cfg = configuration(mode, source.read_text())
    run_case(tag, step, mode, cfg, directory_prefix='gensae-recovery-',
             probe_source=__file__, state_names=state_names('exponential-fault'),
             record_phasors=True, extra_machine_channels=machine_channels(cfg),
             support_files=('gensal_native_probe.py', 'gensae_field_probe.py'))


if __name__ == '__main__':
    main()
