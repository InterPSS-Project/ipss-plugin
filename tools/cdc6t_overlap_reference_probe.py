"""Controlled overlapping-terminal blocking observations with immutable cold captures."""
import argparse,sys
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--repeat',choices=['a','b'],required=True)
parser.add_argument('--first',choices=['rectifier','inverter'],required=True)
args=parser.parse_args()
source=Path('tools/cdc6t_reference_probe.py').read_text()
anchor="    if args.limit_curve == 'active':"
assert source.count(anchor)==1
source=source.replace(anchor,"    constants[7]=.2\n    constants[27:29]=[999.,999.]\n"+anchor)
anchor="    run_until('fault interval', args.fault_clear)"
assert source.count(anchor)==1
source=source.replace(anchor,"""    run_until('second application', .07)
    snapshot('before-overlap')
    checked('overlapping opposite fault',psspy.dist_bus_fault(9 if args.fault_terminal=='rectifier' else 7,
            3,230.,[0.,.2*230**2/100]))
    snapshot('overlap-applied')
"""+anchor)
anchor="    snapshot('first-cleared')"
assert source.count(anchor)==1
source=source.replace(anchor,anchor+"""
    run_until('overlap remaining',.16)
    snapshot('before-overlap-clearing')
    checked('overlap clear',psspy.dist_clear_fault(1))
    snapshot('overlap-cleared')
""")
source=source.replace("    os.chdir(out)","    shutil.copy2(root/'tools/cdc6t_overlap_reference_probe.py',out/'cdc6t_overlap_reference_probe.py')\n    os.chdir(out)")
sys.path.insert(0,str(Path('tools').resolve()))
sys.argv=['cdc6t_reference_probe.py','--repeat',args.repeat,'--profile','fault','--control','inverter-power',
 '--fault-terminal',args.first,'--protection','coordinated','--batch','cdc6t-overlap-'+args.first+'-v2-20261104']
exec(compile(source,'tools/cdc6t_reference_probe.py','exec'),{'__name__':'__main__'})
