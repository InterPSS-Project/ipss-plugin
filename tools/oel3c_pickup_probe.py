"""Predeclared one-parameter pickup discriminators; never a fitted reference."""
import json
from pathlib import Path
import shutil
import sys
import oel3c_powerworld_probe as base


def with_pickup(cfg, pickup):
    if pickup not in (.4, .6):
        raise ValueError('Only the predeclared pickup discriminators are allowed')
    cfg['components'][1]['constants'][0] = pickup
    lines = cfg['dynamic'].splitlines()
    index = next(i for i, line in enumerate(lines) if "'OEL3C'" in line)
    tokens = lines[index].split()
    tokens[5] = str(pickup)
    lines[index] = ' '.join(tokens)
    cfg['dynamic'] = '\n'.join(lines) + '\n'
    return cfg


def main():
    tag, entered, step = sys.argv[1:]
    pickup = float(entered)
    original = base.configuration
    base.configuration = lambda profile, raw: with_pickup(original(profile, raw), pickup)
    sys.argv = [sys.argv[0], tag, step, 'vfe-fault', '--measurement-channels']
    base.main()
    out = Path('target') / ('oel3c-powerworld-' + tag)
    shutil.copy2(__file__, out / 'pickup-driver.py')
    (out / 'pickup-protocol.json').write_text(json.dumps(dict(
        discriminator='Vfe with only entered pickup changed', entered_pickup=pickup,
        allowed_pickups=[.4, .6], parameters_fitted=False, trajectory_acceptance=False,
        invocation=['pickup-driver.py', tag, entered, step]), indent=2) + '\n')


if __name__ == '__main__':
    main()
