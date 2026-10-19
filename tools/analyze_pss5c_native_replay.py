"""All-row PSS5C residual diagnostics; never generate or infer acceptance bounds."""
import csv
import hashlib
import json
import math
from pathlib import Path
import sys

CHANNELS=(('low_signal','PSS_LOW_SIGNAL'),('high_signal','PSS_HIGH_SIGNAL'),
          ('VVL','PSS_VVL'),('VL','PSS_VL'),('VI','PSS_VI'),('VH','PSS_VH'),('Vstab','VOTHSG'))


def compare(folder,replay):
    native=folder/'native.csv'
    reference=list(csv.DictReader(native.read_text().splitlines()))
    actual=list(csv.DictReader(replay.read_text().splitlines()))
    assert len(reference)==len(actual)>0
    maxima={key:dict(max_abs_error=0.,time_s=float(reference[0]['time_s'])) for key,_ in CHANNELS}
    for expected,row in zip(reference,actual):
        time=float(expected['time_s'])
        assert abs(time-float(row['time_s']))<=1e-12 # Serialization precision only; no fitted alignment.
        for key,raw_key in CHANNELS:
            x,y=float(row[key]),float(expected[raw_key]);assert math.isfinite(x) and math.isfinite(y)
            error=abs(x-y)
            if error>maxima[key]['max_abs_error']:maxima[key]=dict(max_abs_error=error,time_s=time)
    sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
    return dict(status='diagnostic-not-acceptance',rows=len(reference),
                negative_history_rows=sum(float(r['time_s'])<0 for r in reference),
                duplicate_event_rows=sum(a['time_s']==b['time_s'] for a,b in zip(reference,reference[1:])),
                native_csv_sha256=sha(native),replay_sha256=sha(replay),channels=maxima,
                raw_var_semantics_certified=False,native_internal_state_coordinates_certified=False,
                fitted_parameters=False,fitted_offsets_or_clocks=False,trajectory_acceptance=False)


if __name__=='__main__':
    print(json.dumps(compare(Path(sys.argv[1]),Path(sys.argv[2])),indent=2))
