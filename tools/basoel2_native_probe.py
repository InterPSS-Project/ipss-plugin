"""Exact three-selector/twelve-constant combined limiter and timer discriminators."""
from pathlib import Path
import sys
from genrou_native_probe import configuration as machine_configuration,run_case

PROFILES=('flat','efd-fault','ifd-fault','vfe-fault','vhz-fault','disabled-fault','ifd-zero-filter')


def configuration(profile,raw):
    if profile not in PROFILES:raise ValueError('Unknown BASOEL2 profile')
    cfg=machine_configuration('flat' if profile=='flat' else 'fault',raw)
    exciter=[0.,.2,.05,10.,.3,20.,-20.,.4,.2,.5,.2,.1,1.1,.75,0.,1.,0.,20.,-20.,99.,0.]
    selected=0 if profile=='efd-fault' else 2 if profile=='vfe-fault' else 1
    oel=0 if profile in ('vhz-fault','disabled-fault') else 1
    vhz=1 if profile in ('flat','vhz-fault') else 0
    constants=[.02,.15,.25,1.8,.01,1.5,.02,1.2,.03,.02,1.05,.1]
    if profile=='flat':constants[3:9]=[4.,0.,3.5,5.,2.5,5.]
    if profile=='efd-fault':constants[3:9]=[1.3,0.,1.2,.01,1.15,.01]
    if profile=='vfe-fault':constants[3:9]=[1.7,.01,1.6,.02,1.5,.03]
    if profile=='ifd-zero-filter':constants[0]=0.
    if profile=='vhz-fault':
        constants[10]=1.0001;cfg['vref_increment']=.2
    cfg['dynamic']+="1 'AC1C' '1' 1 0 "+' '.join(map(str,exciter))+" /\n"
    cfg['dynamic']+="1 'BASOEL2' '1' "+' '.join(map(str,(oel,vhz,selected)))+' '+' '.join(map(str,constants))+" /\n"
    cfg['components']=[dict(kind='EXC',model='AC1C',icons=(1,0),constants=exciter,
        states=('EXC_VSENSE','EXC_LEAD_LAG','EXC_REGULATOR','EXC_FIELD','EXC_RATE_FEEDBACK'),
        variables=('EXC_HV_OUTPUT','EXC_LV_OUTPUT')),
        dict(kind='MAXXL',model='BASOEL2',icons=(oel,vhz,selected),constants=constants,reserved_icon_count=12,
        states=('BAS_INPUT','BAS_INTEGRAL','BAS_SPEED'),
        variables=('BAS_REFERENCE','BAS_OEL_OUTPUT','BAS_VHZ_OUTPUT','BAS_T1_A','BAS_T1_B',
                   'BAS_T2_A','BAS_T2_B','BAS_T3_A','BAS_T3_B'))]
    cfg['record_live_states']=True;cfg['record_live_variables']=True;cfg['record_live_fault_states']=True
    return cfg


if __name__=='__main__':
    tag,step,profile=sys.argv[1:]
    raw=(Path.cwd()/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    run_case('basoel2-'+tag,step,profile,configuration(profile,raw),probe_source=__file__,
             extra_machine_channels=((8,'XAD_IFD'),(11,'VREF'),(13,'VOEL_MACHINE')))
