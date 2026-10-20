"""Audit entered-reference meaning from measured states/output; never replace it."""
import math


def audit(rows, pickup, exponent=1, gain=.1, proportional_gain=1., lower=-1., upper=0.):
    if not rows or exponent <= 0 or gain <= 0 or proportional_gain <= 0 or lower >= upper:
        raise ValueError('Invalid observable reference-equation contract')
    if not all(math.isfinite(value) for value in (pickup, gain, proportional_gain, lower, upper)):
        raise ValueError('Nonfinite reference-equation contract')
    if any(not all(math.isfinite(row[key]) for key in ('time_s','input','integral','output')) for row in rows):
        raise ValueError('Nonfinite reference observation')
    declared = pickup ** exponent
    initial_candidate = rows[0]['input'] ** exponent
    maxima = {'entered_reference': dict(absolute=0.,time_s=rows[0]['time_s']),
              'initial_measurement_candidate': dict(absolute=0.,time_s=rows[0]['time_s'])}
    inferred=[]
    for row in rows:
        x,i,o = row['input'],row['integral'],row['output']
        for key,reference in (('entered_reference',declared),('initial_measurement_candidate',initial_candidate)):
            p = proportional_gain * gain * (reference - x ** exponent) if i < 0 else 0.
            expected = max(lower,min(upper,i+p))
            error = abs(expected-o)
            if error > maxima[key]['absolute']:
                maxima[key] = dict(absolute=error,time_s=row['time_s'])
        if i < 0 and lower < o < upper:
            inferred.append(x ** exponent + (o-i)/(proportional_gain*gain))
    # The same pre-existing serialization guard as the initial-measurement audit;
    # this is not the looser trajectory-RMSE acceptance limit.
    bound=1e-6
    return dict(raw_rows=len(rows),rows_dropped=0,declared_powered_reference=declared,
        initial_measurement_powered_candidate=initial_candidate,algebraic_residuals=maxima,
        observable_active_rows=len(inferred),inferred_powered_reference_range=[min(inferred),max(inferred)] if inferred else None,
        serialization_bound_pu=bound,
        entered_reference_equation_consistent=maxima['entered_reference']['absolute']<bound,
        initialized_measurement_candidate_consistent=maxima['initial_measurement_candidate']['absolute']<bound,
        reference_identity_verified=bool(inferred) and maxima['entered_reference']['absolute']<bound,
        production_reference_changed=False,parameters_fitted=False,trajectory_acceptance=False)


def from_powerworld(fields, rows, pickup):
    def index(name):
        matches=[i+1 for i,f in enumerate(fields) if f[0].strip()=='Generator'
                 and f[1].strip()=='1 1' and f[4].strip()==name]
        if len(matches)!=1:raise ValueError('Missing or duplicate reference channel '+name)
        return matches[0]
    x,i,o = (index(name) for name in ('TSOELState:1','TSOELState:2','TSVOEL'))
    return audit([dict(time_s=row[0],input=row[x],integral=row[i],output=row[o]) for row in rows],pickup)
