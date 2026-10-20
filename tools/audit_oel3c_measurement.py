"""Check observed physical measurement meaning, not just entered selector metadata."""
import csv
import json
import math
from pathlib import Path


def read_measurements(path):
    with Path(path).open(newline='') as stream:
        raw = list(csv.reader(stream))
    if raw[:1] != [['ObjectFields']]:
        raise ValueError('Missing reference metadata')
    end = raw.index(['END'])
    fields = raw[2:end]
    rows = []
    for row in raw[end + 2:]:
        if row == ['END']:
            break
        if row and row[-1] == '':
            row = row[:-1]
        if len(row) != len(fields) + 1:
            raise ValueError('Unexpected numerical cardinality')
        values = list(map(float, row))
        if not all(math.isfinite(value) for value in values):
            raise ValueError('Nonfinite measurement')
        rows.append(values)
    if not rows or rows[0][0] != 0 or any(a[0] > b[0] for a, b in zip(rows, rows[1:])):
        raise ValueError('Missing initialization or nonmonotonic trajectory')
    return fields, rows


def initial_measurement(fields, rows, cfg):
    """Zero-TF, unsaturated AC1C discriminator; not a trajectory acceptance test.

    The fixed 1e-6 pu bound covers printed initial-channel serialization, not
    integration error. Do not substitute the much wider trajectory RMSE bound.
    Initial Efd and Ifd can coincide, so report ambiguity rather than certify
    their dynamic routing from equilibrium alone.
    """
    exciter, limiter = cfg['components']
    con = exciter['constants']
    if exciter['model'] != 'AC1C' or con[14] != 0 or con[16] != 0 or limiter['constants'][2] != 0:
        raise ValueError('Discriminator requires zero TF and unsaturated AC1C')
    row = rows[0]
    if row[0] != 0:
        raise ValueError('Missing initialization row')

    def channel(name):
        indices = [index + 1 for index, field in enumerate(fields)
                   if field[0].strip() == 'Generator' and field[1].strip() == '1 1'
                   and field[4].strip() == name]
        if len(indices) != 1:
            raise ValueError('Missing or duplicate physical channel: ' + name)
        return row[indices[0]]

    efd, ifd, ve = channel('TSEfd'), channel('TSIfd'), channel('TSExciterState:1')
    candidates = dict(Efd=efd, Ifd=ifd, Vfe=con[12] * ve + con[11] * ifd)
    scale = limiter['constants'][1]
    observed = channel('TSOELState:1')
    residuals = {key: abs(observed - scale * value) for key, value in candidates.items()}
    matches = [key for key, value in residuals.items() if value < 1e-6]
    intended = {1: 'Efd', 2: 'Ifd', 3: 'Vfe'}[limiter['icons'][0]]
    consistent = intended in matches
    return dict(intended=intended, matching_physical_inputs=matches,
                initial_scaled_input=observed, initial_physical_inputs=candidates,
                absolute_scaled_residuals=residuals, serialization_bound_pu=1e-6,
                status='mismatch' if not consistent else 'ambiguous' if len(matches) > 1 else 'consistent',
                intended_initial_measurement_consistent=consistent,
                runtime_routing_verified=False, trajectory_acceptance=False,
                raw_rows=len(rows), rows_dropped=0)


def require_initial_measurement(fields, rows, cfg):
    result = initial_measurement(fields, rows, cfg)
    if not result['intended_initial_measurement_consistent']:
        raise ValueError('Physical input mismatch: intended ' + result['intended']
                         + ', observed candidates ' + ','.join(result['matching_physical_inputs']))
    return result


def rmse(errors):
    """User-approved new-channel criterion; callers must establish units/event sides."""
    values = list(errors)
    if not values or any(not math.isfinite(value) for value in values):
        raise ValueError('Empty or nonfinite channel errors')
    value = math.sqrt(math.fsum(error * error for error in values) / len(values))
    return dict(samples=len(values), rmse=value, maximum_absolute=max(map(abs, values)),
                rmse_limit=0.001, passes_rmse=value < 0.001)


if __name__ == '__main__':
    import sys
    from oel3c_routing_probe import configuration
    folder, profile = Path(sys.argv[1]), sys.argv[2]
    raw = Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    print(json.dumps(initial_measurement(*read_measurements(folder / 'powerworld.csv'),
                                        configuration(profile, raw)), indent=2))
