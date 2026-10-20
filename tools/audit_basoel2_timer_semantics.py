"""Declared phase-machine hypothesis for controlled, zero-output timer probes.

No parameter fitting or clock adjustment is performed. Post-initialization
state steps are explicit interventions, not coupled benchmark trajectories.
"""
from dataclasses import dataclass
import csv
import json
from pathlib import Path


@dataclass
class Timer:
    phase:str='idle'
    start:float=0.
    deadline:float=99999.
    above:bool=False

    def advance(self,previous_time,time,sensed,threshold,delay,comparison_margin=0.):
        high=sensed>threshold*(.999 if self.above else 1.)
        if self.phase=='idle':
            if high:self.phase='arming';self.start=previous_time;self.deadline=previous_time+delay
        elif self.phase=='arming' and not high:
            self.phase='idle';self.deadline=99999.
        elif self.phase=='active' and not high:
            self.phase='cooling';self.deadline=2*previous_time-self.start;self.start=previous_time
        elif self.phase=='cooling' and high:
            self.deadline+=time-previous_time
        if self.phase=='arming' and time+comparison_margin>=self.deadline:self.phase='active'
        if self.phase=='cooling' and not high and time+comparison_margin>=self.deadline:
            self.phase='idle';self.deadline=99999.
        self.above=high

    @property
    def active(self):return self.phase in ('active','cooling')


def audit(folder,nearest_step=False):
    folder=Path(folder);manifest=json.loads((folder/'manifest.json').read_text())
    if not manifest['profile'].startswith('timer-'):raise ValueError('Requires an explicit timer-state diagnostic')
    data=manifest['components']['MAXXL'];p=data['actual_constants']
    if data['actual_icons'][0]!=1 or p[1:3]!=[0.,0.]:raise ValueError('Requires enabled timers and zero controller gains')
    thresholds=p[3:9:2];delays=[p[4],p[4]+p[6],p[4]+p[6]+p[8]]
    rows=list(csv.DictReader((folder/'native.csv').open()))
    timers=[Timer() for _ in range(3)];previous=0.;errors=[];changes=[];last=None
    for row in rows:
        time=float(row['time_s'])
        if time>=0 and time>previous:
            for timer,threshold,delay in zip(timers,thresholds,delays):
                timer.advance(previous,time,float(row['BAS_INPUT']),threshold,delay,
                              manifest['step_s']/2 if nearest_step else 0.)
        reference=thresholds[0]
        for i in (1,2):
            if timers[i].active:reference=min(reference,thresholds[i])
        expected=float(row['BAS_REFERENCE']);error=abs(reference-expected);errors.append(error)
        if last!=(reference,expected):changes.append(dict(time_s=time,candidate=reference,reference=expected,phases=[t.phase for t in timers]))
        if time>=0:previous=time
        last=(reference,expected)
    return dict(run=folder.name,rows=len(rows),rmse=(sum(e*e for e in errors)/len(errors))**.5,
                maximum=max(errors),different_rows=sum(e!=0 for e in errors),changes=changes,
                nearest_step_comparison=nearest_step,comparison_channels=['BAS_REFERENCE'],
                status='phase-machine-hypothesis-not-runtime-acceptance')


if __name__=='__main__':
    import sys
    print(json.dumps([audit(Path(path),nearest) for path in sys.argv[1:] for nearest in (False,True)],indent=2))
