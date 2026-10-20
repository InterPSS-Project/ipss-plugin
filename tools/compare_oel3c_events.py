"""All-row, event-side SMIB diagnostics on declared physical coordinates."""
import csv
import json
import math
from pathlib import Path
from audit_oel3c_measurement import read_measurements, initial_measurement, rmse

CHANNELS = {
    'V_BUS1': ('Bus', '1', 'TSVpu'), 'V_BUS2': ('Bus', '2', 'TSVpu'),
    'PMW': ('Generator', '1 1', 'TSMW'), 'QMVAR': ('Generator', '1 1', 'TSMvar'),
    'MACH_SPEED': ('Generator', '1 1', 'TSSpeed'), 'REF_SPEED': ('Generator', '2 1', 'TSSpeed'),
    'EXC_FIELD': ('Generator', '1 1', 'TSExciterState:1'),
    'EXC_SENSE': ('Generator', '1 1', 'TSExciterState:2'),
    'EXC_REGULATOR': ('Generator', '1 1', 'TSExciterState:3'),
    'EXC_LEAD_LAG': ('Generator', '1 1', 'TSExciterState:4'),
    'EXC_RATE_FEEDBACK': ('Generator', '1 1', 'TSExciterState:5'),
    'EFD': ('Generator', '1 1', 'TSEfd'), 'IFD_EXC': ('Generator', '1 1', 'TSIfd'),
    'OEL_OUTPUT': ('Generator', '1 1', 'TSVOEL'),
    'OEL_INPUT': ('Generator', '1 1', 'TSOELState:1'),
    'OEL_INTEGRAL': ('Generator', '1 1', 'TSOELState:2'),
}


def grid_tick(time, step):
    if not math.isfinite(time) or time < 0 or not math.isfinite(step) or step <= 0:
        raise ValueError('Invalid clock or step')
    tick = round(time / step)
    if abs(time - tick * step) > 1e-10:
        raise ValueError('Off-grid raw clock; interpolation and clock fitting are prohibited')
    return tick


def read_actual(path):
    with Path(path).open(newline='') as stream:
        records = list(csv.DictReader(stream))
    if not records:
        raise ValueError('Missing actual trajectory')
    result = []
    for record in records:
        side = record.pop('event_side', None)
        if side not in ('regular', 'pre', 'post'):
            raise ValueError('Missing or invalid event side')
        values = {key: float(value) for key, value in record.items()}
        if not all(math.isfinite(value) for value in values.values()):
            raise ValueError('Nonfinite actual trajectory')
        result.append(dict(values, event_side=side))
    return result


def compare(fields, reference, actual, cfg, step):
    if not reference or not actual:
        raise ValueError('Missing trajectory')
    if float(cfg['raw'].splitlines()[0].split(',')[1]) != 100:
        raise ValueError('This declared comparison requires a 100 MVA network base')
    if any(len(row) != len(fields) + 1 or not all(math.isfinite(value) for value in row) for row in reference):
        raise ValueError('Invalid reference numerical row')
    required = set(CHANNELS) | {'MACH_ANGLE', 'REF_ANGLE', 'time_s', 'event_side'}
    if any(not required.issubset(row) for row in actual):
        raise ValueError('Missing actual channel')
    if reference[0][0] != 0 or actual[0]['time_s'] != 0:
        raise ValueError('Missing initialized trajectory')
    if any(a[0] > b[0] for a, b in zip(reference, reference[1:])):
        raise ValueError('Nonmonotonic reference clock')
    if any(a['time_s'] > b['time_s'] for a, b in zip(actual, actual[1:])):
        raise ValueError('Nonmonotonic actual clock')
    indices = {}
    for index, field in enumerate(fields):
        key = tuple(field[i].strip() for i in (0, 1, 4))
        if key in indices:
            raise ValueError('Duplicate reference channel')
        indices[key] = index + 1

    def signal(row, key):
        if key not in indices:
            raise ValueError('Missing reference channel: ' + str(key))
        return row[indices[key]]

    def reference_values(row):
        values = {key: signal(row, source) for key, source in CHANNELS.items()}
        for key in ('PMW', 'QMVAR'):
            values[key] /= 100.0
        for key in ('MACH_SPEED', 'REF_SPEED'):
            values[key] -= 1.0
        values['REL_ANGLE_RAD'] = math.radians(signal(row, ('Generator', '1 1', 'TSRotorAngle'))
                                             - signal(row, ('Generator', '2 1', 'TSRotorAngle')))
        return values

    def actual_values(row):
        values = {key: row[key] for key in CHANNELS}
        for key in ('PMW', 'QMVAR'):
            values[key] /= 100.0
        values['REL_ANGLE_RAD'] = math.radians(row['MACH_ANGLE'] - row['REF_ANGLE'])
        return values

    events = {a[0] for a, b in zip(reference, reference[1:]) if a[0] == b[0]}
    if events != {.05, .1}:
        raise ValueError('Expected both three-cycle fault event occurrences')
    samples = {}
    for row in actual:
        key = (grid_tick(row['time_s'], step), row['event_side'])
        if key in samples:
            raise ValueError('Duplicate actual observation')
        samples[key] = row
    paired = []
    r0, a0 = reference_values(reference[0])['REL_ANGLE_RAD'], actual_values(actual[0])['REL_ANGLE_RAD']
    for index, row in enumerate(reference):
        time = row[0]
        side = 'post' if index > 0 and reference[index-1][0] == time else 'pre' if time in events else 'regular'
        key = (grid_tick(time, step), side)
        if key not in samples:
            raise ValueError('Missing matching event-side observation: ' + str(key))
        observed = samples.pop(key)
        rv, av = reference_values(row), actual_values(observed)
        # A declared additional coordinate, not an offset fitted to reduce error.
        # Absolute relative angles and both initial coordinates remain reported.
        rv['REL_ANGLE_EXCURSION_RAD'] = rv['REL_ANGLE_RAD'] - r0
        av['REL_ANGLE_EXCURSION_RAD'] = av['REL_ANGLE_RAD'] - a0
        paired.append(dict(raw_index=index, reference_time_s=time, actual_time_s=observed['time_s'],
                           event_side=side, reference=rv, actual=av))
    if samples:
        raise ValueError('Unpaired actual observations')
    metrics = {}
    for channel in paired[0]['reference']:
        metrics[channel] = {}
        for side in ('all', 'regular', 'pre', 'post'):
            observations = [p for p in paired if side == 'all' or p['event_side'] == side]
            errors = [p['actual'][channel] - p['reference'][channel] for p in observations]
            peak = max(range(len(errors)), key=lambda i: abs(errors[i]))
            metrics[channel][side] = dict(rmse(errors), peak_reference_time_s=observations[peak]['reference_time_s'],
                                         peak_event_side=observations[peak]['event_side'])
    sensor_consistency = {}
    # These probes use zero TR and no terminal-voltage compensation. Report
    # simultaneous-channel inconsistency rather than replacing a stale value.
    if cfg['components'][0]['constants'][0] == 0:
        for tool in ('reference', 'actual'):
            sensor_consistency[tool] = {}
            for side in ('all', 'regular', 'pre', 'post'):
                observations = [p for p in paired if side == 'all' or p['event_side'] == side]
                errors = [p[tool]['EXC_SENSE'] - p[tool]['V_BUS1'] for p in observations]
                peak = max(range(len(errors)), key=lambda i: abs(errors[i]))
                sensor_consistency[tool][side] = dict(maximum_absolute=abs(errors[peak]),
                    peak_reference_time_s=observations[peak]['reference_time_s'])
    return dict(raw_reference_rows=len(reference), actual_rows=len(actual), paired_rows=len(paired), rows_dropped=0,
                channels=len(metrics), metrics=metrics, paired=paired,
                zero_tr_uncompensated_sensor_consistency=sensor_consistency,
                initial_measurement=initial_measurement(fields, reference, cfg),
                initial_relative_angles_rad=dict(reference=r0, actual=a0),
                normalization='P/Q on 100 MVA network base; speed deviation from 1 pu; other signals pu; '
                              'relative angles radians; excursions relative to each initialized relative angle',
                unmapped_reference_status_channels=['TSOELOther:'+str(i) for i in range(1,5)],
                numerical_rmse_pass=all(v['all']['passes_rmse'] for v in metrics.values()),
                runtime_routing_verified=False, trajectory_acceptance=False)


if __name__ == '__main__':
    import sys
    from oel3c_routing_probe import configuration
    reference_path, actual_path, profile, step = sys.argv[1:]
    raw = Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    value = compare(*read_measurements(reference_path), read_actual(actual_path), configuration(profile, raw), float(step))
    print(json.dumps(value, indent=2))
