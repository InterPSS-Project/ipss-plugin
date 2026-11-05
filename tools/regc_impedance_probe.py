"""Small-network source-impedance and explicit-reactance reference discriminators."""
import argparse
import csv
import json
import math
import os
from pathlib import Path
import shutil

from reeca_native_probe import configuration
from tgov1_limit_probe import checked, sha


def inputs(root, model, source_x, xe, fixed_commands=False, reactive_rate=None):
    if model not in ('REGCA1', 'REGCAU2', 'REGCB1'):
        raise ValueError('Unknown converter model')
    if not math.isfinite(source_x) or source_x <= 0 or not math.isfinite(xe) or xe < 0:
        raise ValueError('Positive source reactance and nonnegative effective reactance required')
    if model != 'REGCAU2' and xe != 0:
        raise ValueError('Only REGCAU2 has an explicit Xe parameter')
    source = root / 'ipss.test.plugin.core/testData/adpter/psse/v33/renewable'
    lines = (source/'regca_reeca_repca_bus1062.raw').read_text().splitlines()
    lines[1] = 'PUBLIC THREE-BUS CONVERTER IMPEDANCE DISCRIMINATOR'
    for index,line in enumerate(lines):
        if line.startswith("1,'1',"):
            fields = line.split(',');fields[10] = str(source_x);lines[index] = ','.join(fields)
    settings = configuration('flat-q', 'coarse', 'a')
    values = settings['converter']
    if reactive_rate is not None:
        if model=='REGCB1' or not math.isfinite(reactive_rate) or reactive_rate<=0:
            raise ValueError('A positive finite reactive recovery rate requires a REGC_A variant')
        values[12]=reactive_rate;values[13]=-reactive_rate
    if model == 'REGCAU2':
        converter = "1 'USRMDL' '1' 'REGCAU2' 101 1 1 15 3 4 " + ' '.join(map(str,values+[xe])) + ' /'
    elif model == 'REGCB1':
        converter = "1 'REGCB1' '1' 0 1 .02 .02 999 -999 999 .01 1.3 /"
    else:
        converter = "1 'REGCA1' '1' " + ' '.join(map(str,values)) + ' /'
    electrical = "1 'REECA1' '1' " + ' '.join(map(str,settings['icons']+settings['constants'])) + ' /'
    return '\n'.join(lines)+'\n', '\n'.join([converter]+([] if fixed_commands else [electrical])+["3 'GENCLS' '1' 99999 0 /"])+'\n'


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--model',choices=['REGCA1','REGCAU2','REGCB1'],required=True)
    parser.add_argument('--source-x',type=float,required=True)
    parser.add_argument('--xe',type=float,default=0)
    parser.add_argument('--step',type=float,default=.0005)
    parser.add_argument('--repeat',choices=['a','b'],required=True)
    parser.add_argument('--fixed-commands',action='store_true')
    parser.add_argument('--reactive-rate',type=float)
    args=parser.parse_args()
    if args.step not in (.0005,.00025):raise ValueError('Use one of the two recorded grids')
    root=Path.cwd()
    name=f'{args.model.lower()}-z{args.source_x:g}-xe{args.xe:g}-h{args.step:g}'+(f'-r{args.reactive_rate:g}' if args.reactive_rate is not None else '')+('-fixed' if args.fixed_commands else '')+f'-{args.repeat}'
    out=root/'target'/('regc-impedance-'+name);out.mkdir(exist_ok=False)
    for name in ['regc_impedance_probe.py','reeca_native_probe.py','tgov1_limit_probe.py']:
        shutil.copy2(root/'tools'/name,out/name)
    raw,dyr=inputs(root,args.model,args.source_x,args.xe,args.fixed_commands,args.reactive_rate)
    (out/'network.raw').write_text(raw);(out/'dynamic.dyr').write_text(dyr)
    import psse3607
    import psspy,dyntools
    os.chdir(out)
    checked('initialize',psspy.psseinit(50))
    for fn in [psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output]:
        checked('log',fn(2,'native.log',[0,0]))
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL'
    document=library.parent.parent/'DOCS/ModelLibrary.pdf'
    checked('library',psspy.addmodellibrary(str(library)))
    checked('network',psspy.read(0,'network.raw'))
    di,df=psspy.getdefaultint(),psspy.getdefaultreal();flags=[di]*7;flags[5]=1
    checked('renewable flag',psspy.machine_chng_4(1,'1',flags,[df]*17,''))
    checked('power flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
    checked('generators',psspy.cong(0))
    for stage in (1,2,3):checked('loads',psspy.conl(0,1,stage,[0,0],[0.]*4))
    checked('order',psspy.ordr(0));checked('factor',psspy.fact());checked('solution',psspy.tysl(0))
    checked('dynamic',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''))
    layout={kind:checked('count '+kind,psspy.windmind(1,'1','WGEN','N'+kind)) for kind in ['ICON','CON','STATE','VAR']}
    expected={'REGCA1':(1,14,3,4),'REGCAU2':(1,15,3,4),'REGCB1':(2,7,5,8)}[args.model]
    assert tuple(layout[k] for k in ['ICON','CON','STATE','VAR'])==expected
    layout['indices']={kind:checked('index '+kind,psspy.windmind(1,'1','WGEN',kind)) for kind in ['CON','STATE','VAR']}
    for kind,fn in [('STATE',psspy.state_channel),('VAR',psspy.var_channel)]:
        for i in range(layout[kind]):checked('state channel',fn([-1,layout['indices'][kind]+i],kind+str(i)))
    before=[checked('constant',psspy.dsrval('CON',layout['indices']['CON']+i)) for i in range(layout['CON'])]
    for code,name in [(2,'P_PU'),(3,'Q_PU')]:checked('machine channel',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in (1,2,3):checked('voltage channel',psspy.voltage_and_angle_channel([-1,-1,-1,bus],['V'+str(bus),'A'+str(bus)+'_DEG']))
    checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,args.step]+[df]*5))
    checked('start',psspy.strt_2([0,0],'native.out'))
    after=[checked('initialized constant',psspy.dsrval('CON',layout['indices']['CON']+i)) for i in range(layout['CON'])]
    assert before==after
    checked('prefault',psspy.run(0,.05,1000000,1,0))
    checked('fault',psspy.dist_bus_fault(2,3,230.,[0.,100.]))
    checked('fault interval',psspy.run(0,.10,1000000,1,0))
    checked('clear',psspy.dist_clear_fault(1));checked('postfault',psspy.run(0,.20,1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int))
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,time in enumerate(data['time']):writer.writerow([format(time,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    log=Path('native.log').read_text(errors='replace')
    quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','regc_impedance_probe.py','reeca_native_probe.py','tgov1_limit_probe.py','native.log','native.out','native.csv']
    manifest=dict(status='complete' if quality else 'rejected-quality',model=args.model,source_x=args.source_x,xe=args.xe,
        step_s=args.step,repeat=args.repeat,fixed_commands=args.fixed_commands,reactive_rate=args.reactive_rate,layout=layout,entered_constants=before,initialized_constants=after,
        fault=dict(bus=2,apply_s=.05,clear_s=.10,reactance_ohm=100.),end_s=.20,
        samples=len(data['time']),channels=len(keys),library_sha256=sha(library),source_document_sha256=sha(document),
        engine_module=psse3607.__name__,files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({k:v for k,v in manifest.items() if k not in ['files','entered_constants','initialized_constants']}),flush=True)
    assert quality,'Reference initialization or network convergence failed'


if __name__=='__main__':main()
