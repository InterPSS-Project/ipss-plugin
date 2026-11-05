"""Isolate zero-time continuation from physical CDC6T fault/event settings."""
import argparse,sys
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--repeat',choices=['a','b'],required=True);args=parser.parse_args()
source=Path('tools/cdc6t_reference_probe.py').read_text()
source=source.replace("    snapshot('first-cleared')", """    snapshot('first-cleared')
    before=checked('clock before continuation', psspy.dsrval('TIME',0))
    checked('zero-time clearing continuation',psspy.run(0,before,1000000,1,0))
    after=checked('clock after continuation',psspy.dsrval('TIME',0))
    assert before==after, (before,after)
    snapshot('post-clear-zero-time-continuation')""")
source=source.replace("    os.chdir(out)","    shutil.copy2(root/'tools/cdc6t_call_boundary_reference_probe.py',out/'cdc6t_call_boundary_reference_probe.py')\n    os.chdir(out)")
sys.path.insert(0,str(Path('tools').resolve()))
sys.argv=['cdc6t_reference_probe.py','--repeat',args.repeat,'--profile','fault','--control','inverter-power','--fault-terminal','inverter','--protection','dc-bypass','--bypass-recovery-delay','.0104','--batch','cdc6t-call-boundary-20261104']
exec(compile(source,'tools/cdc6t_reference_probe.py','exec'),{'__name__':'__main__'})
