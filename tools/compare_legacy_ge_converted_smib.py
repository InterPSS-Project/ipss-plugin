"""Strict complete converted-WT3 boundary comparison; retain duplicate event samples."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path


def reference_rows(path):
    text=path.read_text().splitlines()
    start=next(i for i,line in enumerate(text) if line.startswith('Results,'))
    rows=[]
    for line in text[start+1:]:
        if line.startswith('END') or not line.strip():
            continue
        rows.append([float(v) for v in next(csv.reader([line])) if v.strip()])
    return rows


def compare(reference,actual):
    expected=reference_rows(reference)
    with actual.open() as stream:
        reader=csv.reader(stream);next(reader)
        measured=[[float(v) for v in row] for row in reader]
    if len(expected)!=len(measured):
        raise ValueError('Sample-count mismatch; event samples must not be dropped')
    if any(len(r)!=5 or not all(math.isfinite(v) for v in r) for r in expected+measured):
        raise ValueError('Invalid channel count or nonfinite sample')
    if any(abs(a[0]-e[0])>1e-9 for a,e in zip(measured,expected)):
        raise ValueError('Time/event-side order mismatch')
    names=('V1_pu','V2_pu','P_MW','Q_MVAR');bounds=(.001,.001,.1,.1)
    metrics={}
    for c,(name,bound) in enumerate(zip(names,bounds),1):
        errors=[abs(a[c]-e[c]) for a,e in zip(measured,expected)]
        worst=max(range(len(errors)),key=errors.__getitem__)
        metrics[name]=dict(initial_error=errors[0],maximum_error=errors[worst],
            maximum_time=expected[worst][0],maximum_row=worst,
            rmse=math.sqrt(sum(e*e for e in errors)/len(errors)),
            maximum_gate=bound,accepted=max(errors)<=bound)
    return dict(status='accepted' if all(m['accepted'] for m in metrics.values()) else 'not-accepted',
        meaning='converted-model-only; no legacy-equivalence claim',samples=len(expected),
        duplicate_event_rows=sum(expected[i][0]==expected[i-1][0] for i in range(1,len(expected))),
        reference_sha256=hashlib.sha256(reference.read_bytes()).hexdigest(),
        actual_sha256=hashlib.sha256(actual.read_bytes()).hexdigest(),channels=metrics)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--reference',type=Path,required=True);p.add_argument('--actual',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    result=compare(a.reference,a.actual)
    if a.output.exists():raise ValueError('Output already exists')
    a.output.write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
    if result['status']!='accepted':raise SystemExit(1)


if __name__=='__main__':main()
