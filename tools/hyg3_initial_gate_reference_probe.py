"""Ordered infeasible initial gate limits, retaining source preparation and warnings."""
import sys
from pathlib import Path
source=Path('tools/hyg3_reference_probe.py').read_text()
needle="    'standard': {},"
assert needle in source
source=source.replace(needle,needle+"\n    'initial-upper-infeasible': {12:.4,13:0.},\n    'initial-lower-infeasible': {12:1.,13:.6},")
needle="    initial_reference = initialized[0]"
insert="""    Path('initial-boundary.json').write_text(json.dumps(dict(
        states=[checked('initial state',psspy.dsrval('STATE',layout['STATE']+i)) for i in range(9)],
        variables=[checked('initial variable',psspy.dsrval('VAR',layout['VAR']+i)) for i in range(5)],
        entered=entered,initialized=initialized),indent=2))
"""
assert needle in source;source=source.replace(needle,insert+needle)
needle='    import psse3607'
assert needle in source;source=source.replace(needle,"    shutil.copy2(root/'tools/hyg3_initial_gate_reference_probe.py',out/'hyg3_initial_gate_reference_probe.py')\n"+needle)
sys.path.insert(0,str(Path('tools').resolve()))
exec(compile(source,'tools/hyg3_reference_probe.py','exec'),{'__name__':'__main__'})
