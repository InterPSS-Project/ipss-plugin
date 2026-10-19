"""Documented zero-q-transient generator profiles with the shared exact probe."""
import json
from pathlib import Path
import shutil
import sys
import genqec_schema_probe as base
from genqec_schema_probe import configuration as base_configuration
from hplntd_fault_probe import sha

PROFILES=('genqec-0','genqec-1','genqej-0','genqej-1')


def configuration(profile):
    if profile not in PROFILES:raise ValueError('Unknown salient-pole profile')
    selector=int(profile[-1]);_,_,c=base_configuration('standard-'+str(selector))
    c[2]=0.;c[9]=c[7]
    header="1 'USRMDL' '1' 'GENQEJU' 1 1 1 16 6 1 " if profile.startswith('genqej') else "1 'GENQEC' '1' "
    text=header+str(selector)+' '+' '.join(map(str,c))+" /\n2 'GENCLS' '1' 99999 0 /\n"
    return text,selector,c


def main():
    import psse3607
    import psspy
    root=Path.cwd();tag,step,profile=sys.argv[1:];assert profile in PROFILES and tag.startswith('salient-')
    # Preserve the exact terminal phasor for independent electrical-state replay.
    psspy.voltage_channel=lambda status,label:psspy.voltage_and_angle_channel(status,[label,'A_BUS'+str(status[3])])
    base.configuration=configuration;base.__file__=__file__;base.main()
    out=root/'target'/('genqec-schema-'+tag)
    shutil.copy2(root/'tools/genqec_schema_probe.py',out/'genqec_schema_probe.py')
    m=json.loads((out/'manifest.json').read_text());m['model']='GENQEJ' if profile.startswith('genqej') else 'GENQEC';m['branch']='Tqop=0,Xqp=Xq'
    m['files']['genqec_schema_probe.py']=sha(out/'genqec_schema_probe.py');(out/'manifest.json').write_text(json.dumps(m,indent=2)+'\n')


if __name__=='__main__':main()
