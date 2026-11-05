"""Exact observed-voltage threshold controls for the second motor protection stage."""
import argparse,sys
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--boundary',choices=['down','equal','up'],required=True);p.add_argument('--repeat',choices=['a','b'],required=True);a=p.parse_args()
source=Path('tools/hsp_model_reference_probe.py').read_text()
source=source.replace('    import psse3607',"    shutil.copy2(root/'tools/composite_motor_threshold_reference_probe.py',out/'composite_motor_threshold_reference_probe.py')\n    import psse3607")
needle="    checked('start', psspy.strt_2([0, 0], 'native.out'))"
source=source.replace(needle,"    checked('observed load voltage channel',psspy.var_channel([-1,layout['VAR']+29],'V_OBS'))\n"+needle)
needle="    checked('prefault', psspy.run(0, .05, 1000000, 1, 0))"
replacement='''    import struct
    threshold_events=[]
    def change_threshold(label,offset):
        voltage=checked('observed voltage',psspy.dsrval('VAR',layout['VAR']+29))
        bits=struct.unpack('<I',struct.pack('<f',voltage))[0]
        value=struct.unpack('<f',struct.pack('<I',bits+offset))[0]
        checked('threshold change',psspy.change_con(layout['CON']+52,value))
        readback=checked('threshold readback',psspy.dsrval('CON',layout['CON']+52))
        assert readback==value
        threshold_events.append(dict(label=label,voltage=voltage,voltage_bits=bits,threshold=value,threshold_bits=bits+offset))
    change_threshold('seed',1)
    checked('seed timer',psspy.run(0,.002,1000000,1,0))
    change_threshold('boundary',OFFSET)
    checked('boundary hold',psspy.run(0,.020,1000000,1,0))
    change_threshold('repickup',1)
    checked('prefault', psspy.run(0, .05, 1000000, 1, 0))
    (out/'threshold-events.json').write_text(json.dumps(threshold_events,indent=2))'''.replace('OFFSET',str({'down':-1,'equal':0,'up':1}[a.boundary]))
assert needle in source;source=source.replace(needle,replacement)
constants={2:0,3:0,5:0,8:-1,18:.2,19:0,20:0,21:0,22:0,31:0,36:0,47:0,48:999,49:0,50:2,51:999,52:0,53:.005,54:.25,55:2,56:999}
sys.path.insert(0,str(Path('tools').resolve()))
sys.argv=['hsp_model_reference_probe.py','--fixture','cmldblu1-mixed','--profile','flat','--repeat',a.repeat,'--channel-part','1','--batch','cmld-motor-threshold-'+a.boundary+'-20261104','--generator-states','--clear-diagnostic-storage']
for k,v in constants.items():sys.argv.extend(['--constant',str(k)+'='+str(v)])
exec(compile(source,'tools/hsp_model_reference_probe.py','exec'),{'__name__':'__main__'})
