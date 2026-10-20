"""Exact two-selector/ten-constant limiter allocation and recorded-state SMIB probe."""
from pathlib import Path
import sys
from genrou_native_probe import configuration as machine_configuration,run_case

PROFILES=('flat','linear-fault','quadratic-fault','zero-filter-fault')
def configuration(profile,raw):
    if profile not in PROFILES:raise ValueError(profile)
    cfg=machine_configuration('flat' if profile=='flat' else 'fault',raw)
    constants=[4.47,.3333,.02,.1,.1,1.,0.,-1.,0.,-1.]
    if profile!='flat':constants[0]=.4
    exponent=2 if profile=='quadratic-fault' else 1
    if profile=='zero-filter-fault':constants[2]=0.
    exciter="1 'AC1C' '1' 1 0 0.0 0.2 0.05 10.0 0.3 20.0 -20.0 0.4 0.2 0.5 0.0 0.0 1.0 0.0 0.0 1.0 0.0 20.0 -20.0 99.0 0.0 /\n"
    cfg['dynamic']+=exciter+"1 'OEL3C' '1' 1 "+str(exponent)+' '+' '.join(map(str,constants))+" /\n"
    cfg['components']=[dict(kind='MAXXL',model='OEL3C',icons=(1,exponent),constants=constants,
        states=('OEL_INPUT','OEL_INTEGRAL'),variables=('OEL_REFERENCE','OEL_ERROR','OEL_GAINED_ERROR','OEL_PROPORTIONAL'))]
    cfg['record_live_states']=True;cfg['record_live_variables']=True;cfg['record_live_fault_states']=True
    return cfg

if __name__=='__main__':
    tag,step,profile=sys.argv[1:]
    raw=(Path.cwd()/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    run_case('oel3c-'+tag,step,profile,configuration(profile,raw),probe_source=__file__,extra_machine_channels=((8,'XAD_IFD'),))
