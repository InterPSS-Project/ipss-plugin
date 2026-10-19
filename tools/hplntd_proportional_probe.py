"""Exact-input proportional-only equilibrium and feedback discriminators."""
import base64
import hashlib
from pathlib import Path
import re
import sys
import hplntd_fault_probe

MODES = {
    'ki-vcomp': {46: '0', 14: '1', 13: '1', 45: '10'},
    'ki-vdroop': {46: '0', 14: '1', 13: '0', 45: '10'},
    'ki-pf': {46: '0', 14: '2'},
    'ki-deadband': {46: '0', 55: '-.005', 56: '.01'},
    'kig-noffwd': {60: '0', 17: '0'},
    'kig-nofeedback': {60: '0', 16: '0'},
    'kig-neither': {60: '0', 16: '0', 17: '0'},
    'kig-zero-kpg': {60: '0', 59: '0'},
    'both-pf-nofeedback': {46: '0', 60: '0', 14: '2', 16: '0'},
    'integral-pf-nofeedback': {14: '2', 16: '0'},
    'reject-ki-zero-kp': {46: '0', 45: '0'},
    'reject-ki-voltage-bound': {46: '0', 14: '1', 13: '1'},
    'reject-kig-zero-kpg-noffwd': {60: '0', 59: '0', 17: '0'},
}


def variant(original, mode):
    if hashlib.sha256(original).hexdigest() != '1a6bd67dbd0c25557697d469a0e7a738536f0a88d68034592eb3bae16063d77c':
        raise ValueError('Changed proportional discriminator baseline')
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
