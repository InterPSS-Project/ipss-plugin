"""Independent local/remote bus measurement discriminators on the SMIB."""
from pathlib import Path
import sys
from genrou_native_probe import run_case
from ieeest_input_probe import configuration as power_configuration

def configuration(profile, raw):
    signal, location, event = profile.split('-')
    if signal not in ('frequency', 'voltage') or location not in ('local', 'remote') or event not in ('flat', 'fault'):
        raise ValueError(profile)
    cfg = power_configuration('accelerating-' + event, raw)
    mode, remote = (2 if signal == 'frequency' else 5), (0 if location == 'local' else 2)
    lines = cfg['dynamic'].splitlines()
    fields = lines[-1].split()
    fields[3:5] = [str(mode), str(remote)]
    lines[-1] = ' '.join(fields)
    cfg['dynamic'] = '\n'.join(lines) + '\n'
    cfg['components'][-1]['icons'] = [mode, remote]
    cfg['frequency_buses'] = (1, 2)
    return cfg

def main():
    tag, step, profile = sys.argv[1:]
    source = Path.cwd() / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw'
    run_case(tag, step, profile, configuration(profile, source.read_text()), directory_prefix='ieeest-input-',
             probe_source=__file__, record_phasors=True,
             extra_machine_channels=((8, 'XAD_IFD'), (9, 'ECOMP'), (10, 'VOTHSG'), (11, 'VREF')),
             support_files=('ieeest_input_probe.py', 'gensae_field_probe.py', 'gensal_native_probe.py'))

if __name__ == '__main__':
    main()
