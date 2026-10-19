"""Run exact salient standard input with the proven commercial export workflow."""
import json
from pathlib import Path
import shutil
import sys
import genqec_schema_powerworld_probe as base
from genqec_salient_probe import configuration
from hplntd_fault_probe import sha


def main():
    root=Path.cwd();tag,step,profile=sys.argv[1:]
    assert profile in ('genqec-0','genqec-1') and tag.startswith('salient-')
    base.configuration=lambda unused:configuration(profile)
    original=sys.argv[:];sys.argv=[sys.argv[0],tag,step,'standard-'+profile[-1]]
    try:base.main()
    finally:
        sys.argv=original;out=root/'target'/('genqec-schema-powerworld-'+tag)
        if out.is_dir():
            shutil.copy2(root/'tools/genqec_schema_powerworld_probe.py',out/'genqec_schema_powerworld_probe.py')
            shutil.copy2(__file__,out/'probe.py');shutil.copy2(root/'tools/genqec_salient_probe.py',out/'genqec_salient_probe.py')
            if (out/'manifest.json').is_file():
                m=json.loads((out/'manifest.json').read_text());m['salient_profile']=profile;m['branch']='Tqop=0,Xqp=Xq'
                m['generating_files']={name:sha(out/name) for name in ['probe.py','genqec_schema_powerworld_probe.py','genqec_salient_probe.py','genqec_schema_probe.py','hplntd_fault_probe.py','runner.py']}
                (out/'manifest.json').write_text(json.dumps(m,indent=2)+'\n')


if __name__=='__main__':main()
