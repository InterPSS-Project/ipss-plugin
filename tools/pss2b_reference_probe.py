"""Independent PSS2B SMIB selectors, limits, bypasses and event-memory discriminators."""
import argparse
from pathlib import Path

from genrou_native_probe import run_case
from ieeest_input_probe import configuration as exciter_configuration


def configuration(raw, selector, slot, remote, event, limits=False, bypass=False, ramp_m=5, ramp_n=1):
    if selector not in range(1, 7) or slot not in (1, 2) or remote not in (0, 2):
        raise ValueError('Unsupported selector, input slot or signal bus')
    if remote and selector not in (2, 5, 6):
        raise ValueError('Machine measurements cannot select a remote bus')
    if event not in ('flat', 'fault', 'reference'):
        raise ValueError(event)
    cfg = exciter_configuration('accelerating-' + ('fault' if event == 'fault' else 'flat'), raw)
    # Published standard example; the alternate input remains a physical measurement.
    constants = [3., 3., 0., 3., 0., 3., 1.25, 1., .5, .1, 3.,
                 .13, .023, .13, .023, .13, .023, 2., -2., 2., -2., .1, -.1]
    if ramp_m < 0 or ramp_n < 0 or ramp_m*ramp_n > 8 or (ramp_m == 0 and ramp_n != 0):
        raise ValueError('Ramp orders must define a proper filter within its state allocation')
    icons = [1, 0, 3, 0, ramp_m, ramp_n]
    icons[(slot-1)*2:(slot-1)*2+2] = [selector, remote]
    if bypass:
        for index in (1, 2, 4, 5, 8, 11, 12, 13, 14, 15, 16):
            constants[index] = 0.
        icons[-2:] = [0, 0]
    if limits:
        bounds = (1.005, .95) if selector == 5 else (.001, -.001)
        constants[17+(slot-1)*2:19+(slot-1)*2] = bounds
        constants[-2:] = [.00001, -.000015]
    lines = cfg['dynamic'].splitlines()
    lines[-1] = "1 'PSS2B' '1' " + ' '.join(map(str, icons + constants)) + ' /'
    cfg['dynamic'] = '\n'.join(lines) + '\n'
    cfg['components'][-1] = dict(kind='STAB', model='PSS2B', icons=icons, constants=constants,
        states=tuple('PSS_STATE_' + str(i) for i in range(17)),
        variables=('PSS_MEMORY_1', 'PSS_DVDT_1', 'PSS_MEMORY_2', 'PSS_DVDT_2'),
        initialize_diagnostic_variables=True)
    cfg.update(frequency_buses=(1, 2), record_live_variables=True,
               record_live_fault_states=True, record_live_event_states=True)
    if event == 'reference':
        cfg['vref_increment'] = .01
    return cfg


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--selector', required=True, type=int, choices=range(1, 7))
    parser.add_argument('--slot', type=int, choices=(1, 2), default=1)
    parser.add_argument('--remote', type=int, choices=(0, 2), default=0)
    parser.add_argument('--event', choices=('flat', 'fault', 'reference'), default='fault')
    parser.add_argument('--limits', action='store_true')
    parser.add_argument('--bypass', action='store_true')
    parser.add_argument('--step', type=float, choices=(.0005, .00025, 1/240, 1/480), default=.0005)
    parser.add_argument('--duration', type=float, choices=(.5, 10.), default=.5)
    parser.add_argument('--ramp-m', type=int, default=5)
    parser.add_argument('--ramp-n', type=int, default=1)
    args = parser.parse_args()
    raw = Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text(encoding='utf8')
    cfg = configuration(raw, args.selector, args.slot, args.remote, args.event, args.limits, args.bypass, args.ramp_m, args.ramp_n)
    cfg['end_s'] = args.duration
    profile = f'selector-{args.selector}-slot-{args.slot}-bus-{args.remote}-{args.event}'
    run_case(args.tag, str(args.step), profile, cfg, directory_prefix='pss2b-native-',
             probe_source=__file__, record_phasors=True,
             extra_machine_channels=((8, 'XAD_IFD'), (9, 'ECOMP'), (10, 'VOTHSG'), (11, 'VREF')),
             support_files=('ieeest_input_probe.py', 'gensae_field_probe.py', 'gensal_native_probe.py'))


if __name__ == '__main__':
    main()
