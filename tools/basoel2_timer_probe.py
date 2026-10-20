"""Controlled sensed-input steps with outputs disabled or both OEL gains zero.

This isolates timer memory; it is not a physical coupled-trajectory benchmark.
The large input-filter time leaves each commanded state away from thresholds.
Every commanded state, raw sample and simulator integration memory is retained.
"""
from pathlib import Path
import sys
from basoel2_native_probe import configuration as base_configuration
from genrou_native_probe import run_case

SCHEDULES={
    'timer-long':((.05,4.),(.25,.5)),
    'timer-short':((.05,4.),(.075,.5),(.125,4.),(.225,.5)),
    'timer-selective':((.05,1.4),(.15,.5),(.2,2.),(.3,.5)),
    'timer-partial':((.05,4.),(.15,.5),(.175,4.),(.225,.5)),
    'timer-hysteresis':((.05,4.),(.15,2.998),(.2,2.996),(.3,.5)),
    'timer-zero':((.05,4.),(.15,.5)),
    'timer-fractional':((.05,4.),(.25,.5)),
}


def configuration(profile,raw):
    active=profile.endswith('-active')
    base=profile.removesuffix('-active')
    if base not in SCHEDULES:raise ValueError('Unknown controlled timer profile')
    cfg=base_configuration('flat',raw);cfg['apply_fault']=False
    limiter=cfg['components'][1];limiter['icons']=(int(active),0,1)
    limiter['constants'][:]=[1e6,.15,.25,3.,.02,2.5,.02,2.,.02,.02,1.05,.1]
    if base=='timer-selective':limiter['constants'][3:9]=[3.,.02,1.8,.02,1.2,.02]
    if active:limiter['constants'][1:3]=[0.,0.]
    if base=='timer-zero':limiter['constants'][4]=limiter['constants'][6]=limiter['constants'][8]=0.
    if base=='timer-fractional':limiter['constants'][4]=limiter['constants'][6]=limiter['constants'][8]=.0202
    prefix=cfg['dynamic'].split("1 'BASOEL2'")[0]
    cfg['dynamic']=prefix+"1 'BASOEL2' '1' "+' '.join(map(str,limiter['icons']))+' '+' '.join(map(str,limiter['constants']))+' /\n'
    cfg['basoel2_timer_state_schedule']=SCHEDULES[base]
    cfg['record_live_states']=cfg['record_live_variables']=True
    return cfg


if __name__=='__main__':
    tag,step,profile=sys.argv[1:]
    raw=(Path.cwd()/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    run_case('basoel2-timer-'+tag,step,profile,configuration(profile,raw),probe_source=__file__,
             support_files=('basoel2_native_probe.py',),extra_machine_channels=((8,'XAD_IFD'),(11,'VREF'),(13,'VOEL_MACHINE')))
