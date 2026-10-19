"""Reference-selector discriminators for disabled hybrid plant control paths."""
import base64
import hashlib
from pathlib import Path
import re
import sys
import hplntd_fault_probe

MODES = {
    'p-disabled-vcomp': {15: '0', 14: '1', 13: '1'},
    'p-disabled-vdroop': {15: '0', 14: '1', 13: '0'},
    'p-disabled-pf': {15: '0', 14: '2'},
    'q-disabled-pf': {43: '0', 14: '2'},
    'both-disabled-pf': {15: '0', 43: '0', 14: '2'},
}


def variant(original, mode):
    if hashlib.sha256(original).hexdigest() != '1a6bd67dbd0c25557697d469a0e7a738536f0a88d68034592eb3bae16063d77c':
        raise ValueError('Changed hybrid selector baseline')
    text = original.decode()
    record, = [line for line in text.splitlines() if "'USRBUS'" in line]
    fields = list(re.finditer(r"'(?:[^']|'')*'|[^\s,]+", record))
    changed = record
    for index, value in sorted(MODES[mode].items(), reverse=True):
        field = fields[index]
        changed = changed[:field.start()] + value + changed[field.end():]
    return text.replace(record, changed).encode()


if __name__ == '__main__':
    tag, step, mode = sys.argv[1:]
    source = Path('ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64')
    hplntd_fault_probe.run_case(tag, step, dynamic=variant(base64.b64decode(source.read_bytes()), mode),
                              apply_fault=False, label=mode,
                              directory_prefix='hplntd-control-reference-', probe_source=__file__)
