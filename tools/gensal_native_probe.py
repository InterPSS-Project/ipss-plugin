"""Exact native five-state salient-pole SMIB discriminators and phasor channels."""
from pathlib import Path
import sys
from genrou_native_probe import configuration as round_configuration, run_case

PROFILES = ('flat', 'fault', 'damping', 'saturated-flat', 'saturated-fault',
            'resistive', 'mva50', 'mva200', 'exponential-flat', 'exponential-fault',
            'exponential-damping', 'exponential-resistive', 'exponential-mva50',
            'exponential-mva200', 'exponential-zero-flat', 'exponential-zero-fault')
BASE_CON = (6., .033, .078, 6.4, 0., .8958, .8645, .1189, .089, .0521, 0., 0.)


def configuration(profile, raw):
    if profile not in PROFILES:
        raise ValueError('Unknown salient-pole profile: ' + profile)
    # Reuse only the exact public network/base/resistance discriminator, not
    # the round-rotor electrical parameter schema or state order.
    base = profile.removeprefix('exponential-') if profile.startswith('exponential-') else profile
    base = base.removeprefix('zero-')
    cfg = round_configuration(base, raw)
    constants = list(BASE_CON)
    if base == 'damping':
        constants[4] = 2.5
    if profile.startswith(('saturated-', 'exponential-')) and not profile.startswith('exponential-zero-'):
        constants[10:] = [.1, .4]
    cfg['model'] = 'GENSAE' if profile.startswith('exponential-') else 'GENSAL'
    cfg['constants'] = constants
    cfg['dynamic'] = "1 '" + cfg['model'] + "' '1' " + ' '.join(map(str, constants))
    cfg['dynamic'] += " /\n2 'GENCLS' '1' 99999 0 /\n"
    return cfg


def state_names(profile):
    if profile not in PROFILES:
        raise ValueError(profile)
    # The two native implementations expose the same coordinates in different
    # allocation order. Label each actual allocation rather than permuting data.
    electrical = ('EP_Q', 'PSIQ_PP', 'PSIK_D') if profile.startswith('exponential-') else ('EP_Q', 'PSIK_D', 'PSIQ_PP')
    return electrical + ('SPEED_DEV', 'ANGLE')


def main():
    tag, step, profile = sys.argv[1:]
    source = Path.cwd() / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    run_case(tag, step, profile, configuration(profile, source.read_text()),
             directory_prefix='gensal-native-', probe_source=__file__,
             state_names=state_names(profile), record_phasors=True)


if __name__ == '__main__':
    main()
