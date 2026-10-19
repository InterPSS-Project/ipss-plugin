"""Direct reference-step discriminators; no parameter fitting or state injection."""
import base64
import hashlib
from pathlib import Path
import re
import sys
import hplntd_fault_probe

MODES = {
    'v-outside-up': ({14: '1', 13: '1', 46: '0'}, ((0, .2),)),
    'v-outside-down': ({14: '1', 13: '1', 46: '0'}, ((0, -.01),)),
    'v-inside-up': ({14: '1', 13: '1', 46: '0', 45: '10'}, ((0, .2),)),
    'v-inside-down': ({14: '1', 13: '1', 46: '0', 45: '10'}, ((0, -.2),)),
    'v-inside-large': ({14: '1', 13: '1', 46: '0', 45: '10'}, ((0, 2.),)),
    'v-integral-narrow': ({14: '1', 13: '1', 71: '1.0', 72: '.9'}, ((0, .2),)),
    'q-upper': ({}, ((1, 2.),)),
    'pf-lower': ({14: '2'}, ((9, -.5),)),
}


def variant(original, mode):
    if hashlib.sha256(original).hexdigest() != '1a6bd67dbd0c25557697d469a0e7a738536f0a88d68034592eb3bae16063d77c':
        raise ValueError('Changed reference-limit baseline')
    text = original.decode()
    record, = [line for line in text.splitlines() if "'USRBUS'" in line]
    fields = list(re.finditer(r"'(?:[^']|'')*'|[^\s,]+", record))
    changed = record
    for index, value in sorted(MODES[mode][0].items(), reverse=True):
        field = fields[index]
        changed = changed[:field.start()] + value + changed[field.end():]
    return text.replace(record, changed).encode()


if __name__ == '__main__':
    tag, step, mode = sys.argv[1:]
    source = Path('ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64')
    hplntd_fault_probe.run_case(tag, step, dynamic=variant(base64.b64decode(source.read_bytes()), mode),
                              apply_fault=False, label=mode,
                              directory_prefix='hplntd-control-reference-', probe_source=__file__,
                              reference_steps=MODES[mode][1])
