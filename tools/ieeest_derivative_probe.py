"""Voltage-derivative memory and event discriminators on the core-machine SMIB."""
from pathlib import Path
import sys
from genrou_native_probe import run_case
from ieeest_measurement_probe import configuration as measurement_configuration


def configuration(profile, raw):
    location, event = profile.split('-')
    if location not in ('local', 'remote') or event not in ('flat', 'fault', 'reference'):
        raise ValueError(profile)
    cfg = measurement_configuration('voltage-' + location + '-' + ('fault' if event == 'fault' else 'flat'), raw)
    lines = cfg['dynamic'].splitlines()
    fields = lines[-1].split(); fields[3] = '6'; lines[-1] = ' '.join(fields)
    cfg['dynamic'] = '\n'.join(lines) + '\n'
    cfg['components'][-1]['icons'][0] = 6
    cfg['components'][-1]['variables'] = ('PSS_MEMORY', 'PSS_VDOT')
    cfg['record_live_variables'] = True
    cfg['record_live_fault_states'] = True
    if event == 'reference':
        cfg['vref_increment'] = .01
        cfg['record_live_event_states'] = True
    return cfg


def main():
    tag, step, profile = sys.argv[1:]
    source = Path.cwd() / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    run_case(tag, step, profile, configuration(profile, source.read_text()), directory_prefix='ieeest-input-',
             probe_source=__file__, record_phasors=True,
             extra_machine_channels=((8, 'XAD_IFD'), (9, 'ECOMP'), (10, 'VOTHSG'), (11, 'VREF')),
             support_files=('ieeest_measurement_probe.py', 'ieeest_input_probe.py', 'gensae_field_probe.py', 'gensal_native_probe.py'))


if __name__ == '__main__':
    main()
