"""Separate physical Efd/Ifd/Vfe inputs and summation/takeover initialization."""
import json
from pathlib import Path
import sys
from genrou_native_probe import run_case
from oel3c_native_probe import configuration as base_configuration

PROFILES=('efd-flat','ifd-flat','vfe-flat','fallback-flat','takeover-flat',
          'efd-fault','ifd-fault','vfe-fault','takeover-fault')

def configuration(profile,raw):
    if profile not in PROFILES:raise ValueError(profile)
    cfg=base_configuration('flat' if profile.endswith('flat') else 'linear-fault',raw)
    lines=cfg['dynamic'].splitlines();i=next(i for i,s in enumerate(lines) if "'AC1C'" in s)
    fields=lines[i].split();constants=list(map(float,fields[5:-1]))
    constants[10:13]=[.2,.1,1.1] # Distinct physical signals; not a fitted trajectory.
    constants[13]=.75 # Canonical nonzero E1; SE1=SE2=0, so saturation remains disabled.
    location=2 if profile.startswith('takeover') else 1
    lines[i]="1 'AC1C' '1' "+str(location)+' 0 '+' '.join(map(str,constants))+' /'
    selected=2 if profile.startswith('ifd') else 3 if profile.startswith('vfe') else 17 if profile.startswith('fallback') else 1
    j=next(i for i,s in enumerate(lines) if "'OEL3C'" in s)
    limiter=cfg['components'][0];limiter['icons']=(selected,1);limiter['constants'][2]=0.
    if profile.endswith('fault'):limiter['constants'][0]=.5
    lines[j]="1 'OEL3C' '1' "+str(selected)+' 1 '+' '.join(map(str,limiter['constants']))+' /'
    cfg['dynamic']='\n'.join(lines)+'\n'
    cfg['components'].insert(0,dict(kind='EXC',model='AC1C',icons=(location,0),constants=constants,
        states=('EXC_VSENSE','EXC_LEAD_LAG','EXC_REGULATOR','EXC_FIELD','EXC_RATE_FEEDBACK'),
        variables=('EXC_HV_OUTPUT','EXC_LV_OUTPUT')))
    return cfg

def main():
    tag,step,profile=sys.argv[1:]
    root=Path.cwd();raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    cfg=configuration(profile,raw)
    try:
        run_case('oel3c-routing-'+tag,step,profile,cfg,probe_source=__file__,
            support_files=('oel3c_native_probe.py',),extra_machine_channels=((8,'XAD_IFD'),))
    except Exception as failure:
        import psspy
        records={}
        for kind in ('EXC','MAXXL'):
            entry={}
            for array in ('CON','ICON','STATE','VAR'):
                a,index=psspy.mdlind(1,'1',kind,array);b,count=psspy.mdlind(1,'1',kind,'N'+array)
                if a==b==0 and index is not None and count is not None:
                    fn=psspy.dsival if array=='ICON' else psspy.dsrval
                    entry[array]=[fn(array,index+i) for i in range(count)]
            records[kind]=entry
        out=root/'target'/('genrou-native-oel3c-routing-'+tag)
        if out.is_dir():(out/'rejected-initialization-readback.json').write_text(json.dumps(dict(
            status='rejected-routing-probe',exception_type=type(failure).__name__,records=records,
            trajectory_acceptance=False),indent=2)+'\n')
        raise

if __name__=='__main__':main()
