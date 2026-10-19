"""Native field-current and composed-controller probes on a public SMIB fixture."""
from pathlib import Path
import sys
from gensal_native_probe import configuration as salient_configuration, state_names
from genrou_native_probe import run_case

MODES = ('open-flat', 'open-fault', 'open-resistive', 'open-mva50', 'open-mva200',
         'open-zero', 'esst1a-flat', 'esst1a-fault', 'esst1a-unlimited', 'esst1a-compensated', 'esst1a-ceiling')
EXC_STATES = ('EXC_VMEAS', 'EXC_LL1', 'EXC_LL2', 'EXC_VA', 'EXC_FEEDBACK')


def configuration(mode, raw):
    if mode not in MODES:
        raise ValueError(mode)
    base = {'open-flat': 'exponential-flat', 'open-resistive': 'exponential-resistive',
            'open-mva50': 'exponential-mva50', 'open-mva200': 'exponential-mva200',
            'open-zero': 'exponential-zero-fault', 'esst1a-flat': 'exponential-flat'}.get(mode, 'exponential-fault')
    cfg = salient_configuration(base, raw)
    if mode.startswith('esst1a-'):
        constants = [.02, .8, -.8, .2, .8, .1, .4, 80., .04, 99., -99., 7.3, -7.3, .2, .1, 1., 2., 1.3]
        if mode == 'esst1a-unlimited':
            constants[13], constants[16] = 0., 0.
        if mode == 'esst1a-ceiling':
            constants[11], constants[16] = 1.6, 0.
        cfg['dynamic'] += "1 'ESST1A' '1' 1 1 " + ' '.join(map(str, constants)) + " /\n"
        cfg['components'] = [dict(kind='EXC', model='ESST1A', icons=[1, 1],
                                  constants=constants, states=EXC_STATES)]
        if mode == 'esst1a-compensated':
            cfg['dynamic'] += "1 'IEEEVC' '1' .01 .02 /\n"
    return cfg


def machine_channels(cfg):
    # VREF is undefined without a connected regulator. Do not record unused
    # native memory as a physical channel or normalize its nondeterminism away.
    result = ((8, 'XAD_IFD'), (9, 'ECOMP'))
    return result + ((11, 'VREF'),) if cfg.get('components') else result


def main():
    tag, step, mode = sys.argv[1:]
    source = Path.cwd() / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    cfg = configuration(mode, source.read_text())
    run_case(tag, step, mode, cfg,
             directory_prefix='gensae-field-', probe_source=__file__,
             state_names=state_names('exponential-fault'), record_phasors=True,
             extra_machine_channels=machine_channels(cfg),
             support_files=('gensal_native_probe.py',))


if __name__ == '__main__':
    main()
