"""Exact nonnegative field-compensation profiles; limiting is on Kw times Id."""
import json
from pathlib import Path
import shutil
import sys
import genqec_schema_probe as base
from genqec_schema_probe import configuration as base_configuration
from hplntd_fault_probe import sha

VALUES={'kw-zero':0.,'kw-one':1.,'kw-high':1.5}


def configuration(profile):
    if profile not in VALUES:raise ValueError('Unknown field-compensation profile')
    _,selector,values=base_configuration('standard-1');values[15]=VALUES[profile]
    return "1 'GENQEC' '1' "+str(selector)+' '+' '.join(map(str,values))+" /\n2 'GENCLS' '1' 99999 0 /\n",selector,values


def main():
    import psse3607
    import psspy
    root=Path.cwd();tag,step,profile=sys.argv[1:];assert profile in VALUES and tag.startswith('kw-')
    psspy.voltage_channel=lambda status,label:psspy.voltage_and_angle_channel(status,[label,'A_BUS'+str(status[3])])
    base.configuration=configuration;base.__file__=__file__;base.main()
    out=root/'target'/('genqec-schema-'+tag);shutil.copy2(root/'tools/genqec_schema_probe.py',out/'genqec_schema_probe.py')
    m=json.loads((out/'manifest.json').read_text());m['branch']='Kw>=0; clamp(Kw*Id,-0.4,0.4)';m['files']['genqec_schema_probe.py']=sha(out/'genqec_schema_probe.py');(out/'manifest.json').write_text(json.dumps(m,indent=2)+'\n')


if __name__=='__main__':main()
