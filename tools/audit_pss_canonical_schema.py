"""Audit exact stabilizer allocations, entered values and complete cold histories."""
import csv
import hashlib
import json
import math
from pathlib import Path
import shlex
import struct
from pss_canonical_schema_probe import LAYOUTS


def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def checked_files(directory, manifest):
    for name, expected in manifest['files'].items():
        path = Path(name)
        assert not path.is_absolute() and '..' not in path.parts, name
        assert digest(directory / path) == expected, name


def table(path):
    with path.open(newline='') as stream:
        rows = list(csv.reader(stream))
    assert len(rows) > 1
    assert len(rows[0]) == len(set(rows[0]))
    assert all(len(row) == len(rows[0]) for row in rows)
    values = [[float(value) for value in row] for row in rows[1:]]
    assert all(math.isfinite(value) for row in values for value in row)
    return rows[0], values


def input_values(path, model):
    tokens = next(shlex.split(line.split('/')[0]) for line in path.read_text().splitlines()
                  if "'" + model + "'" in line)
    values = [float(value) for value in tokens[3:]]
    icons, constants, _, _ = LAYOUTS[model]
    assert len(values) == icons + constants
    assert all(math.isfinite(value) for value in values)
    assert all(value.is_integer() for value in values[:icons])
    return values[:icons], values[icons:]


def audit(root):
    result = []
    for model, layout in LAYOUTS.items():
        runs = []
        for repeat in ('a', 'b'):
            directory = root / 'target' / ('pss-canonical-' + model.lower() + '-' + repeat)
            manifest = json.loads((directory / 'manifest.json').read_text())
            assert manifest['status'] == 'complete' and manifest['model'] == model
            assert tuple(manifest['layout']['N' + k] for k in ('ICON', 'CON', 'STATE', 'VAR')) == layout
            assert manifest['library_sha256'] == 'c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6'
            assert manifest['source_document_sha256'] == '40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d'
            checked_files(directory, manifest)
            assert digest(directory / 'probe.py') == digest(root / 'tools/pss_canonical_schema_probe.py')
            icons, constants = input_values(directory / 'dynamic.dyr', model)
            assert icons == manifest['icons']
            stored = [struct.unpack('f', struct.pack('f', value))[0] for value in constants]
            assert stored == manifest['constants']
            original = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB'
            assert digest(directory / 'network.raw') == digest(original / 'SMIB_v33.raw')
            assert digest(directory / 'dynamic.dyr') == digest(original / ('SMIB_v33_genrou_esst1a_' + model.lower() + '.dyr'))
            assert manifest['step_s'] == .0005 and manifest['end_s'] == 1.
            for event, target in zip(manifest['events'], (.05, .1), strict=True):
                assert abs(event['actual_time_s'] - target) < 1e-6
            log = (directory / 'native.log').read_text(errors='replace')
            assert 'Initial conditions check O.K.' in log and 'Network not converged' not in log
            body = table(directory / 'native.csv')
            assert len(body[0]) == layout[2] + 11 == manifest['channel_count'] + 1
            assert len(body[1]) == manifest['sample_count'] == 2005
            # Retain the accumulated single-precision simulator clock, including
            # its small positive stop-time overshoot; do not relabel timestamps.
            assert body[1][0][0] < 0 and 1 <= body[1][-1][0] < 1 + manifest['step_s']
            assert all(b[0] >= a[0] for a, b in zip(body[1], body[1][1:]))
            assert min(row[body[0].index('V_BUS1')] for row in body[1]) < .95
            legacy = root / 'ipss.test.plugin.core/testData/reference/psse' / ('smib-genrou-esst1a-' + model.lower()) / 'psse.csv'
            assert body == table(legacy), 'Changed native trajectory: ' + model
            runs.append(body)
        assert runs[0] == runs[1]
        result.append(dict(model=model, runs=2, icons=layout[0], constants=layout[1], states=layout[2],
                           samples=len(runs[0][1]), channels=len(runs[0][0]) - 1,
                           all_values_equal_to_existing_reference=True))
    return result


if __name__ == '__main__': print(json.dumps(audit(Path.cwd()), indent=2))
