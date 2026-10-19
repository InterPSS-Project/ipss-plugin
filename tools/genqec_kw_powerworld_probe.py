"""Cold exact-input field-compensation diagnostics without model overrides."""
import json
from pathlib import Path
import shutil
import sys
import genqec_schema_powerworld_probe as base
from genqec_kw_probe import configuration,VALUES
from hplntd_fault_probe import sha


def main():
    root=Path.cwd();tag,step,profile=sys.argv[1:];assert profile in VALUES and tag.startswith('kw-')
    base.configuration=lambda unused:configuration(profile)
    original=sys.argv[:];sys.argv=[sys.argv[0],tag,step,'standard-1']
    try:base.main()
    finally:
        sys.argv=original;out=root/'target'/('genqec-schema-powerworld-'+tag)
        if out.is_dir():
            shutil.copy2(root/'tools/genqec_schema_powerworld_probe.py',out/'genqec_schema_powerworld_probe.py')
            shutil.copy2(__file__,out/'probe.py');shutil.copy2(root/'tools/genqec_kw_probe.py',out/'genqec_kw_probe.py')
            if (out/'manifest.json').is_file():
                m=json.loads((out/'manifest.json').read_text());m['kw_profile']=profile;m['branch']='Kw>=0; clamp(Kw*Id,-0.4,0.4)'
                m['generating_files']={name:sha(out/name) for name in ['probe.py','genqec_schema_powerworld_probe.py','genqec_kw_probe.py','genqec_schema_probe.py','hplntd_fault_probe.py','runner.py']};(out/'manifest.json').write_text(json.dumps(m,indent=2)+'\n')


if __name__=='__main__':main()
