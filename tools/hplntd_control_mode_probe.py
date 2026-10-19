"""One-variable hybrid control-selector/zero-gain reference discriminator.

Run from repository root: reference-python tools/hplntd_control_mode_probe.py
UNIQUE_TAG STEP_SECONDS MODE. No fault is applied; both references step at 0.05s.
"""
import base64
import hashlib
from pathlib import Path
import re
import shlex
import sys
import hplntd_fault_probe

MODES = {
    'closed': {},
    'p-disabled': {15: '0'},
    'q-disabled': {43: '0'},
    'both-disabled': {15: '0', 43: '0'},
    'ki-zero': {46: '0'},
    'kig-zero': {60: '0'},
    'both-zero': {46: '0', 60: '0'},
}


def variant(original, mode):
    """Change only declared selector/gain tokens in the frozen exact wrapper."""
    changes = MODES[mode]
    if hashlib.sha256(original).hexdigest() != '1a6bd67dbd0c25557697d469a0e7a738536f0a88d68034592eb3bae16063d77c':
        raise ValueError('Changed hybrid discriminator baseline')
    text = original.decode()
    records = [s for s in text.splitlines() if "'USRBUS'" in s]
    if len(records) != 1:
        raise ValueError('Exactly one plant wrapper required')
    record = records[0]
    tokens = shlex.split(record.rstrip().removesuffix('/'))
    if len(tokens) != 105 or tokens[3:9] != ['504', '0', '35', '61', '13', '45']:
        raise ValueError('Changed hybrid wrapper allocation')
    fields = list(re.finditer(r"'(?:[^']|'')*'|[^\s,]+", record))
    changed = record
    for index, value in sorted(changes.items(), reverse=True):
        field = fields[index]
        assert not field.group().startswith("'")
        changed = changed[:field.start()] + value + changed[field.end():]
    return text.replace(record, changed).encode()


def main():
    tag, step, mode = sys.argv[1:]
    resource = Path('ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64')
    dynamic = variant(base64.b64decode(resource.read_bytes()), mode)
    hplntd_fault_probe.run_case(tag, step, dynamic=dynamic, apply_fault=False,
                              label=mode, directory_prefix='hplntd-control-reference-', probe_source=__file__)


if __name__ == '__main__':
    main()
